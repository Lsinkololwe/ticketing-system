package com.pml.identity.boot;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.account.AccountRepair;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.config.IdentityAccountRepairProperties;
import com.pml.identity.workflow.repair.AccountRepairSchedule;
import com.pml.identity.workflow.repair.AccountRepairWorkflow;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * The account repair against real MongoDB, Temporal, Redis and Keycloak 26.5.2 (ET-IDN-004 R4, drift D1..D9).
 * Each class is injected straight into MongoDB or Keycloak, the repair is run, and the test asserts the converged
 * state, the {@code REPAIR_Dn} audit entry naming the class, and that a second run changes nothing. A proxy in
 * front of Keycloak answers genuine HTTP 503 so an outage mid-repair can be tested.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ET-IDN-004 · AccountRepair heals each drift class D1..D9 against real Keycloak, audits it, and a second run changes nothing")
@SpringBootTest(classes = IdentityServiceApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class AccountRepairEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String BUYERS = "myticketzm";
    private static final int KEYCLOAK_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final int PROXY_PORT = TestSocketUtils.findAvailableTcpPort();

    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
            .withRealmImportFiles("/keycloak/myticketzm-realm.json", "/keycloak/myticketzm-admin-realm.json")
            .withCreateContainerCmdModifier(cmd -> {
                Ports ports = new Ports();
                ports.bind(ExposedPort.tcp(8080), Ports.Binding.bindPort(KEYCLOAK_PORT));
                ports.bind(ExposedPort.tcp(8443), Ports.Binding.empty());
                ports.bind(ExposedPort.tcp(9000), Ports.Binding.empty());
                cmd.getHostConfig().withPortBindings(ports);
            });
    private static KeycloakFaultProxy PROXY;

    static {
        KEYCLOAK.start();
        try {
            PROXY = new KeycloakFaultProxy(PROXY_PORT, "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
        registry.add("keycloak.server-url", () -> "http://127.0.0.1:" + PROXY_PORT);
        registry.add("keycloak.admin-username", KEYCLOAK::getAdminUsername);
        registry.add("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK::getAdminPassword);
        registry.add("identity.challenge.cooldown", () -> "PT1S");
        registry.add("identity.account.repair.enabled", () -> "true");
        registry.add("identity.account.repair.interval", () -> "PT15M");
    }

    @AfterAll
    static void stop() {
        PROXY.close();
        KEYCLOAK.stop();
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    CapturedMessages captured;
    @Autowired
    AccountRepair repair;
    @Autowired
    WorkflowClient workflowClient;
    @Autowired
    ScheduleClient scheduleClient;
    @Autowired
    IdentityAccountRepairProperties repairProperties;
    @Autowired
    org.springframework.data.mongodb.core.ReactiveMongoTemplate template;
    @Autowired
    com.pml.identity.security.ContactHasher hasher;
    @Autowired
    com.pml.identity.security.ContactCrypto crypto;

    // ---------------------------------------------------------------------------------- helpers

    private record Buyer(String accountId, String keycloakUserId, String contact) {
    }

    private WebTestClient api() {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(90)).build()
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write"),
                        new SimpleGrantedAuthority("SCOPE_internal-read")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        EntityExchangeResult<Map> result = api().post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange().expectBody(Map.class).returnResult();
        Map<String, Object> map = (Map<String, Object>) result.getResponseBody();
        map.put("httpStatus", result.getStatus().value());
        return map;
    }

    private Buyer signUp(String contact) {
        Map<String, Object> issued = post("/api/internal/auth/challenges",
                Map.of("contact", Map.of("value", contact), "clientIp", "203.0.113." + (1 + ThreadLocalRandom.current().nextInt(250))));
        assertThat(issued.get("httpStatus")).isEqualTo(202);
        Map<String, Object> verified = post("/api/internal/auth/challenges/verify",
                Map.of("challengeId", issued.get("challengeId"), "code", captured.lastCodeTo(contact).orElseThrow()));
        Map<String, Object>[] ensured = new Map[1];
        await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            ensured[0] = post("/api/internal/auth/accounts/ensure",
                    Map.of("proof", verified.get("proof"), "clientId", "myticketzm-web", "issueHandle", false));
            return !Integer.valueOf(202).equals(ensured[0].get("httpStatus"));
        });
        assertThat(ensured[0].get("httpStatus")).as("%s", ensured[0]).isEqualTo(200);
        String accountId = ensured[0].get("accountId").toString();
        return new Buyer(accountId, account(accountId).getString("keycloakUserId"), contact);
    }

    private static String email() {
        return "rp-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    private static String phone() {
        return "+26097" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    private Keycloak admin() {
        return KEYCLOAK.getKeycloakAdminClient();
    }

    private UserRepresentation kc(String keycloakUserId) {
        return admin().realm(BUYERS).users().get(keycloakUserId).toRepresentation();
    }

    private List<UserRepresentation> kcByUsername(String username) {
        return admin().realm(BUYERS).users().searchByUsername(username, true);
    }

    private List<Document> mongo(String collection, Document filter) {
        MongoClient client = IdentityStack.mongoClient();
        try {
            return Flux.from(client.getDatabase(DATABASE).getCollection(collection).find(filter)).collectList().block();
        } finally {
            client.close();
        }
    }

    private void mongoSet(String accountId, Document set, Document unset) {
        MongoClient client = IdentityStack.mongoClient();
        try {
            Document update = new Document();
            if (set != null) {
                update.append("$set", set);
            }
            if (unset != null) {
                update.append("$unset", unset);
            }
            Flux.from(client.getDatabase(DATABASE).getCollection("identity_users").updateOne(new Document("_id", accountId), update)).blockLast();
        } finally {
            client.close();
        }
    }

    private Document account(String accountId) {
        return mongo("identity_users", new Document("_id", accountId)).get(0);
    }

    private long audits(String accountId, String repairClass) {
        return mongo("identity_account_events", new Document("accountId", accountId).append("kind", "REPAIR_" + repairClass)).size();
    }

    private Map<String, Long> run() {
        return repair.run().block(Duration.ofMinutes(3));
    }

    /** The repairs a run made: its counts without the alert entries. */
    private static Map<String, Long> repaired(Map<String, Long> report) {
        Map<String, Long> out = new java.util.TreeMap<>(report);
        out.keySet().removeIf(key -> key.startsWith("alert:"));
        return out;
    }

    private void assertSecondRunIsANoOp() {
        long events = mongo("identity_account_events", new Document()).size();
        assertThat(repaired(run())).as("a second run finds nothing").isEmpty();
        assertThat(mongo("identity_account_events", new Document()).size()).as("and writes nothing").isEqualTo((int) events);
    }

    private Instant ago(Duration age) {
        return Instant.now().minus(age);
    }

    // ---------------------------------------------------------------------------------- the classes

    @Test
    @Order(1)
    @DisplayName("D1: the Keycloak user is gone; the repair creates it again by username, links it, restores role and accountId attribute, and audits D1")
    void d1MissingKeycloakUser() {
        Buyer buyer = signUp(email());
        admin().realm(BUYERS).users().delete(buyer.keycloakUserId()).close();
        assertThat(kcByUsername(buyer.accountId())).isEmpty();

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D1", 1L);
        List<UserRepresentation> users = kcByUsername(buyer.accountId());
        assertThat(users).hasSize(1);
        assertThat(account(buyer.accountId()).getString("keycloakUserId")).isEqualTo(users.get(0).getId());
        assertThat(users.get(0).getAttributes().get("accountId")).containsExactly(buyer.accountId());
        assertThat(users.get(0).getEmail()).as("D6 follows in the same pass").isEqualTo(buyer.contact());
        assertThat(admin().realm(BUYERS).users().get(users.get(0).getId()).roles().realmLevel().listAll())
                .extracting(RoleRepresentation::getName).contains("CUSTOMER");
        assertThat(audits(buyer.accountId(), "D1")).isEqualTo(1);
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(2)
    @DisplayName("D3: both exist but the link is unset; the repair links the user found by username = account id")
    void d3UnlinkedUser() {
        Buyer buyer = signUp(email());
        mongoSet(buyer.accountId(), null, new Document("keycloakUserId", ""));

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D3", 1L);
        assertThat(account(buyer.accountId()).getString("keycloakUserId")).isEqualTo(buyer.keycloakUserId());
        assertThat(audits(buyer.accountId(), "D3")).isEqualTo(1);
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(3)
    @DisplayName("D4: a SUSPENDED account is disabled in Keycloak; a user disabled in the console is adopted as SUSPENDED, never re-enabled")
    void d4EnabledVersusStatus() {
        Buyer suspended = signUp(email());
        mongoSet(suspended.accountId(), new Document("status", "SUSPENDED").append("accountStatus", "SUSPENDED").append("active", false), null);
        Buyer consoleDisabled = signUp(email());
        UserRepresentation user = kc(consoleDisabled.keycloakUserId());
        user.setEnabled(false);
        admin().realm(BUYERS).users().get(consoleDisabled.keycloakUserId()).update(user);

        Map<String, Long> report = run();

        assertThat(report.get("D4")).isEqualTo(2L);
        assertThat(kc(suspended.keycloakUserId()).isEnabled()).isFalse();
        assertThat(account(consoleDisabled.accountId()).getString("status")).isEqualTo("SUSPENDED");
        assertThat(account(consoleDisabled.accountId()).getBoolean("active")).isFalse();
        assertThat(kc(consoleDisabled.keycloakUserId()).isEnabled()).isFalse();
        assertThat(audits(suspended.accountId(), "D4")).isEqualTo(1);
        assertThat(audits(consoleDisabled.accountId(), "D4")).isEqualTo(1);
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(4)
    @DisplayName("D5: a role granted in the console and a lost CUSTOMER role and a wrong accountId attribute are reset from the database policy")
    void d5RolesAndAttribute() {
        Buyer buyer = signUp(email());
        var roles = admin().realm(BUYERS).users().get(buyer.keycloakUserId()).roles().realmLevel();
        roles.add(List.of(admin().realm(BUYERS).roles().get("ADMIN").toRepresentation()));
        roles.remove(List.of(admin().realm(BUYERS).roles().get("CUSTOMER").toRepresentation()));
        UserRepresentation user = kc(buyer.keycloakUserId());
        user.getAttributes().put("accountId", List.of("someone-else"));
        admin().realm(BUYERS).users().get(buyer.keycloakUserId()).update(user);

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D5", 1L);
        assertThat(admin().realm(BUYERS).users().get(buyer.keycloakUserId()).roles().realmLevel().listAll())
                .extracting(RoleRepresentation::getName).contains("CUSTOMER").doesNotContain("ADMIN");
        assertThat(kc(buyer.keycloakUserId()).getAttributes().get("accountId")).containsExactly(buyer.accountId());
        assertThat(audits(buyer.accountId(), "D5")).isEqualTo(1);
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(5)
    @DisplayName("D6: Keycloak's email and emailVerified are rewritten from the verified email contact; an email on an account with none is cleared")
    void d6EmailDrift() {
        Buyer emailBuyer = signUp(email());
        UserRepresentation user = kc(emailBuyer.keycloakUserId());
        user.setEmail("console-edit@example.com");
        user.setEmailVerified(false);
        admin().realm(BUYERS).users().get(emailBuyer.keycloakUserId()).update(user);
        Buyer phoneBuyer = signUp(phone());
        UserRepresentation other = kc(phoneBuyer.keycloakUserId());
        other.setEmail("stray@example.com");
        admin().realm(BUYERS).users().get(phoneBuyer.keycloakUserId()).update(other);

        Map<String, Long> report = run();

        assertThat(report.get("D6")).isEqualTo(2L);
        assertThat(kc(emailBuyer.keycloakUserId()).getEmail()).isEqualTo(emailBuyer.contact());
        assertThat(kc(emailBuyer.keycloakUserId()).isEmailVerified()).isTrue();
        assertThat(kc(phoneBuyer.keycloakUserId()).getEmail()).isNull();
        assertThat(audits(emailBuyer.accountId(), "D6")).isEqualTo(1);
        assertThat(mongo("identity_account_events", new Document("accountId", emailBuyer.accountId()).append("kind", "REPAIR_D6")).get(0).toJson())
                .as("the audit holds no contact").doesNotContain("console-edit").doesNotContain(emailBuyer.contact());
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(6)
    @DisplayName("D2: a Keycloak user with no account is adopted (ADOPTED, CUSTOMER by policy, flagged for review) and a console-granted role is stripped")
    void d2OrphanUserAdopted() {
        UserRepresentation orphan = new UserRepresentation();
        orphan.setUsername("orphan-" + UUID.randomUUID().toString().substring(0, 8));
        orphan.setEnabled(true);
        String id;
        try (var response = admin().realm(BUYERS).users().create(orphan)) {
            id = response.getLocation().getPath().substring(response.getLocation().getPath().lastIndexOf('/') + 1);
        }
        admin().realm(BUYERS).users().get(id).roles().realmLevel().add(List.of(admin().realm(BUYERS).roles().get("ORGANIZER").toRepresentation()));

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D2", 1L);
        Document adopted = account(id);
        assertThat(adopted.getString("createdVia")).isEqualTo("ADOPTED");
        assertThat(adopted.getString("keycloakUserId")).isEqualTo(id);
        assertThat(adopted.getString("status")).isEqualTo("ACTIVE");
        assertThat(adopted.getList("roles", String.class)).containsExactly("CUSTOMER");
        assertThat(admin().realm(BUYERS).users().get(id).roles().realmLevel().listAll())
                .extracting(RoleRepresentation::getName).contains("CUSTOMER").doesNotContain("ORGANIZER");
        assertThat(mongo("identity_account_events", new Document("accountId", id).append("kind", "REPAIR_D2")).get(0).get("data", Document.class)
                .getBoolean("needsReview")).isTrue();
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(7)
    @DisplayName("D7: a PROVISIONING account older than 10 minutes with no Keycloak user is resumed to ACTIVE, audited, and alerted on")
    void d7StalledProvisioning() {
        String accountId = UUID.randomUUID().toString();
        String address = email();
        var normalized = hasher.normalize(address, com.pml.identity.domain.enums.ContactType.EMAIL, null, null).orElseThrow();
        Instant created = ago(Duration.ofMinutes(25));
        template.insert(com.pml.identity.domain.model.Contact.builder().id(UUID.randomUUID().toString()).accountId(accountId)
                .type(com.pml.identity.domain.enums.ContactType.EMAIL).valueHash(normalized.key())
                .valueEncrypted(crypto.encrypt(normalized.value()).block()).valueMasked(normalized.masked())
                .verifiedAt(created).primary(true).source("OTP").createdAt(created).build()).block();
        com.pml.identity.domain.model.User stalled = com.pml.identity.domain.model.User.builder().id(accountId).username(accountId)
                .roles(java.util.EnumSet.of(com.pml.shared.constants.UserType.CUSTOMER)).createdVia("OTP").createdAt(created).updatedAt(created).build();
        com.pml.identity.account.AccountStates.apply(stalled, com.pml.identity.domain.enums.AccountState.PROVISIONING);
        template.insert(stalled).block();

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D7", 1L).containsEntry("alert:PROVISIONING", 1L);
        assertThat(account(accountId).getString("status")).isEqualTo("ACTIVE");
        assertThat(kcByUsername(accountId)).hasSize(1);
        assertThat(account(accountId).getString("keycloakUserId")).isEqualTo(kcByUsername(accountId).get(0).getId());
        assertThat(audits(accountId, "D7")).isEqualTo(1);
        assertThat(kcByUsername(accountId).get(0).getEmail()).as("the email contact reached Keycloak").isEqualTo(address);
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(8)
    @DisplayName("D8: an orphaned CHANGING marker past 48 hours is cleared; MERGING past 2 hours and DELETION_REQUESTED past 48 hours alert and change nothing else")
    void d8StaleMarkers() {
        Buyer changing = signUp(email());
        mongoSet(changing.accountId(), new Document("pendingKind", "CHANGING").append("pendingSince", Date.from(ago(Duration.ofHours(50)))), null);
        Buyer merging = signUp(email());
        mongoSet(merging.accountId(), new Document("pendingKind", "MERGING").append("pendingSince", Date.from(ago(Duration.ofHours(3)))), null);
        Buyer deleting = signUp(email());
        mongoSet(deleting.accountId(), new Document("pendingKind", "DELETION_REQUESTED").append("pendingSince", Date.from(ago(Duration.ofHours(60)))), null);

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D8", 1L).containsEntry("alert:MERGING", 1L).containsEntry("alert:DELETION_REQUESTED", 1L);
        assertThat(account(changing.accountId()).get("pendingKind")).isNull();
        assertThat(account(merging.accountId()).getString("pendingKind")).isEqualTo("MERGING");
        assertThat(account(deleting.accountId()).getString("pendingKind")).isEqualTo("DELETION_REQUESTED");
        assertThat(audits(changing.accountId(), "D8")).isEqualTo(1);
        assertThat(audits(merging.accountId(), "D8")).as("the alert is recorded once").isEqualTo(1);
        assertThat(repaired(run())).as("alerts repeat, repairs do not").isEmpty();
        assertThat(audits(merging.accountId(), "D8")).isEqualTo(1);
    }

    @Test
    @Order(9)
    @DisplayName("D9: a second Keycloak user claiming a linked account is disabled and a support task is opened; neither user is deleted, nothing is merged")
    void d9DuplicateKeycloakUser() {
        Buyer buyer = signUp(email());
        UserRepresentation duplicate = new UserRepresentation();
        duplicate.setUsername("dup-" + UUID.randomUUID().toString().substring(0, 8));
        duplicate.setEnabled(true);
        duplicate.setAttributes(Map.of("accountId", List.of(buyer.accountId())));
        String duplicateId;
        try (var response = admin().realm(BUYERS).users().create(duplicate)) {
            duplicateId = response.getLocation().getPath().substring(response.getLocation().getPath().lastIndexOf('/') + 1);
        }

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D9", 1L);
        assertThat(kc(duplicateId).isEnabled()).isFalse();
        assertThat(kc(buyer.keycloakUserId()).isEnabled()).isTrue();
        assertThat(account(buyer.accountId()).getString("keycloakUserId")).isEqualTo(buyer.keycloakUserId());
        assertThat(mongo("identity_users", new Document("_id", duplicateId))).as("not adopted as a stranger").isEmpty();
        Document task = mongo("identity_account_events", new Document("accountId", buyer.accountId()).append("kind", "REPAIR_D9")).get(0);
        assertThat(task.get("data", Document.class).getBoolean("supportTask")).isTrue();
        assertSecondRunIsANoOp();
    }

    // ---------------------------------------------------------------------------------- outage and workflow

    @Test
    @Order(10)
    @DisplayName("Keycloak answering 503 during a repair: the run fails, nothing is half-done, and the next run converges")
    void keycloakDownDuringRepair() {
        Buyer buyer = signUp(email());
        admin().realm(BUYERS).users().delete(buyer.keycloakUserId()).close();
        String linkBefore = account(buyer.accountId()).getString("keycloakUserId");
        PROXY.failBefore(10_000, request -> true);
        try {
            assertThatThrownBy(this::run).isNotNull();
        } finally {
            PROXY.failBefore(0, request -> true);
        }
        assertThat(PROXY.failures()).isPositive();
        assertThat(account(buyer.accountId()).getString("keycloakUserId")).as("the link is untouched while Keycloak is away").isEqualTo(linkBefore);
        assertThat(audits(buyer.accountId(), "D1")).isZero();

        Map<String, Long> report = run();

        assertThat(report).containsEntry("D1", 1L);
        assertThat(kcByUsername(buyer.accountId())).hasSize(1);
        assertSecondRunIsANoOp();
    }

    @Test
    @Order(11)
    @DisplayName("the Schedule identity-account-repair exists (PT15M, overlap SKIP), and the workflow it starts runs the passes and converges after Keycloak's 503s are retried")
    void scheduleAndWorkflow() throws Exception {
        ScheduleDescription description = scheduleClient.getHandle(AccountRepairSchedule.SCHEDULE_ID).describe();
        assertThat(description.getSchedule().getSpec().getIntervals()).extracting(ScheduleIntervalSpec::getEvery)
                .containsExactly(Duration.ofMinutes(15));
        assertThat(description.getSchedule().getPolicy().getOverlap()).isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);

        Buyer buyer = signUp(email());
        UserRepresentation user = kc(buyer.keycloakUserId());
        user.setEmail("drifted@example.com");
        admin().realm(BUYERS).users().get(buyer.keycloakUserId()).update(user);
        PROXY.failBefore(2, request -> request.startsWith("GET /admin/realms/" + BUYERS + "/users/"));

        AccountRepairWorkflow workflow = workflowClient.newWorkflowStub(AccountRepairWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId("account-repair/test-" + UUID.randomUUID()).setTaskQueue("identity-account").build());
        Map<String, Long> result = workflow.run();

        assertThat(result).containsEntry("D6", 1L);
        assertThat(PROXY.failures()).isPositive();
        assertThat(kc(buyer.keycloakUserId()).getEmail()).isEqualTo(buyer.contact());
        scheduleClient.getHandle(AccountRepairSchedule.SCHEDULE_ID).delete();
        assertThat(repairProperties.getInterval()).isEqualTo(Duration.ofMinutes(15));
    }

    @SuppressWarnings("unused")
    private static final Class<?>[] FIXTURES = {com.pml.shared.testing.MongoReplicaSet.class, com.pml.shared.testing.RedisNode.class,
            com.pml.shared.testing.TemporalDevServer.class};
}
