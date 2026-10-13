package com.pml.identity.workflow.staff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountService;
import com.pml.identity.account.ContactService;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.StaffAccountPassword;
import com.pml.identity.account.StaffAccountRequest;
import com.pml.identity.account.StaffAccountSeal;
import com.pml.identity.account.StaffAccountSteps;
import com.pml.identity.account.TemporaryPasswordGenerator;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.ContactNotice;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.TemporalTestEnvironments;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.security.tenancy.StaffRole;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opening a staff account on the in-process Temporal server, with the real MongoDB steps, the real
 * unique indexes and a Keycloak that can refuse or be down.
 *
 * <p>The properties: the person is created complete or not at all; a request repeated while the first is
 * still running, or after the service restarted, ends on one account and one Keycloak user; a step that
 * fails after Keycloak holds the person is retried to completion; a step that cannot finish takes the
 * Keycloak user and the record back; an execution that is killed leaves a record that a repeated request
 * finishes; nobody who is not a super administrator, and no non-staff role, ever starts an execution; the
 * history holds no address, name or password; and it replays.</p>
 */
@Tag("L3")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · StaffAccountWorkflow opens a staff account completely or takes everything back")
class StaffAccountWorkflowTest {

    private static final Clock CLOCK = TestClock.frozenAt("2026-10-13T08:00:00Z");
    private static AccountFixtures db;

    private TestWorkflowEnvironment env;
    private FakeKeycloak keycloak;
    private CapturedMessages captured;
    private FaultyActivities activities;
    private StaffAccountProcess process;
    private StaffAccountSteps steps;
    private StaffAccountSeal seal;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures("identity_staff_account_workflow", CLOCK);
        // The indexes that make a duplicate impossible whatever the pre-checks saw, as the service builds them.
        for (String field : List.of("email", "username", "phoneNumber", "keycloakUserId")) {
            db.template.indexOps("identity_users").createIndex(new Index().on(field, Sort.Direction.ASC).unique()
                            .named("idx_" + field)
                            .partial(PartialIndexFilter.of(Criteria.where(field).type(JsonSchemaObject.Type.stringType()))))
                    .block();
        }
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void start() {
        db.reset();
        keycloak = new FakeKeycloak();
        captured = new CapturedMessages();
        launch(true);
    }

    /** A fresh engine, on the wall clock when a test holds an activity open from its own thread. */
    private void launch(boolean timeSkipping) {
        if (env != null) {
            env.close();
        }
        KeycloakProperties properties = new KeycloakProperties();
        AccountService accounts = new AccountService(db.template, db.transaction, db.outbox, keycloak,
                new DefaultListableBeanFactory().getBeanProvider(
                        com.pml.identity.security.revocation.MongoRevocationStore.class), properties, CLOCK);
        seal = new StaffAccountSeal(new ObjectMapper(), AccountFixtures.CRYPTO);
        DeliveryOrchestrator delivery = new DeliveryOrchestrator(List.of(new CapturingProvider(captured, CLOCK)),
                new SimpleMeterRegistry());
        steps = new StaffAccountSteps(db.template, db.transaction, keycloak, properties, accounts,
                new ContactService(db.template, AccountFixtures.HASHER), seal, AccountFixtures.HASHER, delivery, CLOCK);
        activities = new FaultyActivities(new StaffAccountActivitiesImpl(steps));
        env = TemporalTestEnvironments.newInstance(timeSkipping);
        Worker worker = env.newWorker(TaskQueues.ONBOARDING);
        worker.registerWorkflowImplementationTypes(StaffAccountWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        process = new StaffAccountProcess(new TemporalGateway(env.getWorkflowClient()), db.template,
                AccountFixtures.HASHER, seal, steps, new TemporaryPasswordGenerator(), Duration.ofSeconds(30));
    }

    @AfterEach
    void stop() {
        env.close();
    }

    // ---------------------------------------------------------------------------------- helpers

    private static Context asSuperAdmin() {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken("root-1", "n", StaffRole.SUPER_ADMIN.authority()));
    }

