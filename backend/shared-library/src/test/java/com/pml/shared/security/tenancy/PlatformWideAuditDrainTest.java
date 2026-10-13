package com.pml.shared.security.tenancy;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one-time re-drive at start-up: rows still waiting are started again, settled rows are not,
 * and an unreachable Temporal ends the pass without failing the service.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@DisplayName("at start-up every waiting reach is started again once, and a settled one never is")
class PlatformWideAuditDrainTest {

    private static final String COLLECTION = "test_drain_outbox";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private PlatformWideAuditOutbox outbox;
    private PlatformWideAuditMetrics metrics;
    private SimpleMeterRegistry meters;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client,
                "drain_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), COLLECTION).block();
        var clock = TestClock.frozenAt(NOW);
        outbox = new PlatformWideAuditOutbox(template, COLLECTION, clock);
        meters = new SimpleMeterRegistry();
        metrics = new PlatformWideAuditMetrics("catalog-service", outbox, meters, clock);
    }

    private String reach(int secondsAgo) {
        return outbox.append(new PlatformWideAuditRecord("admin-1", "ROLE_ADMIN", "catalog-service",
                PlatformWideAccess.Reason.EVENT_ACCESS_GRANTS_READ, null, NOW.minusSeconds(secondsAgo))).block();
    }

    @Test
    @DisplayName("rows that are still PENDING are started, oldest first; delivered and refused rows are left alone")
    void startsWhatIsWaiting() {
        String newest = reach(10);
        String oldest = reach(300);
        String delivered = reach(100);
        String refused = reach(200);
        outbox.markDelivered(delivered).block();
        outbox.markRefused(refused).block();
        List<String> started = new ArrayList<>();

        Long count = new PlatformWideAuditDrain(outbox, started::add, metrics).redrive().block();

        assertThat(count).isEqualTo(2);
        assertThat(started).containsExactly(oldest, newest);
    }

    @Test
    @DisplayName("starting a row that already has a delivery running is harmless: it is only a start request")
    void restartIsRepeatable() {
        String id = reach(10);
        List<String> started = new ArrayList<>();
        var drain = new PlatformWideAuditDrain(outbox, started::add, metrics);

        drain.redrive().block();
        drain.redrive().block();

        assertThat(started).containsExactly(id, id);
    }

    @Test
    @DisplayName("an unreachable Temporal ends the pass at the first failure, counts it, and the rows stay PENDING")
    void unreachableTemporalEndsThePass() {
        reach(30);
        reach(20);
        reach(10);
        List<String> attempted = new ArrayList<>();

        Long count = new PlatformWideAuditDrain(outbox, recordId -> {
            attempted.add(recordId);
            throw new IllegalStateException("temporal unreachable");
        }, metrics).redrive().block();

        assertThat(count).isZero();
        assertThat(attempted).as("the pass stopped at the first failure").hasSize(1);
        assertThat(outbox.backlog().block().pending()).isEqualTo(3);
        assertThat(meters.find("platform.wide.audit.delivery.start.failures").counter().count()).isEqualTo(1.0);
    }
}
