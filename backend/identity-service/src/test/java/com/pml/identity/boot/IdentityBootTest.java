package com.pml.identity.boot;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.account.AccountEnsurer;
import com.pml.identity.account.AccountStatusLookup;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.KeycloakAccountPort;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.account.ProofLookup;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.proof.LoginHandleService;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.auth.web.InternalChallengeController;
import com.pml.identity.auth.web.InternalEnsureController;
import com.pml.identity.auth.web.InternalHandleController;
import com.pml.identity.migration.AccountPreflightReportMigrationService;
import com.pml.identity.migration.IdentityMigrationRunner;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TemporalDevServer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * The whole identity-service context, booted once against a MongoDB replica set, Redis and a
 * Temporal server, with a fake Keycloak (ET-IDN-004). It proves the pieces other tests build by hand
 * actually wire: the Redis proof store, the outbox, the mail sender, the delivery capture, the
 * controllers and the Temporal workers - and that {@link IdentityMigrationRunner} runs end to end over
 * a database that already holds legacy users.
 *
 * <p>The legacy users are written BEFORE the context starts, so the runner meets them exactly as it
 * would on the first deploy of the new version: a phone-only account, an email-only account, and a pair
 * that differs only by the case of the email.</p>
 */
@Tag("L2")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · the identity-service context boots and the migration runner converts legacy users without loss")
@SpringBootTest(classes = {IdentityServiceApplication.class, IdentityBootTest.Fakes.class})
// A context left cached would keep polling the shared Temporal task queues and take the next class's workflow tasks.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class IdentityBootTest {

    private static final String DATABASE = IdentityStack.newDatabase();

    private static final String PHONE_ONLY = "11111111-1111-4111-8111-111111111111";
    private static final String EMAIL_ONLY = "22222222-2222-4222-8222-222222222222";
    private static final String TWIN_UNVERIFIED = "33333333-3333-4333-8333-333333333333";
    private static final String TWIN_VERIFIED = "44444444-4444-4444-8444-444444444444";
    private static final String PLACEHOLDER = "55555555-5555-4555-8555-555555555555";

    @TestConfiguration
    static class Fakes {
        /** Replaces the real Keycloak admin client behind both account ports. */
        @Bean
        @Primary
        FakeKeycloak fakeKeycloak() {
            return new FakeKeycloak();
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
        // a sender bean, so the mail wiring is exercised without any server being contacted
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "2525");
        registry.add("identity.delivery.email.enabled", () -> "true");
        registry.add("identity.delivery.email.from", () -> "no-reply@myticket.test");
    }

    /** Legacy documents, as the previous release wrote them: no status, no contacts collection. */
    @BeforeAll
    static void seedLegacyUsers() {
        MongoClient client = IdentityStack.mongoClient();
        try {
            MongoCollection<Document> users = client.getDatabase(DATABASE).getCollection("identity_users");
            Date created = Date.from(Instant.parse("2026-01-10T08:00:00Z"));
            List<Document> legacy = List.of(
                    legacyUser(PHONE_ONLY, null, false, "+260971234567", true, created),
                    legacyUser(EMAIL_ONLY, "ada@example.com", true, null, false, created),
                    // looks like one person twice: the same address in two spellings, one verified
                    legacyUser(TWIN_UNVERIFIED, "Jane@Example.com", false, null, false, created),
                    legacyUser(TWIN_VERIFIED, "jane@example.com", true, null, false, created),
                    // the made-up address the phone login used to write
                    legacyUser(PLACEHOLDER, "user_12345678@phone.local", false, "+260977654321", true, created));
            Mono.from(users.insertMany(legacy)).block();
        } finally {
            client.close();
        }
    }

    private static Document legacyUser(String id, String email, boolean emailVerified, String phone,
                                       boolean phoneVerified, Date created) {
        Document user = new Document("_id", id)
                .append("roles", List.of("CUSTOMER"))
                .append("accountStatus", "ACTIVE")
                .append("active", true)
                .append("emailVerified", emailVerified)
                .append("phoneVerified", phoneVerified)
                .append("createdAt", created)
                .append("updatedAt", created);
        if (email != null) {
            user.append("email", email);
        }
        if (phone != null) {
            user.append("phoneNumber", phone);
        }
        return user;
    }

    @Autowired
    ApplicationContext context;
    private WebTestClient client() {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    private WebTestClient as(String scope) {
        return client().mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }

    @Autowired
    AccountPreflightReportMigrationService preflight;
    @Autowired
    CapturedMessages captured;

    private List<Document> all(String collection) {
        MongoClient mongo = IdentityStack.mongoClient();
        try {
            return Flux.from(mongo.getDatabase(DATABASE).getCollection(collection).find()).collectList().block();
        } finally {
            mongo.close();
        }
    }

    // ------------------------------------------------------------------------------------------ wiring

    @Test
    @DisplayName("every bean the sign-in path needs is present, and the fake Keycloak is the one wired")
    void beansWire() {
        assertThat(context.getBeanNamesForType(ProofLookup.class)).as("ProofLookup over Redis").hasSize(1);
        assertThat(context.getBean(ProofLookup.class)).isInstanceOf(ProofService.class);
        assertThat(context.getBeansOfType(Outbox.class)).as("the transactional outbox").isNotEmpty();
        assertThat(context.getBean(JavaMailSender.class)).as("mail").isNotNull();
        assertThat(context.getBean(CapturedMessages.class)).as("delivery capture (profile test)").isNotNull();
        assertThat(context.getBean(InternalChallengeController.class)).isNotNull();
        assertThat(context.getBean(InternalEnsureController.class)).isNotNull();
        assertThat(context.getBean(InternalHandleController.class)).isNotNull();
        assertThat(context.getBean(LoginHandleService.class)).isNotNull();
        assertThat(context.getBean(AccountEnsurer.class)).as("the Temporal facade").isNotNull();
        assertThat(context.getBean(AccountStatusLookup.class)).isNotNull();
        assertThat(context.getBean(KeycloakAccountPort.class)).isInstanceOf(FakeKeycloak.class);
        assertThat(context.getBean(KeycloakUserAdminPort.class)).isInstanceOf(FakeKeycloak.class);
        assertThat(context.getBean(IdentityMigrationRunner.class)).isNotNull();
    }

    @Test
    @DisplayName("the infrastructure the context uses is the containers, not a developer's machine")
    void usesTheContainers() {
        assertThat(context.getEnvironment().getProperty("spring.data.redis.port"))
                .isEqualTo(String.valueOf(RedisNode.port()));
        assertThat(context.getEnvironment().getProperty("spring.temporal.connection.target"))
                .isEqualTo(TemporalDevServer.target());
        assertThat(MongoReplicaSet.connectionString()).isNotBlank();
    }

    @Test
    @DisplayName("a challenge request and its verification run through the controllers, Redis and the capture")
    void challengeAndVerifyThroughTheWiredContext() {
        WebTestClient authed = as("internal-write");
        String email = "boot-" + System.nanoTime() + "@example.com";

        Map<?, ?> issued = authed.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", email), "clientIp", "203.0.113.7"))
                .exchange().expectStatus().isAccepted().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(issued.get("channel")).isEqualTo("EMAIL");
        assertThat(issued.toString()).doesNotContain(email);

        String code = captured.lastCodeTo(email).orElseThrow();
        Map<?, ?> verified = authed.post().uri("/api/internal/auth/challenges/verify").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("challengeId", issued.get("challengeId"), "code", code))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat((String) verified.get("proof")).isNotBlank();
    }

    @Test
    @DisplayName("the internal auth API refuses a caller without the internal-write scope")
    void scopeIsEnforced() {
        as("internal-read")
                .post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", "x@example.com")))
                .exchange().expectStatus().isForbidden();
    }

    // ------------------------------------------------------------------------------------------ migration

    @Test
    @DisplayName("every migration step succeeded, in the ledger, including the four account steps")
    void everyStepSucceeded() {
        Map<String, String> status = all("identity_migrations").stream()
                .collect(Collectors.toMap(d -> d.getString("_id"), d -> d.getString("status")));
        for (String step : List.of("users-legacy-indexes-drop", "account-preflight-report",
                "users-account-fields-backfill", "contacts-backfill", "contacts-unique-index")) {
            assertThat(status).as("ledger rows %s", status.keySet()).containsEntry(step, "SUCCEEDED");
        }
        assertThat(status.values()).as("no step failed or is left running").containsOnly("SUCCEEDED");
    }

    @Test
    @DisplayName("the preflight report counts what the seeded database holds")
    void preflightReport() {
        AccountPreflightReportMigrationService.Report report = preflight.migrate().block();
        assertThat(report.users()).isEqualTo(5);
        assertThat(report.placeholderEmails()).isEqualTo(1);
        assertThat(report.caseCollidingEmailGroups()).as("Jane@Example.com / jane@example.com").isEqualTo(1);
        assertThat(report.samePersonOnSeveralAccounts()).isGreaterThanOrEqualTo(1);
        assertThat(report.orphans()).as("every seeded id is a UUID").isZero();
        // the same counts are what the ledger recorded when the step ran
        String summary = all("identity_migrations").stream()
                .filter(d -> "account-preflight-report".equals(d.getString("_id")))
                .findFirst().orElseThrow().getString("summary");
        assertThat(summary).contains("users=5").contains("placeholderEmails=1").contains("caseCollidingEmailGroups=1");
    }

    @Test
    @DisplayName("contacts were backfilled: right type, verified only when the account verified it, no plain value stored")
    void contactsBackfilled() {
        List<Document> contacts = all("identity_contacts");
        // phone-only 1, email-only 1, twins 2, placeholder account's phone 1; the made-up address is skipped
        assertThat(contacts).hasSize(5);

        Map<String, List<Document>> byAccount = contacts.stream()
                .collect(Collectors.groupingBy(d -> d.getString("accountId")));
        assertThat(byAccount.get(PHONE_ONLY)).singleElement().satisfies(c -> {
            assertThat(c.getString("type")).isEqualTo("WHATSAPP");
            assertThat(c.get("verifiedAt")).isNotNull();
            assertThat(c.getBoolean("primary")).isTrue();
        });
        assertThat(byAccount.get(EMAIL_ONLY)).singleElement().satisfies(c -> {
            assertThat(c.getString("type")).isEqualTo("EMAIL");
            assertThat(c.get("verifiedAt")).isNotNull();
        });
        assertThat(byAccount.get(TWIN_UNVERIFIED)).singleElement().satisfies(c -> assertThat(c.get("verifiedAt")).isNull());
        assertThat(byAccount.get(TWIN_VERIFIED)).singleElement().satisfies(c -> assertThat(c.get("verifiedAt")).isNotNull());
        assertThat(byAccount.get(TWIN_UNVERIFIED).get(0).getString("valueHash"))
                .as("both spellings normalise to ONE contact key").isEqualTo(byAccount.get(TWIN_VERIFIED).get(0).getString("valueHash"));
        assertThat(byAccount.get(PLACEHOLDER)).singleElement().satisfies(c -> assertThat(c.getString("type")).isEqualTo("WHATSAPP"));

        String dump = contacts.toString();
        assertThat(contacts).allSatisfy(c -> assertThat(c.getString("valueEncrypted")).isNotBlank());
        assertThat(contacts.stream().map(Document::toJson).collect(Collectors.joining()))
                .doesNotContain("+260971234567", "ada@example.com", "jane@example.com", "+260977654321");
        assertThat(dump).isNotBlank();
    }

    @Test
    @DisplayName("the unique verified-contact index exists, and it admits the unverified twin")
    void uniqueIndexBuilt() {
        MongoClient mongo = IdentityStack.mongoClient();
        try {
            List<Document> indexes = Flux.from(mongo.getDatabase(DATABASE).getCollection("identity_contacts").listIndexes())
                    .collectList().block();
            Document unique = indexes.stream().filter(i -> "uniq_verified_contact".equals(i.getString("name")))
                    .findFirst().orElseThrow(() -> new AssertionError("uniq_verified_contact missing from " + indexes));
            assertThat(unique.getBoolean("unique")).isTrue();
            assertThat(unique.get("partialFilterExpression")).isNotNull();
        } finally {
            mongo.close();
        }
    }

    @Test
    @DisplayName("no account was lost or rewritten beyond the new fields: ids, emails, phones and active flags survive")
    void noDataLoss() {
        Map<String, Document> users = all("identity_users").stream()
                .collect(Collectors.toMap(d -> d.getString("_id"), d -> d));
        assertThat(users).containsOnlyKeys(PHONE_ONLY, EMAIL_ONLY, TWIN_UNVERIFIED, TWIN_VERIFIED, PLACEHOLDER);

        assertThat(users.get(PHONE_ONLY).getString("phoneNumber")).isEqualTo("+260971234567");
        assertThat(users.get(EMAIL_ONLY).getString("email")).isEqualTo("ada@example.com");
        assertThat(users.get(TWIN_UNVERIFIED).getString("email")).isEqualTo("Jane@Example.com");
        assertThat(users.get(TWIN_VERIFIED).getString("email")).isEqualTo("jane@example.com");
        assertThat(users.get(PLACEHOLDER).getString("email")).isEqualTo("user_12345678@phone.local");
        assertThat(users.values()).allSatisfy(user -> {
            assertThat(user.getBoolean("active")).isTrue();
            assertThat(user.getString("status")).as("backfilled lifecycle status").isEqualTo("ACTIVE");
            assertThat(user.getString("keycloakUserId")).as("legacy accounts keep _id = Keycloak id").isEqualTo(user.getString("_id"));
            assertThat(user.get("provisionedAt")).isNotNull();
        });
        assertThat(users.get(PHONE_ONLY).getString("primaryContactId")).isNotBlank();
        assertThat(users.get(TWIN_UNVERIFIED).getString("primaryContactId")).as("nothing verified, so no primary").isNull();
    }

    @Test
    @DisplayName("running the migrations again changes nothing")
    void migrationsAreIdempotent() {
        long before = all("identity_contacts").size();
        context.getBean(IdentityMigrationRunner.class).runMigrations();
        assertThat(all("identity_contacts")).hasSize((int) before);
    }
}
