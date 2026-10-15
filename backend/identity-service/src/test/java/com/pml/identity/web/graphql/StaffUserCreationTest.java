package com.pml.identity.web.graphql;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountService;
import com.pml.identity.account.ContactService;
import com.pml.identity.account.StaffAccountPassword;
import com.pml.identity.account.StaffAccountSeal;
import com.pml.identity.account.StaffPasswordReset;
import com.pml.identity.account.TemporaryPasswordGenerator;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.security.revocation.RevocationTrigger;
import com.pml.identity.service.AdminAuditService;
import com.pml.identity.account.StaffAccountSteps;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.TemporalTestEnvironments;
import com.pml.identity.workflow.staff.StaffAccountActivitiesImpl;
import com.pml.identity.workflow.staff.StaffAccountProcess;
import com.pml.identity.workflow.staff.StaffAccountWorkflowImpl;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.impl.UserServiceImpl;
import com.pml.identity.web.graphql.mutation.UserMutationResolver;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import com.pml.shared.security.RequiresPermissionAspect;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Who may open a staff account and hand out staff roles, through the real resolver, the real
 * method security, the real creation workflow on the in-process engine, a real MongoDB and an
 * in-memory Keycloak that can refuse a role grant.
 */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("staff accounts: only a super administrator creates them, only with a staff role, and a failed creation leaves nothing")
class StaffUserCreationTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);

    private static AccountFixtures db;
    private static UserRepository users;
    private static FakeKeycloak keycloak;
    private static AnnotationConfigApplicationContext context;
    private static UserMutationResolver resolver;
    private static AccountService accounts;
    private static UserServiceImpl userService;
    private static TestWorkflowEnvironment env;
    /** Accounts whose sessions the reset cut, with the trigger it gave: the revocation store's stand-in. */
    private static final List<String> CUT = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Configuration
    @EnableReactiveMethodSecurity
    @EnableAspectJAutoProxy
    static class Beans {
        @Bean
        RequiresPermissionAspect permissionAspect() {
            return new RequiresPermissionAspect();
        }

        @Bean
        UserMutationResolver resolver() {
            return new UserMutationResolver(userService, accounts);
        }
    }

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_staff_creation", CLOCK);
        users = new ReactiveMongoRepositoryFactory(db.template).getRepository(UserRepository.class);
        keycloak = new FakeKeycloak();
        KeycloakProperties properties = new KeycloakProperties();
        accounts = new AccountService(db.template, db.transaction, db.outbox, keycloak,
                new DefaultListableBeanFactory().getBeanProvider(
                        com.pml.identity.security.revocation.MongoRevocationStore.class), properties, CLOCK);
        ContactService contacts = new ContactService(db.template, AccountFixtures.HASHER);
        StaffAccountSeal seal = new StaffAccountSeal(new ObjectMapper(), AccountFixtures.CRYPTO);
        DeliveryOrchestrator delivery = new DeliveryOrchestrator(
                List.of(new CapturingProvider(new CapturedMessages(), CLOCK)), new SimpleMeterRegistry());
        StaffAccountSteps steps = new StaffAccountSteps(db.template, db.transaction, keycloak, properties, accounts,
                contacts, seal, AccountFixtures.HASHER, delivery, CLOCK);
        // The creation runs as the real workflow on the in-process engine: what the resolver returns is what
        // the process produced, not a stand-in for it.
        env = TemporalTestEnvironments.newInstance();
        Worker worker = env.newWorker(TaskQueues.ONBOARDING);
        worker.registerWorkflowImplementationTypes(StaffAccountWorkflowImpl.class);
        worker.registerActivitiesImplementations(new StaffAccountActivitiesImpl(steps));
        env.start();
        StaffAccountProcess process = new StaffAccountProcess(new TemporalGateway(env.getWorkflowClient()), db.template,
                AccountFixtures.HASHER, seal, steps, new TemporaryPasswordGenerator(), Duration.ofSeconds(30));
        StaffPasswordReset reset = new StaffPasswordReset(accounts, keycloak, new TemporaryPasswordGenerator(),
                (accountId, trigger, actorId) -> {
                    CUT.add(accountId + ":" + trigger + ":" + actorId);
                    return Mono.empty();
                }, new AdminAuditService(db.template, CLOCK));
        userService = new UserServiceImpl(users, contacts, accounts, process, reset, CLOCK);
        context = new AnnotationConfigApplicationContext(Beans.class);
        resolver = context.getBean(UserMutationResolver.class);
    }

    @AfterAll
    static void disconnect() {
        context.close();
        env.close();
        db.close();
    }

    @BeforeEach
    void clean() {
        db.reset();
        keycloak.staff.clear();
        keycloak.staffRoles.clear();
        keycloak.temporaryPasswords.clear();
        keycloak.calls.clear();
        keycloak.sessionsEnded.clear();
        keycloak.failGrants = false;
        CUT.clear();
    }

    private static JwtAuthenticationToken caller(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt,
                Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    private static <T> Mono<T> as(JwtAuthenticationToken caller, Mono<T> action) {
        return action.contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller));
    }

    private static Map<String, Object> input(String email, Object role) {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("email", email);
        input.put("firstName", "Ops");
        input.put("lastName", "Person");
        if (role != null) {
            input.put("role", role.toString());
        }
        return input;
    }

    private static ErrorCode refusal(Runnable call) {
        try {
            call.run();
        } catch (DomainRefusal refusal) {
            return refusal.errorCode();
        }
        throw new AssertionError("expected a refusal");
    }

    private void assertNothingCreated() {
        assertThat(db.count("identity_users")).isZero();
        assertThat(keycloak.staff).isEmpty();
        assertThat(keycloak.calls).doesNotContain("createStaffUser");
    }

    @Test
    @DisplayName("an administrator cannot create a super administrator")
    void adminCannotMintSuperAdmin() {
        ErrorCode code = refusal(() -> as(caller("admin-1", "ADMIN"),
                resolver.createUser(input("root@example.org", UserType.SUPER_ADMIN))).block());

        assertThat(code).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertNothingCreated();
    }

    @Test
    @DisplayName("an administrator cannot create any staff role, because handing out staff roles is for a super administrator")
    void adminCannotCreateFinance() {
        ErrorCode code = refusal(() -> as(caller("admin-1", "ADMIN"),
                resolver.createUser(input("money@example.org", UserType.FINANCE))).block());

        assertThat(code).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertNothingCreated();
    }

    @Test
    @DisplayName("a customer or an organizer cannot be created here, by anyone, and nothing is left behind")
    void buyerRolesAreRefused() {
        for (UserType role : List.of(UserType.CUSTOMER, UserType.ORGANIZER)) {
            ErrorCode code = refusal(() -> as(caller("root-1", "SUPER_ADMIN"),
                    resolver.createUser(input(role.name().toLowerCase() + "@example.org", role))).block());
            assertThat(code).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
            ErrorCode adminCode = refusal(() -> as(caller("admin-1", "ADMIN"),
                    resolver.createUser(input(role.name().toLowerCase() + "2@example.org", role))).block());
            assertThat(adminCode).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        }
        assertThat(refusal(() -> as(caller("root-1", "SUPER_ADMIN"),
                resolver.createUser(input("plain@example.org", null))).block()))
                .as("there is no plain account any more").isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertNothingCreated();
    }

    @Test
    @DisplayName("a customer calling createUser is forbidden")
    void customerIsForbidden() {
        assertThatThrownBy(() -> as(caller("buyer-1", "CUSTOMER"),
                resolver.createUser(input("ops@example.org", UserType.FINANCE))).block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
        assertNothingCreated();
    }

    @Test
    @DisplayName("a super administrator creates a finance user with a temporary password: staff realm only, ACTIVE, no buyer role")
    void superAdminCreatesFinance() {
        StaffAccountPassword answer = as(caller("root-1", "SUPER_ADMIN"),
                resolver.createUser(input("finance.one@example.org", UserType.FINANCE))).block();
        User created = answer.user();

        assertThat(answer.temporaryPassword()).as("the one-time password is in the answer of the creating call").isNotBlank();
        assertThat(keycloak.temporaryPasswords.get(created.getId())).isEqualTo(answer.temporaryPassword());
        User stored = db.template.findById(created.getId(), User.class).block();
        assertThat(stored.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(stored.getCreatedVia()).isEqualTo(AccountService.STAFF_ADMIN);
        assertThat(stored.getRoles()).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.FINANCE);
        assertThat(keycloak.staff).containsKey(stored.getKeycloakUserId());
        assertThat(keycloak.staffRoles.get(stored.getKeycloakUserId()))
                .as("the staff realm user holds the staff role and no buyer or organizer role")
                .containsExactly("FINANCE");
        assertThat(keycloak.byUsername).as("nothing was created in the buyer realm").isEmpty();
        assertThat(keycloak.calls).doesNotContain("createUser");
    }

    @Test
    @DisplayName("a role grant that fails leaves no Keycloak user and no stored account")
    void failedGrantLeavesNothing() {
        keycloak.failGrants = true;

        assertThatThrownBy(() -> as(caller("root-1", "SUPER_ADMIN"),
                resolver.createUser(input("finance.two@example.org", UserType.FINANCE))).block())
                .isInstanceOf(Exception.class);

        assertThat(db.count("identity_users")).isZero();
        assertThat(keycloak.staff).isEmpty();
        assertThat(keycloak.calls).contains("createStaffUser", "deleteStaffUser");

        keycloak.failGrants = false;
        assertThat(as(caller("root-1", "SUPER_ADMIN"),
                resolver.createUser(input("finance.two@example.org", UserType.FINANCE))).block().user().getId())
                .as("the same email can be used again").isNotBlank();
    }

    @Test
    @DisplayName("an administrator cannot add a staff role to anyone, set roles that include one, or take one away")
    void adminCannotChangeStaffRoles() {
        User target = as(caller("root-1", "SUPER_ADMIN"),
                resolver.createUser(input("target@example.org", UserType.SUPER_ADMIN))).block().user();

        assertThat(refusal(() -> as(caller("admin-1", "ADMIN"),
                resolver.addUserRole(target.getId(), UserType.FINANCE)).block())).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(refusal(() -> as(caller("admin-1", "ADMIN"),
                resolver.setUserRoles(target.getId(), List.of(UserType.CUSTOMER, UserType.ADMIN))).block()))
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(refusal(() -> as(caller("admin-1", "ADMIN"),
                resolver.setUserRoles(target.getId(), List.of(UserType.CUSTOMER))).block()))
                .as("demoting a super administrator is a staff-role change too").isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(refusal(() -> as(caller("admin-1", "ADMIN"),
                resolver.removeUserRole(target.getId(), UserType.SUPER_ADMIN)).block())).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);

        assertThat(db.template.findById(target.getId(), User.class).block().getRoles())
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.SUPER_ADMIN);
    }

    @Test
    @DisplayName("a super administrator can add a staff role")
    void superAdminCanAddRole() {
        User target = as(caller("root-1", "SUPER_ADMIN"),
                resolver.createUser(input("target2@example.org", UserType.FINANCE))).block().user();

        User changed = as(caller("root-1", "SUPER_ADMIN"),
                resolver.addUserRole(target.getId(), UserType.FINANCE_LEAD)).block();

        assertThat(changed.getRoles()).contains(UserType.FINANCE, UserType.FINANCE_LEAD);
        assertThat(Set.copyOf(keycloak.staffRoles.get(target.getKeycloakUserId()))).containsExactlyInAnyOrder("FINANCE", "FINANCE_LEAD");
    }

    private User openedStaff(String email, UserType role) {
        return as(caller("root-1", "SUPER_ADMIN"), resolver.createUser(input(email, role))).block().user();
    }

    @Test
    @DisplayName("a super administrator resets a staff member's password: a new value is applied, their sessions are cut and the reset is recorded")
    void superAdminResetsAStaffPassword() {
        User staff = openedStaff("reset.me@example.org", UserType.FINANCE);
        String original = keycloak.temporaryPasswords.get(staff.getKeycloakUserId());
        keycloak.openSession(staff.getKeycloakUserId(), "session-1");

        StaffAccountPassword reset = as(caller("root-1", "SUPER_ADMIN"),
                resolver.resetStaffTemporaryPassword(staff.getId())).block();

        assertThat(reset.temporaryPassword()).isNotBlank().isNotEqualTo(original);
        assertThat(keycloak.temporaryPasswords.get(staff.getKeycloakUserId())).isEqualTo(reset.temporaryPassword());
        assertThat(CUT).as("the sessions of that account were revoked, naming the administrator")
                .containsExactly(staff.getId() + ":" + RevocationTrigger.CREDENTIAL_CHANGE + ":root-1");
        assertThat(keycloak.sessionsEnded).contains(staff.getKeycloakUserId());
        var rows = db.all("identity_audit_logs").stream()
                .filter(row -> AuditLog.AuditAction.STAFF_PASSWORD_RESET.name().equals(row.getString("action"))).toList();
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getString("performedBy")).isEqualTo("root-1");
            assertThat(row.getString("resourceId")).isEqualTo(staff.getId());
            assertThat(row.toJson()).as("the row never carries the password").doesNotContain(reset.temporaryPassword());
        });
        assertThat(db.all("identity_account_events").toString()).doesNotContain(reset.temporaryPassword());
        assertThat(db.all("identity_users").toString()).doesNotContain(reset.temporaryPassword());
    }

    @Test
    @DisplayName("two resets give two different passwords, and only the last one is the one Keycloak holds")
    void eachResetIsItsOwnPassword() {
        User staff = openedStaff("reset.twice@example.org", UserType.ADMIN);

        String first = as(caller("root-1", "SUPER_ADMIN"), resolver.resetStaffTemporaryPassword(staff.getId())).block().temporaryPassword();
        String second = as(caller("root-1", "SUPER_ADMIN"), resolver.resetStaffTemporaryPassword(staff.getId())).block().temporaryPassword();

        assertThat(second).isNotEqualTo(first);
        assertThat(keycloak.temporaryPasswords.get(staff.getKeycloakUserId())).isEqualTo(second);
    }

    @Test
    @DisplayName("an administrator cannot reset a staff password, and nothing is changed or cut")
    void adminCannotReset() {
        User staff = openedStaff("reset.admin@example.org", UserType.FINANCE);
        String original = keycloak.temporaryPasswords.get(staff.getKeycloakUserId());

        assertThatThrownBy(() -> as(caller("admin-1", "ADMIN"), resolver.resetStaffTemporaryPassword(staff.getId())).block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
        assertThatThrownBy(() -> as(caller("buyer-1", "CUSTOMER"), resolver.resetStaffTemporaryPassword(staff.getId())).block())
                .isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);

        assertThat(keycloak.temporaryPasswords.get(staff.getKeycloakUserId())).isEqualTo(original);
        assertThat(CUT).isEmpty();
    }

    @Test
    @DisplayName("a customer or organizer account is refused NOT_A_STAFF_ACCOUNT and its sign-in is untouched")
    void buyerAccountIsRefused() {
        User buyer = User.builder().id("buyer-9").username("buyer-9").keycloakUserId("kc-buyer-9")
                .roles(java.util.EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER)).createdAt(NOW).updatedAt(NOW).build();
        com.pml.identity.account.AccountStates.apply(buyer, AccountState.ACTIVE);
        db.template.insert(buyer).block();
        long callsBefore = keycloak.calls.size();

        ErrorCode code = refusal(() -> as(caller("root-1", "SUPER_ADMIN"), resolver.resetStaffTemporaryPassword("buyer-9")).block());

        assertThat(code).isEqualTo(ErrorCode.NOT_A_STAFF_ACCOUNT);
        assertThat(keycloak.calls).hasSize((int) callsBefore);
        assertThat(CUT).isEmpty();
        assertThat(db.all("identity_audit_logs")).isEmpty();
    }

    @Test
    @DisplayName("an unknown account is refused USER_UNKNOWN")
    void unknownAccountIsRefused() {
        ErrorCode code = refusal(() -> as(caller("root-1", "SUPER_ADMIN"), resolver.resetStaffTemporaryPassword("nobody")).block());

        assertThat(code).isEqualTo(ErrorCode.USER_UNKNOWN);
    }
}
