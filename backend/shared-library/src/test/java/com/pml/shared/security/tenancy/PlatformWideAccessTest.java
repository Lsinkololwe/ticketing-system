package com.pml.shared.security.tenancy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The single named path to a platform-wide scope: who gets it, what is recorded when they do, and
 * what a refusal leaves behind. OWASP A01:2021 · A09:2021.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007 · PlatformWideAccess grants platform-wide reach to administrators only, and records it")
class PlatformWideAccessTest {

    private static final Instant NOW = Instant.parse("2026-10-10T08:30:00Z");
    private static final String TOKEN_VALUE = "eyJ-secret-token-material";

    private final List<PlatformWideAuditRecord> rows = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(LogOnlyPlatformWideAuditSink.LOGGER_NAME);
    private PlatformWideAccess access;

    @BeforeEach
    void setUp() {
        access = new PlatformWideAccess("booking-service", record -> {
            rows.add(record);
            return Mono.empty();
        }, Clock.fixed(NOW, ZoneOffset.UTC), PlatformWideAccess.DEFAULT_AUTHORITIES);
        logged.start();
        auditLogger.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(logged);
    }

    private static JwtAuthenticationToken jwt(String subject, String... roles) {
        Jwt token = Jwt.withTokenValue(TOKEN_VALUE).header("alg", "none").subject(subject).build();
        return new JwtAuthenticationToken(token,
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList(), subject);
    }

