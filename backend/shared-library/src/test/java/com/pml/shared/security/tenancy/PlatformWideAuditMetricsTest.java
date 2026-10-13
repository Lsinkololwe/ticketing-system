package com.pml.shared.security.tenancy;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** What an operator sees: how many reaches wait and for how long. */
@Tag("L2")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@DisplayName("the outbox gauges report how many reaches wait and how old the oldest is, and fall back to zero")
class PlatformWideAuditMetricsTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client,
                "metrics_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("pending and oldest-age follow the backlog as it is read in the background")
    void gaugesFollowTheBacklog() {
        Instant now = Instant.parse("2026-10-13T09:00:00Z");
        // each reading is more than the refresh interval after the last, so each asks the database again
        var time = new java.util.concurrent.atomic.AtomicReference<>(now);
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return time.get();
            }
        };
        var outbox = new PlatformWideAuditOutbox(template, "test_metrics_outbox", clock);
        var meters = new SimpleMeterRegistry();
        new PlatformWideAuditMetrics("booking-service", outbox, meters, clock);
        var pending = meters.get("platform.wide.audit.outbox.pending").tag("service", "booking-service").gauge();
        var age = meters.get("platform.wide.audit.outbox.oldest.age.seconds").tag("service", "booking-service").gauge();

        assertThat(pending.value()).as("nothing waits yet").isZero();
        assertThat(age.value()).isZero();

        String id = outbox.append(new PlatformWideAuditRecord("a", "ROLE_ADMIN", "booking-service",
                PlatformWideAccess.Reason.BUYER_BOOKINGS_READ, null, now.minusSeconds(120))).block();
        time.set(now.plusSeconds(10));
        pending.value();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(pending.value()).isEqualTo(1);
            assertThat(age.value()).isEqualTo(130);
        });

        outbox.markDelivered(id).block();
        time.set(now.plusSeconds(30));
        pending.value();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(pending.value()).isZero();
            assertThat(age.value()).isZero();
        });
    }
}
