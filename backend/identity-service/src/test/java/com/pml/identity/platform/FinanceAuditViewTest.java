package com.pml.identity.platform;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.config.MongoSchemaValidationConfig;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.service.AdminReadService;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.SortDirection;
import com.pml.identity.web.graphql.dto.platform.AuditLogEntry;
import com.pml.identity.web.graphql.dto.platform.AuditLogFilterInput;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAccess.Reason;
import com.pml.shared.security.tenancy.StaffRole;
import com.pml.shared.testing.MongoReplicaSet;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The finance officer's audit view: money reasons by any actor, nothing else, enforced in the query
 * and not by the filter the client sends. Against a real MongoDB with the production validators.
 * OWASP A01:2021 · A09:2021.
 */
@Tag("L2")
@Tag("ET-PLT-009")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-009 · finance reads the money view of the audit trail and nothing outside it")
class FinanceAuditViewTest {

    private static final Instant NOW = Instant.parse("2026-10-13T08:30:00Z");
    private static final Set<String> AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    /** Reasons deliberately left out of the money view; see {@link FinanceAuditView}. */
    private static final Set<Reason> NOT_MONEY = EnumSet.of(
            Reason.AUDIT_TRAIL_READ, Reason.FINANCE_AUDIT_VIEW_READ, Reason.BOOKING_CONTACT_READ,
            Reason.EVENT_ACCESS_GRANTS_READ, Reason.USER_EVENT_GRANT_READ, Reason.ORGANIZATION_MEMBER_READ,
            Reason.ORGANIZATION_TEAM_READ, Reason.OWNERSHIP_TRANSFER_READ, Reason.CALLER_SCOPED_QUERY,
            Reason.STAFF_PERMISSION_GRANT_READ, Reason.PROMO_CODE_MANAGE, Reason.PLATFORM_PERMISSION_EXERCISED,
            // system reaches are a log line, never an audit row
            Reason.LIFECYCLE_WORKFLOW, Reason.EVENT_COMPLETION, Reason.FINANCE_WORKFLOW, Reason.REFUND_WORKFLOW);

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static AdminReadService reads;
    private static PlatformWideAccess access;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_finance_audit_view"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();
        reads = new AdminReadService(template);
        access = new PlatformWideAccess("booking-service", new PlatformWideAuditTrail(template, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC), AUTHORITIES);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void empty() {
        template.remove(new Query(), IdentityCollections.AUDIT_LOGS).block();
        template.remove(new Query(), IdentityCollections.ACCOUNT_EVENTS).block();
    }

