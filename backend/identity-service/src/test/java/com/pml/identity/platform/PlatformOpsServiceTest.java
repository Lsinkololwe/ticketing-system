package com.pml.identity.platform;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.config.IdentityIndexInitializer;
import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.enums.AlertStatus;
import com.pml.identity.domain.enums.AnnouncementSegment;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.SystemAlert;
import com.pml.identity.domain.model.SystemAnnouncement;
import com.pml.identity.service.AdminAuditService;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
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
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Alerts and announcements against a real replica set, with the registry's real indexes. */
@Tag("L5")
@Tag("ET-ADM-005")
@DisplayName("ET-ADM-005-R9 · alerts are one per condition even under contention; announcements follow their window and segment")
class PlatformOpsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static PlatformOpsService ops;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_platform_ops"));
        template.getMongoDatabase().flatMap(db -> reactor.core.publisher.Mono.from(db.drop())).block();
        new IndexEnsurer(template).ensure(IdentityIndexInitializer.specifications()).block();
        TestClock clock = TestClock.frozenAt(NOW);
        ops = new PlatformOpsService(template, new AdminAuditService(template, clock), clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), SystemAlert.class).block();
        template.remove(new Query(), SystemAnnouncement.class).block();
        template.remove(new Query(), AuditLog.class).block();
    }

    @Test
    @DisplayName("the same condition raised again is counted, not listed twice")
    void raisingTwiceCounts() {
        ops.raise("health-probe", "service-down:catalog", AlertSeverity.CRITICAL, "catalog is down", "timeout").block();
        SystemAlert second = ops.raise("health-probe", "service-down:catalog", AlertSeverity.CRITICAL, "catalog is down", "timeout").block();

        assertThat(second.getOccurrences()).isEqualTo(2);
        assertThat(template.count(new Query(), SystemAlert.class).block()).isEqualTo(1);
    }

    @Test
    @DisplayName("forty callers raising one condition at once leave one alert counting all forty")
    void concurrentRaisers() {
        Concurrency.repeat(3, () -> {
            template.remove(new Query(), SystemAlert.class).block();
            var outcome = Concurrency.inParallel(40, caller ->
                    ops.raise("booking-service", "queue-backlog", AlertSeverity.WARNING, "backlog", "deep").block());

            assertThat(outcome.failures()).isEmpty();
            assertThat(template.count(new Query(), SystemAlert.class).block()).isEqualTo(1);
            assertThat(template.findOne(new Query(), SystemAlert.class).block().getOccurrences()).isEqualTo(40);
        });
    }

    @Test
    @DisplayName("a resolved condition that recurs opens a fresh alert")
    void recurrenceAfterResolution() {
        ops.raise("health-probe", "service-down:redis", AlertSeverity.CRITICAL, "redis is down", null).block();
        ops.resolve("health-probe", "service-down:redis").block();
        SystemAlert again = ops.raise("health-probe", "service-down:redis", AlertSeverity.CRITICAL, "redis is down", null).block();

        assertThat(again.getOccurrences()).isEqualTo(1);
        assertThat(template.count(new Query(), SystemAlert.class).block()).isEqualTo(2);
        assertThat(ops.alerts(AlertStatus.RESOLVED, null).collectList().block()).hasSize(1);
        assertThat(ops.alerts(AlertStatus.OPEN, AlertSeverity.CRITICAL).collectList().block()).hasSize(1);
    }

    @Test
    @DisplayName("acknowledging records who and when, keeps the first acknowledgement and is audited once")
    void acknowledge() {
        SystemAlert alert = ops.raise("health-probe", "k", AlertSeverity.WARNING, "t", null).block();

        SystemAlert first = ops.acknowledge(alert.getId(), "admin-1").block();
        SystemAlert second = ops.acknowledge(alert.getId(), "admin-2").block();

        assertThat(first.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(second.getAcknowledgedBy()).isEqualTo("admin-1");
        assertThat(template.count(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("action")
                .is(AuditLog.AuditAction.SYSTEM_ALERT_ACKNOWLEDGED)), AuditLog.class).block()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown alert is refused")
    void unknownAlert() {
        assertThatThrownBy(() -> ops.acknowledge("nope", "admin-1").block()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("a caller sees only live announcements for their segments: not scheduled, ended or cancelled ones")
    void announcementsFollowWindowAndSegment() {
        ops.broadcast("Everyone now", "m", AnnouncementSegment.ALL, null, null, null, "admin-1").block();
        ops.broadcast("Organizers now", "m", AnnouncementSegment.ORGANIZERS, AlertSeverity.WARNING, null, null, "admin-1").block();
        ops.broadcast("Scheduled", "m", AnnouncementSegment.ALL, null, NOW.plusSeconds(3600), null, "admin-1").block();
        ops.broadcast("Staff only", "m", AnnouncementSegment.STAFF, null, null, null, "admin-1").block();
        SystemAnnouncement withdrawn = ops.broadcast("Withdrawn", "m", AnnouncementSegment.ALL, null, null, null, "admin-1").block();
        ops.cancel(withdrawn.getId(), "admin-1").block();

        var buyer = ops.activeFor(AnnouncementRules.segmentsOf(false, false)).collectList().block();
        var organizer = ops.activeFor(AnnouncementRules.segmentsOf(true, false)).collectList().block();
        var staff = ops.activeFor(AnnouncementRules.segmentsOf(false, true)).collectList().block();

        assertThat(buyer).extracting(SystemAnnouncement::getTitle).containsExactly("Everyone now");
        assertThat(organizer).extracting(SystemAnnouncement::getTitle).containsExactlyInAnyOrder("Everyone now", "Organizers now");
        assertThat(staff).extracting(SystemAnnouncement::getTitle).containsExactlyInAnyOrder("Everyone now", "Staff only");
        assertThat(ops.all().collectList().block()).hasSize(5);
        assertThat(EnumSet.allOf(AnnouncementSegment.class)).hasSize(4);
    }

    @Test
    @DisplayName("an invalid announcement is refused before it is stored, and a publication is audited")
    void validationAndAudit() {
        assertThatThrownBy(() -> ops.broadcast(" ", "m", null, null, null, null, "admin-1").block()).isInstanceOf(RuntimeException.class);
        assertThat(template.count(new Query(), SystemAnnouncement.class).block()).isZero();

        ops.broadcast("Hello", "m", null, null, null, null, "admin-1").block();
        assertThat(template.count(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("action")
                .is(AuditLog.AuditAction.ANNOUNCEMENT_PUBLISHED)), AuditLog.class).block()).isEqualTo(1);
    }
}
