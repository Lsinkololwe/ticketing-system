package com.pml.shared.security.tenancy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The staff-aware entry: who is answered yes, what is recorded when they are, and what a failing
 * audit store does to the person reaching. OWASP A01:2021 · A09:2021.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007 · platform staff, finance included, are answered and recorded by one path")
class PlatformStaffAccessTest {

    private static final Instant NOW = Instant.parse("2026-10-13T08:30:00Z");

    private final List<PlatformWideAuditRecord> rows = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(LogOnlyPlatformWideAuditSink.LOGGER_NAME);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private PlatformWideAccess access;

    @BeforeEach
    void setUp() {
        access = new PlatformWideAccess("booking-service", record -> {
            rows.add(record);
            return Mono.empty();
        }, Clock.fixed(NOW, ZoneOffset.UTC), PlatformWideAccess.DEFAULT_AUTHORITIES, meters);
        logged.start();
        auditLogger.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(logged);
    }

    private static JwtAuthenticationToken jwt(String subject, String... roles) {
        Jwt token = Jwt.withTokenValue("eyJ-secret").header("alg", "none").subject(subject).build();
        return new JwtAuthenticationToken(token, List.of(roles).stream().map(SimpleGrantedAuthority::new).toList(), subject);
    }

    private <T> Mono<T> as(PlatformWideAccess with, Mono<T> call, JwtAuthenticationToken token) {
        return call.contextWrite(ctx -> PlatformWideAccess.seed(ctx, with))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    private <T> Mono<T> as(Mono<T> call, JwtAuthenticationToken token) {
        return as(access, call, token);
    }

    @Test
    @DisplayName("a finance officer is staff and the use is recorded under ROLE_FINANCE")
    void financeIsStaffAndRecorded() {
        Boolean staff = as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("finance-1", "ROLE_FINANCE", "ROLE_CUSTOMER")).block();

        assertThat(staff).isTrue();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).actorSub()).isEqualTo("finance-1");
        assertThat(rows.get(0).role()).isEqualTo("ROLE_FINANCE");
        assertThat(rows.get(0).service()).isEqualTo("booking-service");
        assertThat(rows.get(0).reason()).isEqualTo(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ);
        assertThat(rows.get(0).at()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("an administrator and a super administrator are staff, each recorded under their own role")
    void administratorsAreStaff() {
        assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("admin-1", "ROLE_ADMIN")).block()).isTrue();
        assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("root-1", "ROLE_SUPER_ADMIN")).block()).isTrue();

        assertThat(rows).extracting(PlatformWideAuditRecord::role).containsExactly("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("a customer, an organizer, a holder of no role and nobody signed in are refused with no record")
    void everyoneElseIsRefusedWithoutARecord() {
        for (String role : List.of("ROLE_CUSTOMER", "ROLE_ORGANIZER", "ROLE_USER", "ROLE_SCANNER")) {
            assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                    jwt("someone", role)).block()).as(role).isFalse();
        }
        assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("nobody")).block()).isFalse();
        assertThat(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ)
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, access)).block()).isFalse();

        assertThat(rows).isEmpty();
        assertThat(logged.list).isEmpty();
    }

    @Test
    @DisplayName("a bare role name or a look-alike authority is not staff")
    void lookAlikesAreNotStaff() {
        assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("x", "FINANCE", "ADMIN", "ROLE_FINANCE_INTERN", "ROLE_ADMINISTRATOR")).block()).isFalse();
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("a site that never admitted super administrators names the roles it did, and stays as it was")
    void narrowSetKeepsASiteUnchanged() {
        Set<StaffRole> adminAndFinance = Set.of(StaffRole.ADMIN, StaffRole.FINANCE);

        assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ, adminAndFinance),
                jwt("root-1", "ROLE_SUPER_ADMIN")).block()).isFalse();
        assertThat(rows).isEmpty();
        assertThat(as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ, adminAndFinance),
                jwt("finance-1", "ROLE_FINANCE")).block()).isTrue();
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("a caller holding two staff roles is recorded under the same one every time")
    void roleIsDeterministic() {
        as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("both", "ROLE_FINANCE", "ROLE_ADMIN")).block();
        as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BOOKING_CONTACT_READ),
                jwt("both", "ROLE_ADMIN", "ROLE_FINANCE")).block();

        assertThat(rows).extracting(PlatformWideAuditRecord::role).containsExactly("ROLE_ADMIN", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("the staff question hands out no tenant scope: it answers a boolean and nothing else")
    void staffEntryGrantsNoScope() {
        Object answer = as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("finance-1", "ROLE_FINANCE")).block();

        assertThat(answer).isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("the reason is a fixed declared value: a workflow reason is refused for a person, and none at all")
    void reasonIsNeverFromUserInput() {
        assertThatThrownBy(() -> PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.REFUND_WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformWideAccess.isPlatformStaff(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PlatformWideAuditRecord.class.getRecordComponents()).extracting(c -> c.getName())
                .as("a record has no field for a request body, a filter value or a token")
                .containsExactly("actorSub", "role", "service", "reason", "operation", "at");
    }

    @Test
    @DisplayName("the same reach twice in one request is one record; a different reason or request is another")
    void oneRequestIsOneRecordPerReach() {
        Mono<Boolean> twice = PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BOOKING_CONTACT_READ)
                .then(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BOOKING_CONTACT_READ))
                .then(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ));

        as(twice, jwt("finance-1", "ROLE_FINANCE")).block();
        assertThat(rows).extracting(PlatformWideAuditRecord::reason)
                .containsExactly(PlatformWideAccess.Reason.BOOKING_CONTACT_READ, PlatformWideAccess.Reason.BUYER_BOOKINGS_READ);

        as(PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BOOKING_CONTACT_READ), jwt("finance-1", "ROLE_FINANCE")).block();
        assertThat(rows).hasSize(3);
    }

    @Test
    @DisplayName("an act by staff is recorded with its fixed operation, and never refuses anyone")
    void staffActIsRecorded() {
        as(PlatformWideAccess.recordStaffAct(PlatformWideAccess.Reason.RECOVERY_ACTION_CONFIRM, "WRITE_OFF_CHARGEBACK"),
                jwt("finance-2", "ROLE_FINANCE")).block();
        as(PlatformWideAccess.recordStaffAct(PlatformWideAccess.Reason.RECOVERY_ACTION_CONFIRM, "WRITE_OFF_CHARGEBACK"),
                jwt("buyer-1", "ROLE_CUSTOMER")).block();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).operation()).isEqualTo("WRITE_OFF_CHARGEBACK");
        assertThat(rows.get(0).role()).isEqualTo("ROLE_FINANCE");
    }

    @Test
    @DisplayName("an audit store that fails does not block, is logged as an error with the facts, and is counted")
    void failingStoreDoesNotBlockButIsLoudAndCounted() {
        PlatformWideAccess failing = new PlatformWideAccess("booking-service",
                record -> Mono.error(new IllegalStateException("audit store down")),
                Clock.fixed(NOW, ZoneOffset.UTC), PlatformWideAccess.DEFAULT_AUTHORITIES, meters);

        Boolean staff = as(failing, PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("finance-1", "ROLE_FINANCE")).block();

        assertThat(staff).isTrue();
        assertThat(logged.list).hasSize(1);
        assertThat(logged.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(logged.list.get(0).getFormattedMessage())
                .contains("NOT recorded", "finance-1", "ROLE_FINANCE", "booking-service", "BUYER_BOOKINGS_READ")
                .doesNotContain("eyJ-secret");
        assertThat(meters.get(PlatformWideAccess.FAILURE_METER).tag("service", "booking-service")
                .tag("reason", "BUYER_BOOKINGS_READ").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("an audit store that hangs is given up on after the bound, and the reach carries on")
    void hangingStoreIsGivenUpOn() {
        PlatformWideAccess hanging = new PlatformWideAccess("booking-service", record -> Mono.never(),
                Clock.fixed(NOW, ZoneOffset.UTC), PlatformWideAccess.DEFAULT_AUTHORITIES, meters);

        Boolean staff = as(hanging, PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ),
                jwt("admin-1", "ROLE_ADMIN")).block(Duration.ofSeconds(10));

        assertThat(staff).isTrue();
        assertThat(meters.get(PlatformWideAccess.FAILURE_METER).counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the strict staff entry grants only the named roles, records the grant, and refuses everyone else with no row")
    void requireStaffGrantsOnlyTheNamedRoles() {
        Set<StaffRole> financeOnly = Set.of(StaffRole.FINANCE);

        as(PlatformWideAccess.requireStaff(PlatformWideAccess.Reason.FINANCE_AUDIT_VIEW_READ, "auditLogs", financeOnly),
                jwt("finance-1", "ROLE_FINANCE")).block();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).role()).isEqualTo("ROLE_FINANCE");
        assertThat(rows.get(0).operation()).isEqualTo("auditLogs");

        for (String role : List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_CUSTOMER", "ROLE_ORGANIZER")) {
            assertThatThrownBy(() -> as(PlatformWideAccess.requireStaff(
                            PlatformWideAccess.Reason.FINANCE_AUDIT_VIEW_READ, "auditLogs", financeOnly),
                    jwt("someone", role)).block())
                    .as(role)
                    .isInstanceOf(com.pml.shared.error.TranslatedRefusal.class);
        }
        assertThatThrownBy(() -> PlatformWideAccess.requireStaff(
                        PlatformWideAccess.Reason.FINANCE_AUDIT_VIEW_READ, "auditLogs", financeOnly)
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, access)).block())
                .isInstanceOf(com.pml.shared.error.TranslatedRefusal.class);
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("the strict entry's failure path is the same: it grants, logs an error and counts")
    void strictEntryFailureIsCountedToo() {
        PlatformWideAccess failing = new PlatformWideAccess("identity-service",
                record -> { throw new IllegalStateException("sync failure"); },
                Clock.fixed(NOW, ZoneOffset.UTC), PlatformWideAccess.DEFAULT_AUTHORITIES, meters);

        TenantScope scope = as(failing, PlatformWideAccess.platformWide(PlatformWideAccess.Reason.AUDIT_TRAIL_READ, "auditLogs"),
                jwt("admin-1", "ROLE_ADMIN")).block();

        assertThat(scope.platformAdmin()).isTrue();
        assertThat(meters.get(PlatformWideAccess.FAILURE_METER).tag("reason", "AUDIT_TRAIL_READ").counter().count())
                .isEqualTo(1.0);
    }
}