    /** One reach, as the sink receives it, by {@code actor} in {@code role} for {@code reason}. */
    private static void reach(String actor, String role, Reason reason) {
        PlatformWideAccess.recordStaffAct(reason, "op-" + reason.name().toLowerCase())
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(
                        actor, "n/a", List.of(new SimpleGrantedAuthority(role)))))
                .block();
    }

    private static void seedEveryReason() {
        for (Reason reason : Reason.values()) {
            if (!reason.isSystem()) {
                reach("admin-" + reason.name(), "ROLE_ADMIN", reason);
            }
        }
        reach("finance-own", "ROLE_FINANCE", Reason.BANK_ACCOUNTS_READ);
        reach("root-1", "ROLE_SUPER_ADMIN", Reason.RECOVERY_ACTION_CONFIRM);
    }

    private static AuditLogFilterInput filter(String action, String actor, String service, String reason) {
        return new AuditLogFilterInput(null, null, action, actor, null, null, null, null, service, reason);
    }

    private static List<String> reasonsOf(List<AuditLogEntry> rows) {
        return rows.stream().map(row -> (String) row.metadata().get("reason")).toList();
    }

    @Test
    @DisplayName("every reason is decided one way or the other, so a new reason cannot slip into or out of the money view unseen")
    void everyReasonIsClassified() {
        Set<Reason> all = EnumSet.allOf(Reason.class);
        Set<Reason> decided = EnumSet.copyOf(FinanceAuditView.MONEY_REASONS);
        decided.addAll(NOT_MONEY);

        assertThat(decided).isEqualTo(all);
        assertThat(FinanceAuditView.MONEY_REASONS).doesNotContainAnyElementsOf(NOT_MONEY);
        assertThat(FinanceAuditView.MONEY_REASONS).noneMatch(Reason::isSystem);
        assertThat(FinanceAuditView.MONEY_REASON_NAMES).hasSameSizeAs(FinanceAuditView.MONEY_REASONS);
    }

    @Test
    @DisplayName("finance sees the money reasons from any actor, and no row outside the set")
    void financeSeesMoneyFromAnyActor() {
        seedEveryReason();

        List<AuditLogEntry> seen = reads.financeAuditLogs(null, new OffsetPaginationInput(0, 100, null, SortDirection.DESC))
                .block().content();

        // one row per money reason by an administrator, plus finance's own and a super administrator's
        List<String> expected = new java.util.ArrayList<>(FinanceAuditView.MONEY_REASON_NAMES);
        expected.add("BANK_ACCOUNTS_READ");
        expected.add("RECOVERY_ACTION_CONFIRM");
        assertThat(reasonsOf(seen)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(seen).extracting(AuditLogEntry::actorId).contains("admin-BANK_ACCOUNT_MANAGE", "root-1", "finance-own");
    }

    @Test
    @DisplayName("asking for a row outside the set by reason, actor, service or action returns nothing, not the row")
    void filtersCannotWidenTheView() {
        seedEveryReason();

        for (Reason outside : NOT_MONEY) {
            if (outside.isSystem()) {
                continue;
            }
            assertThat(reads.financeAuditLogs(filter(null, null, null, outside.name()), null).block().content())
                    .as("reason %s", outside).isEmpty();
            assertThat(reads.financeAuditLogs(filter(null, "admin-" + outside.name(), null, null), null).block().content())
                    .as("actor of %s", outside).isEmpty();
        }
        assertThat(reads.auditLogs(filter(null, "admin-AUDIT_TRAIL_READ", null, null), null).block().content())
                .as("the administrator's own view still has it").hasSize(1);
        assertThat(reads.financeAuditLogs(filter("USER_LOGIN", null, null, null), null).block().content()).isEmpty();
        assertThat(reads.financeAuditLogs(filter(null, null, "identity-service", null), null).block().content()).isEmpty();
        assertThat(reads.financeAuditLogs(filter(null, null, "booking-service", "BANK_ACCOUNT_MANAGE"), null)
                .block().content()).hasSize(1);
    }

    @Test
    @DisplayName("account events are never merged into finance's view, whatever includeAccountEvents asks")
    void accountEventsAreNotMerged() {
        seedEveryReason();
        template.insert(AccountEvent.builder().id("evt-1").accountId("acct-1").kind("OTP_LOCK").at(NOW).data(Map.of()).build())
                .block();
        AuditLogFilterInput withEvents = new AuditLogFilterInput(null, null, null, null, null, null, null, true, null, null);

        assertThat(reads.auditLogs(withEvents, new OffsetPaginationInput(0, 100, null, null)).block().content())
                .as("the administrator's view merges them").extracting(AuditLogEntry::source).contains("ACCOUNT_EVENT");
        assertThat(reads.financeAuditLogs(withEvents, new OffsetPaginationInput(0, 100, null, null)).block().content())
                .extracting(AuditLogEntry::source).containsOnly("AUDIT");
    }

    @Test
    @DisplayName("the administrator's view is whole: every reason, money or not")
    void administratorSeesAll() {
        seedEveryReason();

        List<String> reasons = reasonsOf(reads.auditLogs(null, new OffsetPaginationInput(0, 100, null, null)).block().content());

        for (Reason reason : Reason.values()) {
            if (!reason.isSystem()) {
                assertThat(reasons).as(reason.name()).contains(reason.name());
            }
        }
    }

    @Test
    @DisplayName("an oversized page is refused for the money view too, not clamped")
    void oversizedPageIsRefused() {
        assertThatThrownBy(() -> new OffsetPaginationInput(0, 1_000_000, null, null)).isInstanceOf(DomainRefusal.class);
    }

    @Test
    @DisplayName("finance's own read is recorded under its own reason and role, and finance cannot see that row")
    void financeReadLeavesItsOwnRow() {
        PlatformWideAccess.requireStaff(Reason.FINANCE_AUDIT_VIEW_READ, "auditLogs", Set.of(StaffRole.FINANCE))
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(
                        "finance-9", "n/a", List.of(new SimpleGrantedAuthority("ROLE_FINANCE")))))
                .block();

        List<AuditLogEntry> adminSees = reads.auditLogs(filter(null, "finance-9", null, null), null).block().content();
        assertThat(adminSees).hasSize(1);
        assertThat(adminSees.get(0).metadata()).containsEntry("reason", "FINANCE_AUDIT_VIEW_READ")
                .containsEntry("actorRole", "ROLE_FINANCE").containsEntry("operation", "auditLogs");
        assertThat(reads.financeAuditLogs(null, null).block().content()).isEmpty();
    }
}