    private static Context asAdmin() {
        return ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken("admin-1", "n", StaffRole.ADMIN.authority()));
    }

    private static StaffAccountRequest request(String email, UserType role) {
        return new StaffAccountRequest(email, "Ops", "Person", null, role, "root-1");
    }

    private Mono<User> create(StaffAccountRequest request) {
        return process.create(request).map(StaffAccountPassword::user).contextWrite(asSuperAdmin());
    }

    private StaffAccountPassword createWithPassword(StaffAccountRequest request) {
        return process.create(request).contextWrite(asSuperAdmin()).block();
    }

    private User created(String email, UserType role) {
        return create(request(email, role)).block();
    }

    private static String address() {
        return "staff-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
    }

    private User stored(String id) {
        return db.template.findById(id, User.class).block();
    }

    private long users() {
        return db.count("identity_users");
    }

    private static ErrorCode refusalOf(Runnable call) {
        try {
            call.run();
        } catch (DomainRefusal refusal) {
            return refusal.errorCode();
        }
        throw new AssertionError("expected a refusal");
    }

    private long keycloakCreates() {
        return keycloak.calls.stream().filter("createStaffUser"::equals).count();
    }

    private String history(String email) {
        String id = WorkflowIds.staffAccountCreate(AccountFixtures.HASHER.hash(ContactType.EMAIL, email));
        return env.getWorkflowExecutionHistory(WorkflowExecution.newBuilder().setWorkflowId(id).build()).toJson(true);
    }

    private void assertNothingLeft() {
        assertThat(users()).as("no record").isZero();
        assertThat(keycloak.staff).as("no Keycloak person").isEmpty();
    }

    // ---------------------------------------------------------------------------------- the flow

    @Test
    @DisplayName("a super administrator opens a staff account: ACTIVE, one staff role, creator recorded, sign-in opened, no customer contact")
    void opensTheAccount() {
        String email = address();
        StaffAccountRequest asked = new StaffAccountRequest(email, "Ops", "Person", "+260971234567",
                UserType.FINANCE, "root-1");

        StaffAccountPassword opened = createWithPassword(asked);
        User result = opened.user();

        User account = stored(result.getId());
        assertThat(account.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(account.isActive()).isTrue();
        assertThat(account.getCreatedVia()).isEqualTo(AccountService.STAFF_ADMIN);
        assertThat(account.getCreatedBy()).as("who created whom").isEqualTo("root-1");
        assertThat(account.getUpdatedBy()).isEqualTo("root-1");
        assertThat(account.getRoles()).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.FINANCE);
        assertThat(account.getEmail()).isEqualTo(email);
        assertThat(account.getUsername()).isEqualTo(email);
        assertThat(account.getPhoneNumber()).isEqualTo("+260971234567");
        assertThat(account.getStaffCreation()).as("the creating state is gone").isNull();
        assertThat(account.getId()).as("the account's id is the Keycloak id, as for every staff account")
                .isEqualTo(account.getKeycloakUserId());

        assertThat(keycloak.staff).hasSize(1).containsKey(account.getKeycloakUserId());
        assertThat(keycloak.staff.get(account.getKeycloakUserId()).enabled()).as("sign-in opened at the end").isTrue();
        assertThat(keycloak.staffRoles.get(account.getKeycloakUserId())).containsExactly("FINANCE");
        assertThat(opened.temporaryPassword()).as("the call that created the account carries the password").isNotBlank();
        assertThat(keycloak.temporaryPasswords.get(account.getKeycloakUserId()))
                .as("the password shown is the one Keycloak holds").isEqualTo(opened.temporaryPassword());
        assertThat(keycloak.byUsername).as("nothing in the buyer realm").isEmpty();
        assertThat(db.count("identity_contacts")).as("a staff account carries no customer contact").isZero();

        var grants = db.all("identity_audit_logs").stream().filter(row -> "ROLE_GRANT".equals(row.getString("action"))).toList();
        assertThat(grants).singleElement().satisfies(row -> {
            assertThat(row.getString("performedBy")).isEqualTo("root-1");
            assertThat(row.getString("resourceId")).isEqualTo(account.getId());
        });
        assertThat(captured.noticesTo(email)).extracting(CapturedMessages.Notice::notice)
                .containsExactly(ContactNotice.STAFF_ACCOUNT_CREATED);
    }

    @Test
    @DisplayName("the execution's history and visibility hold no address, name, number or password")
    void historyHoldsNoPersonalData() {
        String email = address();
        StaffAccountPassword opened = createWithPassword(
                new StaffAccountRequest(email, "Chileshe", "Mwansa", "+260971234567", UserType.ADMIN, "root-1"));

        String history = history(email);

        assertThat(history).contains("StaffAccountClaim")
                .doesNotContain(email).doesNotContain(email.substring(0, email.indexOf('@')))
                .doesNotContain(opened.temporaryPassword())
                .doesNotContain("Chileshe").doesNotContain("Mwansa").doesNotContain("260971234567");
        assertThat(WorkflowIds.staffAccountCreate(AccountFixtures.HASHER.hash(ContactType.EMAIL, email))).doesNotContain("@");
    }

    @Test
    @DisplayName("the recorded history replays against the implementation")
    void theHistoryReplays() throws Exception {
        String email = address();
        activities.failLink.set(1);
        created(email, UserType.SUPER_ADMIN);

        WorkflowReplayer.replayWorkflowExecution(history(email), StaffAccountWorkflowImpl.class);
    }

    @Test
    @DisplayName("a failure after Keycloak holds the person, before the record is complete, is retried to completion with one Keycloak user")
    void retriesToCompletionAfterKeycloakCreate() {
        String email = address();
        activities.failLink.set(2);
        activities.failGrant.set(2);
        activities.failActivate.set(1);

        User result = created(email, UserType.FINANCE_LEAD);

        assertThat(stored(result.getId()).getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(keycloakCreates()).as("the Keycloak create ran once").isEqualTo(1);
        assertThat(keycloak.staff).hasSize(1);
        assertThat(users()).isEqualTo(1);
        assertThat(keycloak.staffRoles.get(result.getKeycloakUserId())).containsExactly("FINANCE_LEAD");
    }

    @Test
    @DisplayName("a Keycloak that is down for a moment is waited out; the person is created once it answers")
    void keycloakDownForAMoment() {
        keycloak.failNext.set(3);

        User result = created(address(), UserType.ADMIN);

        assertThat(stored(result.getId()).getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(keycloak.staff).hasSize(1);
    }

    // ---------------------------------------------------------------------------------- cannot finish

    @Test
    @DisplayName("a role grant that cannot succeed takes back the Keycloak user and the record, and the address can be used again")
    void failedGrantIsCompensated() {
        String email = address();
        keycloak.failGrants = true;

        assertThatThrownBy(() -> create(request(email, UserType.FINANCE)).block()).isInstanceOf(Exception.class);

        assertNothingLeft();
        assertThat(keycloak.calls).contains("createStaffUser", "deleteStaffUser");

        keycloak.failGrants = false;
        User again = created(email, UserType.FINANCE);
        assertThat(stored(again.getId()).getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(keycloak.staff).hasSize(1);
    }

    @Test
    @DisplayName("an activation that cannot finish takes everything back too: no enabled Keycloak user is left behind")
    void failedActivationIsCompensated() {
        activities.failActivate.set(100);

        assertThatThrownBy(() -> create(request(address(), UserType.ADMIN)).block()).isInstanceOf(Exception.class);

        assertNothingLeft();
    }

    @Test
    @DisplayName("a number another account holds is refused cleanly and leaves no record and no Keycloak user")
    void duplicateNumberIsRefused() {
        created(address(), UserType.ADMIN, "+260971234567");

        StaffAccountRequest second = new StaffAccountRequest(address(), "Ops", "Person", "+260971234567",
                UserType.ADMIN, "root-1");
        ErrorCode code = refusalOf(() -> create(second).block());

        assertThat(code).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(users()).isEqualTo(1);
        assertThat(keycloak.staff).hasSize(1);
    }

    private User created(String email, UserType role, String phone) {
        return create(new StaffAccountRequest(email, "Ops", "Person", phone, role, "root-1")).block();
    }

    @Test
    @DisplayName("the same administrator repeating a finished request gets the same account back, with no second Keycloak call or notice")
    void repeatOfAFinishedRequestReturnsTheSameAccount() {
        String email = address();
        StaffAccountPassword opened = createWithPassword(request(email, UserType.FINANCE));
        User first = opened.user();
        String applied = keycloak.temporaryPasswords.get(first.getKeycloakUserId());
        long calls = keycloak.calls.size();

        StaffAccountPassword repeated = createWithPassword(request(email, UserType.FINANCE));
        User again = repeated.user();

        assertThat(repeated.temporaryPassword()).as("shown once: a repeat of a finished request returns none").isNull();
        assertThat(keycloak.temporaryPasswords.get(first.getKeycloakUserId())).as("and applies none").isEqualTo(applied);
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(users()).isEqualTo(1);
        assertThat(keycloak.calls).hasSize((int) calls);
        assertThat(captured.noticesTo(email)).hasSize(1);
    }

    @Test
    @DisplayName("another administrator repeating the address, or the same one asking for another role, is refused as a duplicate")
    void anotherRequesterIsRefused() {
        String email = address();
        created(email, UserType.FINANCE);

        StaffAccountRequest other = new StaffAccountRequest(email, "Ops", "Person", null, UserType.FINANCE, "root-2");
        assertThat(refusalOf(() -> create(other).block())).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(refusalOf(() -> create(request(email, UserType.SUPER_ADMIN)).block())).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(users()).isEqualTo(1);
    }

    @Test
    @DisplayName("an address another account already holds is refused as RESOURCE_CONFLICT before anything is created")
    void duplicateAddressIsRefused() {
        String email = address();
        created(email, UserType.ADMIN);
        long creates = keycloakCreates();

        ErrorCode code = refusalOf(() -> create(request(email, UserType.FINANCE)).block());

        assertThat(code).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(users()).isEqualTo(1);
        assertThat(keycloakCreates()).isEqualTo(creates);
    }

    @Test
    @DisplayName("an address that is a buyer's verified contact is refused, and nothing is created")
    void buyerAddressIsRefused() {
        String email = address();
        var proof = db.proofFor(ContactType.EMAIL, email, "p-" + UUID.randomUUID());
        db.template.insert(com.pml.identity.domain.model.Contact.builder().id("c-1").accountId("buyer-1").type(ContactType.EMAIL)
                .valueHash(proof.contactKey()).valueEncrypted(proof.valueEncrypted()).valueMasked(proof.valueMasked())
                .verifiedAt(CLOCK.instant()).primary(true).source("OTP").createdAt(CLOCK.instant()).build()).block();
        db.template.insert(User.builder().id("buyer-1").username("buyer-1").createdAt(CLOCK.instant()).build()).block();

        ErrorCode code = refusalOf(() -> create(request(email, UserType.ADMIN)).block());

        assertThat(code).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(keycloak.staff).isEmpty();
    }

    @Test
    @DisplayName("every created account gets its own password: unrelated values, each one the value Keycloak holds")
    void everyCreationGetsItsOwnPassword() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) {
            StaffAccountPassword opened = createWithPassword(request(address(), UserType.ADMIN));
            assertThat(opened.temporaryPassword()).hasSizeGreaterThanOrEqualTo(16);
            assertThat(keycloak.temporaryPasswords.get(opened.user().getKeycloakUserId()))
                    .isEqualTo(opened.temporaryPassword());
            seen.add(opened.temporaryPassword());
        }
        assertThat(seen).hasSize(5);
    }

    @Test
    @DisplayName("a step retried after the person exists keeps applying the one password of the request, never a different one")
    void retriedStepsKeepTheSamePassword() {
        activities.failLink.set(2);
        activities.failGrant.set(1);

        StaffAccountPassword opened = createWithPassword(request(address(), UserType.FINANCE));

        assertThat(keycloak.temporaryPasswords.get(opened.user().getKeycloakUserId()))
                .isEqualTo(opened.temporaryPassword());
        assertThat(keycloak.calls.stream().filter("issueTemporaryPassword"::equals).count())
                .as("applied at least once, and repeated applications are harmless").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a Keycloak user that was already there keeps its own credential: no password is issued for it")
    void adoptedUserKeepsItsCredential() {
        String email = address();
        String existing = UUID.randomUUID().toString();
        keycloak.staff.put(existing, new com.pml.identity.account.KeycloakUserAdminPort.KeycloakUserView(existing, email, email,
                "Old", "Person", false, false, java.util.Set.of()));
        keycloak.temporaryPasswords.put(existing, "their-own-password");

        createWithPassword(request(email, UserType.FINANCE));

        assertThat(keycloak.temporaryPasswords.get(existing)).isEqualTo("their-own-password");
    }

    // ---------------------------------------------------------------------------------- who and what may start it

    @Test
    @DisplayName("an administrator is refused ACTOR_NOT_PERMITTED for any staff role, before any execution exists")
    void administratorIsRefusedBeforeAnythingStarts() {
        String email = address();

        for (UserType role : List.of(UserType.SUPER_ADMIN, UserType.ADMIN, UserType.FINANCE, UserType.FINANCE_LEAD)) {
            ErrorCode code = refusalOf(() -> process.create(request(email, role)).contextWrite(asAdmin()).block());
            assertThat(code).as(role.name()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        }

        assertNothingLeft();
        assertThat(keycloak.calls).isEmpty();
        assertNoExecution(email);
    }

    @Test
    @DisplayName("a caller with no authentication at all is refused and nothing starts")
    void anonymousIsRefused() {
        String email = address();

        ErrorCode code = refusalOf(() -> process.create(request(email, UserType.ADMIN)).block());

        assertThat(code).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertNoExecution(email);
        assertNothingLeft();
    }

    @Test
    @DisplayName("a customer, an organizer or no role is refused as not well formed for a super administrator, and nothing starts")
    void buyerRolesAreRefusedBeforeAnythingStarts() {
        String email = address();
        for (UserType role : new UserType[]{UserType.CUSTOMER, UserType.ORGANIZER, null}) {
            ErrorCode code = refusalOf(() -> create(request(email, role)).block());
            assertThat(code).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        }

        assertNothingLeft();
        assertNoExecution(email);
    }

    @Test
    @DisplayName("an address or a number that does not parse is refused before anything starts")
    void malformedInputIsRefusedBeforeAnythingStarts() {
        assertThat(refusalOf(() -> create(request("not-an-address", UserType.ADMIN)).block())).isEqualTo(ErrorCode.CONTACT_INVALID);
        assertThat(refusalOf(() -> create(new StaffAccountRequest(address(), "Ops", "Person", "12",
                UserType.ADMIN, "root-1")).block())).isEqualTo(ErrorCode.PHONE_NUMBER_INVALID);

        assertNothingLeft();
        assertThat(keycloak.calls).isEmpty();
    }

    private void assertNoExecution(String email) {
        String id = WorkflowIds.staffAccountCreate(AccountFixtures.HASHER.hash(ContactType.EMAIL, email));
        WorkflowClient client = env.getWorkflowClient();
        assertThatThrownBy(() -> client.newUntypedWorkflowStub(id).describe())
                .as("no execution was started for this address")
                .isInstanceOfAny(WorkflowNotFoundException.class, io.grpc.StatusRuntimeException.class);
    }

    // ---------------------------------------------------------------------------------- restart and duplicates

    @Test
    @DisplayName("a request repeated while the first is still running joins it: one account, one Keycloak create, same id returned")
    void duplicateWhileRunningConverges() throws Exception {
        launch(false);
        String email = address();
        activities.hold();

        CompletableFuture<StaffAccountPassword> first = CompletableFuture.supplyAsync(
                () -> createWithPassword(request(email, UserType.FINANCE)));
        assertThat(activities.held.await(30, TimeUnit.SECONDS)).as("the first request reached the role grant").isTrue();
        assertThat(users()).as("the unfinished record is the only account").isEqualTo(1);
        assertThat(stored(db.all("identity_users").get(0).getString("_id")).getStatus()).isEqualTo(AccountState.PROVISIONING);
        assertThat(keycloak.staff.values()).as("the person exists in Keycloak, unable to sign in")
                .singleElement().satisfies(person -> assertThat(person.enabled()).isFalse());

        CompletableFuture<StaffAccountPassword> second = CompletableFuture.supplyAsync(
                () -> createWithPassword(request(email, UserType.FINANCE)));
        Thread.sleep(500);
        activities.releaseHold();

        StaffAccountPassword firstAnswer = first.get(60, TimeUnit.SECONDS);
        StaffAccountPassword secondAnswer = second.get(60, TimeUnit.SECONDS);
        User one = firstAnswer.user();
        User two = secondAnswer.user();
        assertThat(firstAnswer.temporaryPassword()).as("the call that started the execution has its password").isNotBlank();
        assertThat(secondAnswer.temporaryPassword()).as("a call that met another's execution has none to show").isNull();
        assertThat(keycloak.temporaryPasswords.get(one.getKeycloakUserId())).isEqualTo(firstAnswer.temporaryPassword());
        assertThat(two.getId()).isEqualTo(one.getId());
        assertThat(users()).isEqualTo(1);
        assertThat(keycloakCreates()).isEqualTo(1);
        assertThat(stored(one.getId()).getStatus()).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("an execution killed part way leaves an unfinished record that cannot sign in; the same request finishes it with no second person")
    void killedExecutionIsFinishedByTheSameRequest() throws Exception {
        launch(false);
        String email = address();
        activities.hold();
        String workflowId = WorkflowIds.staffAccountCreate(AccountFixtures.HASHER.hash(ContactType.EMAIL, email));

        CompletableFuture<User> doomed = CompletableFuture.supplyAsync(() -> create(request(email, UserType.ADMIN)).block());
        assertThat(activities.held.await(30, TimeUnit.SECONDS)).isTrue();
        env.getWorkflowClient().newUntypedWorkflowStub(workflowId).terminate("an operator stopped it");
        activities.releaseHold();
        assertThat(activities.heldDone.await(30, TimeUnit.SECONDS)).as("the orphaned activity ended").isTrue();
        assertThatThrownBy(() -> doomed.get(30, TimeUnit.SECONDS)).isInstanceOf(Exception.class);

        assertThat(users()).isEqualTo(1);
        User leftover = stored(db.all("identity_users").get(0).getString("_id"));
        assertThat(leftover.getStatus()).isEqualTo(AccountState.PROVISIONING);
        assertThat(keycloak.staff.values()).singleElement().satisfies(person -> assertThat(person.enabled()).isFalse());
        long creates = keycloakCreates();
        String firstPassword = keycloak.temporaryPasswords.get(keycloak.staff.keySet().iterator().next());

        StaffAccountPassword completed = createWithPassword(request(email, UserType.ADMIN));
        User finished = completed.user();

        assertThat(completed.temporaryPassword()).as("an unfinished request, repeated, gets a fresh password").isNotBlank()
                .isNotEqualTo(firstPassword);
        assertThat(keycloak.temporaryPasswords.get(finished.getKeycloakUserId()))
                .as("and it is the one Keycloak now holds").isEqualTo(completed.temporaryPassword());
        assertThat(stored(finished.getId()).getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(users()).isEqualTo(1);
        assertThat(keycloak.staff).hasSize(1);
        assertThat(keycloak.staff.get(finished.getKeycloakUserId()).enabled()).isTrue();
        assertThat(keycloakCreates()).as("the Keycloak user was found again, not created twice").isEqualTo(creates);
    }

    @Test
    @DisplayName("a repeated request for another role replaces the unfinished record; the role that is honoured is the one in hand")
    void anotherRoleReplacesAnUnfinishedRecord() throws Exception {
        launch(false);
        String email = address();
        activities.hold();
        String workflowId = WorkflowIds.staffAccountCreate(AccountFixtures.HASHER.hash(ContactType.EMAIL, email));
        CompletableFuture<User> doomed = CompletableFuture.supplyAsync(() -> create(request(email, UserType.FINANCE)).block());
        assertThat(activities.held.await(30, TimeUnit.SECONDS)).isTrue();
        env.getWorkflowClient().newUntypedWorkflowStub(workflowId).terminate("an operator stopped it");
        activities.releaseHold();
        assertThat(activities.heldDone.await(30, TimeUnit.SECONDS)).as("the orphaned activity ended").isTrue();
        assertThatThrownBy(() -> doomed.get(30, TimeUnit.SECONDS)).isInstanceOf(Exception.class);

        User finished = create(request(email, UserType.SUPER_ADMIN)).block();

        User account = stored(finished.getId());
        assertThat(account.getRoles()).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.SUPER_ADMIN);
        assertThat(users()).isEqualTo(1);
        assertThat(keycloak.staff).as("the first attempt's Keycloak user was removed, not left beside the new one").hasSize(1);
        assertThat(keycloak.staffRoles.get(account.getKeycloakUserId())).containsExactly("SUPER_ADMIN");
    }

    @Test
    @DisplayName("a Keycloak user that was already there is adopted and is never removed by an undo or enabled by this process")
    void adoptedKeycloakUserIsNeverRemoved() {
        String email = address();
        String existing = UUID.randomUUID().toString();
        keycloak.staff.put(existing, new com.pml.identity.account.KeycloakUserAdminPort.KeycloakUserView(existing, email, email,
                "Old", "Person", false, false, java.util.Set.of()));
        keycloak.failGrants = true;

        assertThatThrownBy(() -> create(request(email, UserType.FINANCE)).block()).isInstanceOf(Exception.class);

        assertThat(users()).as("the record is removed").isZero();
        assertThat(keycloak.staff).as("the user that was already there is untouched").containsKey(existing);
        assertThat(keycloak.staff.get(existing).enabled()).isFalse();
        assertThat(keycloak.calls).doesNotContain("deleteStaffUser");
    }

    @Test
    @DisplayName("a request that waits longer than its bound is told STAFF_CREATION_IN_PROGRESS and the work carries on to the same account")
    void answersInProgressAndCarriesOn() throws Exception {
        launch(false);
        StaffAccountProcess impatient = new StaffAccountProcess(new TemporalGateway(env.getWorkflowClient()), db.template,
                AccountFixtures.HASHER, seal, steps, new TemporaryPasswordGenerator(), Duration.ofMillis(300));
        String email = address();
        activities.hold();

        ErrorCode code = refusalOf(() -> impatient.create(request(email, UserType.ADMIN)).contextWrite(asSuperAdmin()).block());

        assertThat(code).isEqualTo(ErrorCode.STAFF_CREATION_IN_PROGRESS);
        assertThat(users()).as("no half-made account was returned, but the record exists and is being finished").isEqualTo(1);
        activities.releaseHold();
        User again = create(request(email, UserType.ADMIN)).block();
        assertThat(stored(again.getId()).getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(keycloak.staff).hasSize(1);
        assertThat(keycloakCreates()).isEqualTo(1);
    }

    /** The real activities, with failures that can be scheduled at chosen steps and one that can be held open. */
    static final class FaultyActivities implements StaffAccountActivities {
        private final StaffAccountActivities real;
        final AtomicInteger failLink = new AtomicInteger();
        final AtomicInteger failGrant = new AtomicInteger();
        final AtomicInteger failActivate = new AtomicInteger();
        volatile CountDownLatch held = new CountDownLatch(1);
        /** Counted down when the held role grant has finished, after the execution that asked for it may be gone. */
        volatile CountDownLatch heldDone = new CountDownLatch(1);
        private volatile CountDownLatch released;
        private final AtomicBoolean holdOnce = new AtomicBoolean();

        FaultyActivities(StaffAccountActivities real) {
            this.real = real;
        }

        /** The next role grant stops until {@link #releaseHold()}; the test is told when it has arrived. */
        void hold() {
            held = new CountDownLatch(1);
            heldDone = new CountDownLatch(1);
            released = new CountDownLatch(1);
            holdOnce.set(true);
        }

        void releaseHold() {
            CountDownLatch gate = released;
            if (gate != null) {
                gate.countDown();
            }
        }

        private static void die(AtomicInteger counter, String where) {
            if (counter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new IllegalStateException("worker crashed " + where);
            }
        }

        @Override
        public Claimed claim(String sealed) {
            return real.claim(sealed);
        }

        @Override
        public KeycloakUser createInKeycloak(String accountId, String sealed) {
            return real.createInKeycloak(accountId, sealed);
        }

        @Override
        public String link(Link link) {
            String id = real.link(link);
            // the write landed and the worker died before answering: the retry must find it done
            die(failLink, "after the link");
            return id;
        }

        @Override
        public void grantRole(String accountId) {
            boolean heldHere = holdOnce.compareAndSet(true, false);
            if (heldHere) {
                held.countDown();
                try {
                    released.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                die(failGrant, "before the role grant");
                real.grantRole(accountId);
            } finally {
                if (heldHere) {
                    heldDone.countDown();
                }
            }
        }

        @Override
        public void activate(String accountId) {
            die(failActivate, "before the activation");
            real.activate(accountId);
        }

        @Override
        public void compensate(String accountId) {
            real.compensate(accountId);
        }

        @Override
        public void notifyCreated(String accountId) {
            real.notifyCreated(accountId);
        }
    }
}