    private <T> Mono<T> as(Mono<T> call, JwtAuthenticationToken token) {
        return call.contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    private <T> Mono<T> anonymously(Mono<T> call) {
        return call.contextWrite(ctx -> PlatformWideAccess.seed(ctx, access));
    }

    @Test
    @DisplayName("an administrator receives a platform-wide scope and one audit row with the expected fields")
    void administratorIsGrantedAndAuditedOnce() {
        TenantScope scope = as(PlatformWideAccess.platformWide(
                PlatformWideAccess.Reason.BANK_ACCOUNTS_READ, "bankAccountsByOrganizer"), jwt("admin-1", "ROLE_ADMIN"))
                .block();

        assertThat(scope.platformAdmin()).isTrue();
        assertThat(scope.subject()).isEqualTo("admin-1");
        assertThat(rows).hasSize(1);
        PlatformWideAuditRecord row = rows.get(0);
        assertThat(row.actorSub()).isEqualTo("admin-1");
        assertThat(row.role()).isEqualTo("ROLE_ADMIN");
        assertThat(row.service()).isEqualTo("booking-service");
        assertThat(row.reason()).isEqualTo(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ);
        assertThat(row.operation()).isEqualTo("bankAccountsByOrganizer");
        assertThat(row.at()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("the audit row carries no token material")
    void auditRowHoldsNoTokenMaterial() {
        as(PlatformWideAccess.platformWide(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ),
                jwt("admin-1", "ROLE_SUPER_ADMIN")).block();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).toString()).doesNotContain(TOKEN_VALUE);
        assertThat(rows.get(0).role()).isEqualTo("ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("an organizer is refused ACTOR_NOT_PERMITTED and no audit row is written")
    void organizerIsRefusedWithoutARow() {
        Throwable refused = catchThrowable(() -> as(
                PlatformWideAccess.platformWide(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ),
                jwt("org-owner", "ROLE_ORGANIZER")).block());

        assertThat(refused).isInstanceOf(DomainRefusal.class);
        assertThat(((DomainRefusal) refused).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("a customer is refused, and the refusal is logged as an incident rather than as an audit row")
    void customerIsRefusedAndLogged() {
        Throwable refused = catchThrowable(() -> as(
                PlatformWideAccess.platformWide(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ),
                jwt("buyer-1", "ROLE_USER")).block());

        assertThat(refused).isInstanceOf(DomainRefusal.class);
        assertThat(rows).isEmpty();
        assertThat(logged.list).hasSize(1);
        assertThat(logged.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logged.list.get(0).getFormattedMessage())
                .contains("securityIncident=true")
                .doesNotContain(TOKEN_VALUE);
    }

    @Test
    @DisplayName("an unauthenticated caller is refused and nothing is recorded as a reach")
    void unauthenticatedIsRefused() {
        Throwable refused = catchThrowable(
                () -> anonymously(PlatformWideAccess.platformWide(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ)).block());

        assertThat(refused).isInstanceOf(DomainRefusal.class);
        assertThat(((DomainRefusal) refused).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("a request that is not authenticated is refused even when the authentication is not marked authenticated")
    void unauthenticatedTokenIsRefused() {
        var unauthenticated = new UsernamePasswordAuthenticationToken("admin-1", "x");
        Throwable refused = catchThrowable(() -> PlatformWideAccess.platformWide(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ)
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated))
                .block());

        assertThat(refused).isInstanceOf(DomainRefusal.class);
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("the branch form answers false for a member without recording anything")
    void memberBranchIsSilent() {
        Boolean wide = as(PlatformWideAccess.isPlatformWide(TenantScope.of("member-1", Set.of("org-1")),
                PlatformWideAccess.Reason.CALLER_SCOPED_QUERY), jwt("member-1", "ROLE_ORGANIZER")).block();

        assertThat(wide).isFalse();
        assertThat(rows).isEmpty();
        assertThat(logged.list).isEmpty();
    }

    @Test
    @DisplayName("the branch form answers true for an administrator scope and records the reach once")
    void administratorBranchIsAudited() {
        Boolean wide = as(PlatformWideAccess.isPlatformWide(TenantScope.platformAdministrator("admin-1", Set.of()),
                PlatformWideAccess.Reason.CALLER_SCOPED_QUERY), jwt("admin-1", "ROLE_ADMIN")).block();

        assertThat(wide).isTrue();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).reason()).isEqualTo(PlatformWideAccess.Reason.CALLER_SCOPED_QUERY);
        assertThat(rows.get(0).actorSub()).isEqualTo("admin-1");
    }

    @Test
    @DisplayName("the authentication-based branch is false for a buyer and true, once recorded, for staff")
    void authenticationBranch() {
        assertThat(as(PlatformWideAccess.isPlatformWide(PlatformWideAccess.Reason.OWNERSHIP_TRANSFER_READ),
                jwt("buyer-1", "ROLE_USER")).block()).isFalse();
        assertThat(rows).isEmpty();

        assertThat(as(PlatformWideAccess.isPlatformWide(PlatformWideAccess.Reason.OWNERSHIP_TRANSFER_READ),
                jwt("admin-1", "ROLE_ADMIN")).block()).isTrue();
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("the capability gate follows the role and writes no row")
    void capabilityGateIsNotAudited() {
        assertThat(as(PlatformWideAccess.holdsPlatformRole(), jwt("admin-1", "ROLE_ADMIN")).block()).isTrue();
        assertThat(as(PlatformWideAccess.holdsPlatformRole(), jwt("buyer-1", "ROLE_USER")).block()).isFalse();
        assertThat(anonymously(PlatformWideAccess.holdsPlatformRole()).block()).isFalse();
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("a workflow reach keeps its system subject and is logged at WARN with securityIncident=false")
    void workflowReachIsLogged() {
        TenantScope scope = PlatformWideAccess.system(PlatformWideAccess.Reason.LIFECYCLE_WORKFLOW,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(scope.platformAdmin()).isTrue();
        assertThat(scope.subject()).isEqualTo("system:lifecycle-workflow");
        assertThat(logged.list).hasSize(1);
        assertThat(logged.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logged.list.get(0).getFormattedMessage())
                .contains("securityIncident=false")
                .contains("LIFECYCLE_WORKFLOW")
                .contains(NOW.toString());
    }

    @Test
    @DisplayName("a sink that fails does not block the administrator")
    void failingSinkDoesNotBlock() {
        PlatformWideAccess failing = new PlatformWideAccess("booking-service",
                record -> Mono.error(new IllegalStateException("audit store down")),
                Clock.fixed(NOW, ZoneOffset.UTC), PlatformWideAccess.DEFAULT_AUTHORITIES);

        TenantScope scope = PlatformWideAccess.platformWide(PlatformWideAccess.Reason.BANK_ACCOUNTS_READ)
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, failing))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwt("admin-1", "ROLE_ADMIN")))
                .block();

        assertThat(scope.platformAdmin()).isTrue();
    }
}
