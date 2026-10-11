package com.pml.shared.security.tenancy;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * The one named, audited way to read across every organization.
 * OWASP A01:2021 · A09:2021.
 *
 * <h2>Why a single place</h2>
 * A platform administrator's reach used to be decided inline wherever a resolver or service
 * needed it: {@code scope.platformAdmin() ? unfiltered : scoped}, or a private set of role names,
 * repeated per call site. Each copy was a place the boundary could quietly widen and none of them
 * left a record. Every such decision now goes through this class, which does two things the inline
 * checks did not: it makes the decision in one place that a lint can pin
 * ({@code PlatformWideBypassLintTest}), and it records each use.
 *
 * <h2>What a {@link Reason} is</h2>
 * A fixed enum value chosen by the call site. It is never derived from the request, so an audit
 * row's reason cannot be forged by a caller and cannot carry user input into a log line.
 *
 * <h2>Entry points</h2>
 * <ul>
 *   <li>{@link #platformWide} — strict: requires a platform-wide authority in the security
 *       context, refuses {@code ACTOR_NOT_PERMITTED} otherwise, audits when it grants.</li>
 *   <li>{@link #isPlatformWide(TenantScope, Reason)} / {@link #isPlatformWide(Reason)} — the
 *       branch form for a call site that serves both an administrator and a member and must stay
 *       identical for the member: answers {@code false} silently for anyone else, audits when it
 *       answers {@code true}.</li>
 *   <li>{@link #system} — a Temporal activity or other no-request path acting platform-wide by
 *       design; log-only, named by a system reason.</li>
 *   <li>{@link #holdsPlatformRole()} — a capability gate (may set {@code featured}, may see the
 *       moderation log). It reaches no one else's rows, so it is not audited.</li>
 * </ul>
 *
 * <h2>Where collaborators come from</h2>
 * The sink, clock and service name are carried in the Reactor context by
 * {@link TenantScopeWebFilter}, the same way {@link CurrentTenantScope} is, so a static call from
 * a resolver or a static helper needs no injection. Outside a request (a unit test that seeds
 * only a scope) the log-only fallback applies.
 */
public final class PlatformWideAccess {

    /** Why a call site reaches across organizations. One value per call site; never user input. */
    public enum Reason {
        EVENT_ACCESS_GRANTS_READ(null),
        USER_EVENT_GRANT_READ(null),
        ORGANIZATION_MEMBER_READ(null),
        ORGANIZATION_TEAM_READ(null),
        OWNERSHIP_TRANSFER_READ(null),
        BANK_ACCOUNT_MANAGE(null),
        BANK_ACCOUNTS_READ(null),
        DEFAULT_BANK_ACCOUNT_READ(null),
        CALLER_SCOPED_QUERY(null),
        LIFECYCLE_WORKFLOW("system:lifecycle-workflow"),
        EVENT_COMPLETION("system:event-completion"),
        FINANCE_WORKFLOW("system:finance-workflow"),
        REFUND_WORKFLOW("system:refund-workflow");

        private final String systemSubject;

        Reason(String systemSubject) {
            this.systemSubject = systemSubject;
        }

        /** The principal a workflow reach is recorded and scoped under; {@code null} for a person. */
        String systemSubject() {
            return systemSubject;
        }
    }

    static final Set<String> DEFAULT_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private static final String KEY = PlatformWideAccess.class.getName();
    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final String SCOPE_ROLE = "SCOPE_PLATFORM_WIDE";

    private static final Logger log = LoggerFactory.getLogger(LogOnlyPlatformWideAuditSink.LOGGER_NAME);
    private static final LogOnlyPlatformWideAuditSink LOG_ONLY = new LogOnlyPlatformWideAuditSink();
    private static final PlatformWideAccess FALLBACK =
            new PlatformWideAccess("unknown", LOG_ONLY, Clock.systemUTC(), DEFAULT_AUTHORITIES);

    private final String service;
    private final PlatformWideAuditSink sink;
    private final Clock clock;
    private final Set<String> authorities;

    public PlatformWideAccess(String service, PlatformWideAuditSink sink, Clock clock, Collection<String> authorities) {
        this.service = service == null || service.isBlank() ? "unknown" : service;
        this.sink = sink;
        this.clock = clock;
        this.authorities = Set.copyOf(authorities);
    }

    /** Seeds the instance a request's platform-wide reaches are recorded through. */
    public static Context seed(Context context, PlatformWideAccess access) {
        return access == null ? context : context.put(KEY, access);
    }

    // ── strict entry ────────────────────────────────────────────────────────

    /** @see #platformWide(Reason, String) */
    public static Mono<TenantScope> platformWide(Reason reason) {
        return platformWide(reason, null);
    }

    /**
     * A scope that permits every organization, for a caller holding a platform-wide authority.
     *
     * <p>A caller without one — a customer, an organizer, or nobody signed in — is refused
     * {@code ACTOR_NOT_PERMITTED}, and no audit row is written for the refusal: the row means
     * "a reach happened". The refusal is logged on its own as a security incident.
     *
     * @param operation a fixed operation name where the call site has one; never request data
     */
    public static Mono<TenantScope> platformWide(Reason reason, String operation) {
        requireHumanReason(reason);
        return current().flatMap(access -> authentication()
                .flatMap(auth -> access.roleOf(auth)
                        .map(role -> access.audit(auth.getName(), role, reason, operation)
                                .thenReturn(TenantScope.platformAdministrator(auth.getName(), Set.of())))
                        .orElseGet(() -> access.<TenantScope>refuse(reason, auth.getName())))
                .switchIfEmpty(Mono.defer(() -> access.<TenantScope>refuse(reason, null))));
    }

    // ── branch entry ────────────────────────────────────────────────────────

    /** @see #isPlatformWide(TenantScope, Reason, String) */
    public static Mono<Boolean> isPlatformWide(TenantScope scope, Reason reason) {
        return isPlatformWide(scope, reason, null);
    }

    /**
     * Whether the already-resolved {@code scope} is platform-wide, recording the reach when it is.
     *
     * <p>The scope's flag is set by {@link TenantScopeWebFilter} from a realm role and from nothing
     * the caller supplies, so it is trusted here rather than re-derived: a unit test that seeds a
     * scope and no authentication keeps working, and production cannot disagree with itself.
     */
    public static Mono<Boolean> isPlatformWide(TenantScope scope, Reason reason, String operation) {
        requireHumanReason(reason);
        if (scope == null || !scope.platformAdmin()) {
            return Mono.just(false);
        }
        return current().flatMap(access -> authentication()
                .map(auth -> access.roleOf(auth).orElse(SCOPE_ROLE))
                .defaultIfEmpty(SCOPE_ROLE)
                .flatMap(role -> access.audit(scope.subject(), role, reason, operation))
                .thenReturn(true));
    }

    /**
     * Whether the signed-in caller holds a platform-wide authority, recording the reach when so.
     * For a site that holds an {@code Authentication} and no {@link TenantScope}.
     */
    public static Mono<Boolean> isPlatformWide(Reason reason) {
        requireHumanReason(reason);
        return current().flatMap(access -> authentication()
                .flatMap(auth -> access.roleOf(auth)
                        .map(role -> access.audit(auth.getName(), role, reason, null).thenReturn(true))
                        .orElseGet(() -> Mono.just(false)))
                .defaultIfEmpty(false));
    }

    /**
     * Whether the signed-in caller holds a platform-wide authority, with no record.
     *
     * <p>For a capability gate that never widens a data read: setting a flag only staff may set,
     * showing a field only staff may see. Auditing those would write a row per field per row in
     * a list, which buries the reaches that matter.
     */
    public static Mono<Boolean> holdsPlatformRole() {
        return current().flatMap(access -> authentication()
                .map(auth -> access.roleOf(auth).isPresent())
                .defaultIfEmpty(false));
    }

    // ── workflow entry ──────────────────────────────────────────────────────

    /**
     * The scope a workflow activity acts under: no caller, platform-wide by design, because the
     * transition that scheduled it already authorized the work.
     *
     * <p>Recorded on the log-only sink under the same {@code system:...} subject the scope carries.
     * Takes the caller's own {@link Clock} so the timestamp is the service's, not the machine's.
     */
    public static TenantScope system(Reason reason, Clock clock) {
        if (reason == null || reason.systemSubject() == null) {
            throw new IllegalArgumentException("a workflow reach needs a system reason");
        }
        LOG_ONLY.write(new PlatformWideAuditRecord(
                reason.systemSubject(), SYSTEM_ROLE, FALLBACK.service, reason, null, clock.instant()));
        return TenantScope.platformAdministrator(reason.systemSubject(), Set.of());
    }

    // ── internals ───────────────────────────────────────────────────────────

    private static void requireHumanReason(Reason reason) {
        if (reason == null || reason.systemSubject() != null) {
            throw new IllegalArgumentException("a request reach needs a non-system reason");
        }
    }

    private static Mono<PlatformWideAccess> current() {
        return Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault(KEY, FALLBACK)));
    }

    private static Mono<Authentication> authentication() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> auth != null && auth.isAuthenticated() ? Mono.just(auth) : Mono.empty());
    }

    /** The platform-wide authority the caller holds; the same one every time for the same set. */
    private Optional<String> roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authorities::contains)
                .min(Comparator.naturalOrder());
    }

    private Mono<Void> audit(String actorSub, String role, Reason reason, String operation) {
        PlatformWideAuditRecord record =
                new PlatformWideAuditRecord(actorSub, role, service, reason, operation, clock.instant());
        return sink.record(record).onErrorResume(failure -> {
            log.warn("securityIncident=false platform-wide audit sink failed: service={} reason={} cause={}",
                    service, reason, failure.getClass().getSimpleName());
            return Mono.empty();
        });
    }

    private <T> Mono<T> refuse(Reason reason, String actorSub) {
        log.warn("securityIncident=true platform-wide access refused: actor={} service={} reason={}",
                actorSub, service, reason);
        return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                "platform-wide access needs a platform administrator"));
    }
}
