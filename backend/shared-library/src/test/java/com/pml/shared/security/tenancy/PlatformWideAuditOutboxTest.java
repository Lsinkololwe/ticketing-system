package com.pml.shared.security.tenancy;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.config.MongoSchemaValidationConfig;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The service's local copy of a platform-wide reach: written before anything leaves the service,
 * holding the facts and the delivery state and nothing else, and refusing every other property at
 * the database. OWASP A04:2021 · A09:2021.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@Tag("OWASP")
@DisplayName("a platform-wide reach is kept in the service's own outbox as its facts and its delivery state, nothing else")
class PlatformWideAuditOutboxTest {

    private static final String COLLECTION = "test_platform_wide_audit_outbox";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private PlatformWideAuditOutbox outbox;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client,
                "outbox_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        // the validator the service applies at start-up, through the shared auto-configuration's runner
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties()) {
            @Override
            protected Map<String, String> getSchemaDefinitions() {
                return Map.of(COLLECTION, "platform-wide-audit-outbox-schema.json");
            }
        }.applySchemaValidation();
        new IndexEnsurer(template).ensure(PlatformWideAuditOutbox.indexes(COLLECTION)).block();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), COLLECTION).block();
        outbox = new PlatformWideAuditOutbox(template, COLLECTION, TestClock.frozenAt(NOW));
    }

    private static PlatformWideAuditRecord reach(Instant at) {
        return new PlatformWideAuditRecord("finance-1", "ROLE_FINANCE", "booking-service",
                PlatformWideAccess.Reason.BUYER_BOOKINGS_READ, "bookingsByBuyer", at);
    }

    private Document stored(String recordId) {
        return template.findById(recordId, Document.class, COLLECTION).block();
    }

    @Test
    @DisplayName("appending stores the facts, the time it happened and PENDING, under a fresh permanent id")
    void appendStoresTheFacts() {
        String first = outbox.append(reach(NOW.minusSeconds(5))).block();
        String second = outbox.append(reach(NOW.minusSeconds(5))).block();

        assertThat(first).isNotEqualTo(second).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        Document row = stored(first);
        assertThat(row.getString("actorSub")).isEqualTo("finance-1");
        assertThat(row.getString("role")).isEqualTo("ROLE_FINANCE");
        assertThat(row.getString("service")).isEqualTo("booking-service");
        assertThat(row.getString("reason")).isEqualTo("BUYER_BOOKINGS_READ");
        assertThat(row.getString("operation")).isEqualTo("bookingsByBuyer");
        assertThat(row.getDate("occurredAt").toInstant()).isEqualTo(NOW.minusSeconds(5));
        assertThat(row.getString("state")).isEqualTo(PlatformWideAuditOutbox.PENDING);
        assertThat(row.getInteger("attempts")).isZero();
    }

    @Test
    @DisplayName("a stored row has no property beyond the facts of a reach and its delivery state")
    void rowShapeIsClosed() {
        String id = outbox.append(reach(NOW)).block();
        outbox.noteAttempt(id).block();
        outbox.markDelivered(id).block();

        Set<String> allowed = Set.of("_id", "actorSub", "role", "service", "reason", "operation", "occurredAt",
                "state", "attempts", "createdAt", "lastAttemptAt", "deliveredAt", "refusedAt");
        assertThat(stored(id).keySet()).isSubsetOf(allowed);
    }

    @Test
    @DisplayName("the record handed to the outbox has no field that could carry a request, a token or free text")
    void recordShapeCarriesNoRequestData() {
        Set<String> components = Stream.of(PlatformWideAuditRecord.class.getRecordComponents())
                .map(c -> c.getName()).collect(Collectors.toSet());

        assertThat(components).containsExactlyInAnyOrder("actorSub", "role", "service", "reason", "operation", "at");
        assertThat(Stream.of(PlatformWideAuditRecord.class.getRecordComponents()).map(c -> c.getType().getSimpleName()))
                .as("only strings, the declared reason and an instant").containsOnly("String", "Reason", "Instant");
    }

    @Test
    @DisplayName("the database refuses a row that carries any other property, such as a request body or a token")
    void validatorRefusesExtraProperties() {
        for (String smuggled : new String[]{"requestBody", "token", "filter", "authorization", "note"}) {
            Document row = new Document("_id", UUID.randomUUID().toString()).append("actorSub", "a")
                    .append("role", "ROLE_ADMIN").append("service", "booking-service")
                    .append("reason", "BUYER_BOOKINGS_READ").append("occurredAt", java.util.Date.from(NOW))
                    .append("state", "PENDING").append("attempts", 0).append("createdAt", java.util.Date.from(NOW))
                    .append(smuggled, "x");
            assertThatThrownBy(() -> template.insert(row, COLLECTION).block()).as(smuggled)
                    .hasMessageContaining("validation");
        }
    }

    @Test
    @DisplayName("the database refuses an unknown state and an identifier that is not a UUID")
    void validatorRefusesBadStateAndId() {
        Document base = new Document("actorSub", "a").append("role", "ROLE_ADMIN").append("service", "booking-service")
                .append("reason", "BUYER_BOOKINGS_READ").append("occurredAt", java.util.Date.from(NOW))
                .append("attempts", 0).append("createdAt", java.util.Date.from(NOW));

        Document wrongState = new Document(base).append("_id", UUID.randomUUID().toString()).append("state", "DONE");
        Document wrongId = new Document(base).append("_id", "not-a-uuid").append("state", "PENDING");

        assertThatThrownBy(() -> template.insert(wrongState, COLLECTION).block()).hasMessageContaining("validation");
        assertThatThrownBy(() -> template.insert(wrongId, COLLECTION).block()).hasMessageContaining("validation");
    }

    @Test
    @DisplayName("delivered and refused rows stay put and are never moved by a later settle")
    void settlingIsOnceOnly() {
        String delivered = outbox.append(reach(NOW)).block();
        String refused = outbox.append(reach(NOW)).block();

        outbox.markDelivered(delivered).block();
        outbox.markRefused(delivered).block();
        outbox.markRefused(refused).block();
        outbox.markDelivered(refused).block();
        outbox.noteAttempt(delivered).block();

        assertThat(stored(delivered).getString("state")).isEqualTo(PlatformWideAuditOutbox.DELIVERED);
        assertThat(stored(delivered).getInteger("attempts")).isZero();
        assertThat(stored(refused).getString("state")).isEqualTo(PlatformWideAuditOutbox.REFUSED);
        assertThat(outbox.find(delivered).block().state()).isEqualTo(PlatformWideAuditOutbox.DELIVERED);
    }

    @Test
    @DisplayName("the backlog counts what still waits and names the oldest reach, ignoring settled rows")
    void backlog() {
        assertThat(outbox.backlog().block()).isEqualTo(PlatformWideAuditOutbox.Backlog.EMPTY);

        String old = outbox.append(reach(NOW.minusSeconds(600))).block();
        outbox.append(reach(NOW.minusSeconds(60))).block();
        String settled = outbox.append(reach(NOW.minusSeconds(6000))).block();
        outbox.markDelivered(settled).block();

        PlatformWideAuditOutbox.Backlog waiting = outbox.backlog().block();
        assertThat(waiting.pending()).isEqualTo(2);
        assertThat(waiting.oldest()).isEqualTo(NOW.minusSeconds(600));
        assertThat(outbox.pendingIds(10).collectList().block()).hasSize(2).startsWith(old);
    }

    @Test
    @DisplayName("the re-drive reads at most the limit it is given, oldest first")
    void pendingIsBounded() {
        for (int i = 0; i < 5; i++) {
            outbox.append(reach(NOW.minusSeconds(100 - i))).block();
        }
        assertThat(outbox.pendingIds(3).collectList().block()).hasSize(3);
    }

    @Test
    @DisplayName("an unknown row answers empty rather than failing")
    void unknownRow() {
        assertThat(outbox.find(UUID.randomUUID().toString()).block()).isNull();
    }
}
