package com.pml.identity.platform;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.account.AccountService;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.AdminReadService;
import com.pml.identity.service.GrowthBuckets.Bucket;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.SortDirection;
import com.pml.identity.web.graphql.dto.platform.AuditLogFilterInput;
import com.pml.identity.web.graphql.dto.platform.GrowthPoint;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.testing.MongoReplicaSet;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The audit trail, staff list and growth series against a real MongoDB. */
@Tag("L2")
@Tag("ET-PLT-009")
@Tag("ET-ADM-004")
@DisplayName("ET-PLT-009-R9 / ET-ADM-004-R9 · the audit query, staff accounts and the growth series")
class AdminReadServiceTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static AdminReadService reads;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_admin_reads"));
        reads = new AdminReadService(template);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), AuditLog.class).block();
        template.remove(new Query(), AccountEvent.class).block();
        template.remove(new Query(), User.class).block();
    }

    private static AuditLog audit(AuditLog.AuditAction action, String actor, String resourceId, String at) {
        AuditLog row = AuditLog.success(action, null, actor, Instant.parse(at));
        row.setResourceType("Organization");
        row.setResourceId(resourceId);
        row.setMetadata(Map.of("detail", "x"));
        return row;
    }

    @Test
    @DisplayName("the trail is newest first, paged, and filtered by actor, action, resource and time")
    void auditFilters() {
        template.insertAll(List.of(
                audit(AuditLog.AuditAction.ORGANIZATION_SUSPENDED, "admin-1", "org-1", "2026-10-01T10:00:00Z"),
                audit(AuditLog.AuditAction.ORGANIZATION_COMMISSION_CHANGED, "admin-2", "org-1", "2026-10-02T10:00:00Z"),
                audit(AuditLog.AuditAction.PAYOUT_ACCOUNT_REJECTED, "admin-1", "org-2", "2026-10-03T10:00:00Z"))).collectList().block();

        var page = reads.auditLogs(null, new OffsetPaginationInput(0, 2, "timestamp", SortDirection.DESC)).block();
        assertThat(page.content()).extracting(e -> e.action())
                .containsExactly("PAYOUT_ACCOUNT_REJECTED", "ORGANIZATION_COMMISSION_CHANGED");
        assertThat(page.pageInfo().totalElements()).isEqualTo(3);
        assertThat(page.pageInfo().hasNextPage()).isTrue();

        assertThat(reads.auditLogs(new AuditLogFilterInput(null, null, null, "admin-1", null, null, null, null), null).block().content())
                .extracting(e -> e.resourceId()).containsExactly("org-2", "org-1");
        assertThat(reads.auditLogs(new AuditLogFilterInput(null, null, null, null, "Organization", "org-1", null, null), null).block().content())
                .hasSize(2);
        assertThat(reads.auditLogs(new AuditLogFilterInput(Instant.parse("2026-10-02T00:00:00Z"),
                Instant.parse("2026-10-03T00:00:00Z"), null, null, null, null, null, null), null).block().content())
                .extracting(e -> e.action()).containsExactly("ORGANIZATION_COMMISSION_CHANGED");
        assertThat(reads.auditLogs(new AuditLogFilterInput(null, null, "ORGANIZATION_SUSPENDED", null, null, null, "SUCCESS", null), null)
                .block().content()).hasSize(1);
    }

    @Test
    @DisplayName("account events appear only when asked for, without repeating what the trail already carries")
    void accountEventsOptIn() {
        template.insert(audit(AuditLog.AuditAction.USER_SUSPENDED, "admin-1", "u-1", "2026-10-01T10:00:00Z")).block();
        template.insert(AccountEvent.builder().id("e-1").accountId("u-1").kind("SUSPENDED")
                .at(Instant.parse("2026-10-01T10:00:00Z")).data(Map.of("by", "admin-1")).build()).block();
        template.insert(AccountEvent.builder().id("e-2").accountId("u-1").kind("OTP_LOCKED")
                .at(Instant.parse("2026-10-02T10:00:00Z")).data(Map.of("attempts", 5, "nested", Map.of("a", "b"))).build()).block();

        assertThat(reads.auditLogs(null, null).block().content()).hasSize(1);

        var merged = reads.auditLogs(new AuditLogFilterInput(null, null, null, null, null, null, null, true), null).block();
        assertThat(merged.content()).extracting(e -> e.source() + ":" + e.action())
                .containsExactly("ACCOUNT_EVENT:OTP_LOCKED", "AUDIT:USER_SUSPENDED");
        assertThat(merged.content().get(0).metadata()).containsEntry("attempts", 5).doesNotContainKey("nested");
    }

    @Test
    @DisplayName("staff are the staff-realm accounts only, filtered by name and role")
    void staff() {
        template.insertAll(List.of(
                User.builder().id("s-1").firstName("Natasha").lastName("Mulenga").email("natasha@staff.example")
                        .createdVia(AccountService.STAFF_ADMIN).roles(EnumSet.of(UserType.CUSTOMER, UserType.ADMIN)).build(),
                User.builder().id("s-2").firstName("Brian").lastName("Phiri").email("brian@staff.example")
                        .createdVia(AccountService.STAFF_SYNC).roles(EnumSet.of(UserType.CUSTOMER, UserType.FINANCE)).build(),
                User.builder().id("b-1").firstName("Natasha").lastName("Buyer").createdVia("OTP").build())).collectList().block();

        assertThat(reads.staff(null, null).block()).extracting(User::getId).containsExactly("s-1", "s-2");
        assertThat(reads.staff("natasha", null).block()).extracting(User::getId).containsExactly("s-1");
        assertThat(reads.staff(null, UserType.FINANCE).block()).extracting(User::getId).containsExactly("s-2");
    }

    @Test
    @DisplayName("growth counts joiners per local day, fills empty days, carries the earlier total and can be filtered by role")
    void growth() {
        template.insertAll(List.of(
                User.builder().id("g-0").createdAt(Instant.parse("2026-09-01T10:00:00Z")).roles(EnumSet.of(UserType.CUSTOMER)).build(),
                User.builder().id("g-1").createdAt(Instant.parse("2026-10-01T10:00:00Z")).roles(EnumSet.of(UserType.CUSTOMER)).build(),
                User.builder().id("g-2").createdAt(Instant.parse("2026-10-01T11:00:00Z")).roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER)).build(),
                // 23:30 UTC on 2 Oct is already 3 Oct in Lusaka
                User.builder().id("g-3").createdAt(Instant.parse("2026-10-02T23:30:00Z")).roles(EnumSet.of(UserType.CUSTOMER)).build())).collectList().block();

        List<GrowthPoint> series = reads.userGrowth(Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-04T00:00:00Z"), Bucket.DAY, null).block();

        assertThat(series).extracting(GrowthPoint::newUsers).containsExactly(2, 0, 1, 0);
        assertThat(series).extracting(GrowthPoint::cumulative).containsExactly(3L, 3L, 4L, 4L);

        List<GrowthPoint> organizers = reads.userGrowth(Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-04T00:00:00Z"), Bucket.DAY, UserType.ORGANIZER).block();
        assertThat(organizers).extracting(GrowthPoint::newUsers).containsExactly(1, 0, 0, 0);
    }

    @Test
    @DisplayName("a backwards or enormous range is refused")
    void growthRange() {
        Instant now = Instant.parse("2026-10-04T00:00:00Z");
        assertThatThrownBy(() -> reads.userGrowth(now, now.minusSeconds(1), Bucket.DAY, null).block())
                .isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> reads.userGrowth(now.minusSeconds(86400L * 4000), now, Bucket.DAY, null).block())
                .isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> reads.userGrowth(null, now, Bucket.DAY, null).block()).isInstanceOf(DomainRefusal.class);
    }
}
