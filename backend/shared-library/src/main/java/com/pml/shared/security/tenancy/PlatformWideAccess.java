package com.pml.shared.security.tenancy;

import com.pml.shared.security.Authorities;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
 * <h2>Platform staff, whatever a service has configured as tenant-wide</h2>
 * Which authorities are tenant-wide is per-service configuration: booking lists finance, identity
 * and catalog do not. A call site that needs "is this caller one of the platform's own staff"
 * cannot hang that on the configuration, or the same finance officer is staff in one service and
 * not in another. {@link #isPlatformStaff} answers it from the fixed {@link StaffRole} set
 * (administrator, super administrator, finance), records the answer exactly as an administrator's
 * reach is recorded, and hands out no {@link TenantScope} — it answers the question and widens
 * nothing. A site that has only ever admitted some of those roles names them and stays as it was.
 *
 * <h2>What is recorded, and what is not</h2>
 * Each use leaves one row: who (subject and the role they acted under), which service, why (the
 * fixed {@link Reason}), which operation and when. A row never holds a request body, a filter
 * value, a token or a contact detail, so reading the trail shows that a reach happened, not what
 * it returned. A caller who is refused leaves no row; the refusal is a security log line.
 *
 * <p>A person's reach is one row per request for each distinct reason and operation: a list of
 * fifty bookings whose contact field asks "is this caller staff" fifty times is still one reach.
 *
 * <h2>Entry points</h2>
 * <ul>
 *   <li>{@link #platformWide} — strict: requires a platform-wide authority in the security
 *       context, refuses {@code ACTOR_NOT_PERMITTED} otherwise, audits when it grants.</li>
 *   <li>{@link #isPlatformWide(TenantScope, Reason)} / {@link #isPlatformWide(Reason)} — the
 *       branch form for a call site that serves both an administrator and a member and must stay
 *       identical for the member: answers {@code false} silently for anyone else, audits when it
 *       answers {@code true}.</li>
 *   <li>{@link #isPlatformStaff(Reason)} / {@link #isPlatformStaff(Reason, Set)} — the branch
 *       form for staff who include finance: {@code true} (and a row) for a caller holding one of
 *       the named {@link StaffRole}s, {@code false} silently for anyone else.</li>
 *   <li>{@link #requireStaff} — the strict form of the staff question: completes (and records)
 *       for a caller holding one of the named roles, refuses {@code ACTOR_NOT_PERMITTED} with
 *       no row for anyone else. For a read only some staff roles may make, whatever a service
 *       has configured as tenant-wide.</li>
 *   <li>{@link #recordStaffAct} — an approval or other act by staff that the caller has already
 *       authorized: leaves the same row, never refuses.</li>
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
        PROMO_CODE_MANAGE(null),
        CALLER_SCOPED_QUERY(null),
        LIFECYCLE_WORKFLOW("system:lifecycle-workflow"),
        EVENT_COMPLETION("system:event-completion"),
        FINANCE_WORKFLOW("system:finance-workflow"),
        REFUND_WORKFLOW("system:refund-workflow"),

        // Platform staff, administrators and finance alike, acting across organizers or buyers.
        AUDIT_TRAIL_READ(null),
        BOOKING_CONTACT_READ(null),
        BUYER_BOOKINGS_READ(null),
        PLATFORM_SALES_ANALYTICS_READ(null),
        REFUND_REQUEST_CANCEL(null),
        RESERVATION_SUPPORT_READ(null),
        STAFF_PERMISSION_GRANT_READ(null),
        FINANCE_AUDIT_VIEW_READ(null),
        RECOVERY_ACTION_PROPOSE(null),
        RECOVERY_ACTION_CONFIRM(null),

        // A platform role's grant satisfied an operation's permission; the operation field names the permission.
        PLATFORM_PERMISSION_EXERCISED(null);

        private final String systemSubject;

        Reason(String systemSubject) {
            this.systemSubject = systemSubject;
        }

        /** The principal a workflow reach is recorded and scoped under; {@code null} for a person. */
        String systemSubject() {
            return systemSubject;
        }

        /** Whether this reason belongs to a workflow with no caller rather than to a person's request. */
        public boolean isSystem() {
            return systemSubject != null;
        }
    }

    static final Set<String> DEFAULT_AUTHORITIES = Set.of(Authorities.ROLE_ADMIN, Authorities.ROLE_SUPER_ADMIN);

    private static final String KEY = PlatformWideAccess.class.getName();
    /** The reaches already recorded in this request, so one request is one row per distinct reach. */
    private static final String SEEN_KEY = PlatformWideAccess.class.getName() + ".SEEN";
    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final String SCOPE_ROLE = "SCOPE_PLATFORM_WIDE";

    private static final Logger log = LoggerFactory.getLogger(LogOnlyPlatformWideAuditSink.LOGGER_NAME);
    private static final LogOnlyPlatformWideAuditSink LOG_ONLY = new LogOnlyPlatformWideAuditSink();
    private static final PlatformWideAccess FALLBACK =
            new PlatformWideAccess("unknown", LOG_ONLY, Clock.systemUTC(), DEFAULT_AUTHORITIES);

    /** Counts reaches that happened but could not be written to the audit store. */
    /** How long a reach waits for the audit store before carrying on without it. */
    static final Duration SINK_TIMEOUT = Duration.ofSeconds(3);

    static final String FAILURE_METER = "platform.wide.audit.failures";

    private final String service;
    private final PlatformWideAuditSink sink;
    private final Clock clock;
    private final Set<String> authorities;
    private final MeterRegistry meters;

    public PlatformWideAccess(String service, PlatformWideAuditSink sink, Clock clock, Collection<String> authorities) {
        this(service, sink, clock, authorities, null);
    }

    /**
     * @param meters where a failed audit write is counted; {@code null} counts nothing and the
     *               failure is still logged
     */
    public PlatformWideAccess(String service, PlatformWideAuditSink sink, Clock clock, Collection<String> authorities,
                              MeterRegistry meters) {
        this.service = service == null || service.isBlank() ? "unknown" : service;
        this.sink = sink;
        this.clock = clock;
        this.authorities = Set.copyOf(authorities);
        this.meters = meters;
    }

    /** Seeds the instance a request's platform-wide reaches are recorded through. */
    public static Context seed(Context context, PlatformWideAccess access) {
        return access == null ? context : context.put(KEY, access).put(SEEN_KEY, ConcurrentHashMap.newKeySet());
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

    // ── staff entry ─────────────────────────────────────────────────────────

    /** @see #isPlatformStaff(Reason, Set) */
    public static Mono<Boolean> isPlatformStaff(Reason reason) {
        return isPlatformStaff(reason, StaffRole.all());
    }

    /**
     * Whether the signed-in caller holds one of {@code roles}, recording the use when so.
     *
     * <p>For a call site that serves staff and a member side by side and must stay identical for
     * the member. It grants no {@link TenantScope}: a finance officer asking this question learns
     * "yes, staff" and gains no organizer's rows by it. Anyone else — a customer, an organizer,
     * nobody signed in — gets {@code false} with no row.
     *
     * @param roles the staff roles the site has always admitted; a site that never admitted
     *              super administrators names only the roles it did, so its behavior is unchanged
     */
    public static Mono<Boolean> isPlatformStaff(Reason reason, Set<StaffRole> roles) {
        return isPlatformStaff(reason, null, roles);
    }

    /** @see #isPlatformStaff(Reason, Set) */
    public static Mono<Boolean> isPlatformStaff(Reason reason, String operation, Set<StaffRole> roles) {
        requireHumanReason(reason);
        Set<String> admitted = roles.stream().map(StaffRole::authority).collect(Collectors.toUnmodifiableSet());
        return current().flatMap(access -> authentication()
                .flatMap(auth -> heldAmong(auth, admitted)
                        .map(role -> access.audit(auth.getName(), role, reason, operation).thenReturn(true))
                        .orElseGet(() -> Mono.just(false)))
                .defaultIfEmpty(false));
    }

    /**
     * Requires the signed-in caller to hold one of {@code roles}, recording the use when granted.
     *
     * <p>Completes empty for a staff member holding one of the roles. Anyone else, including
     * staff of a role not named, is refused {@code ACTOR_NOT_PERMITTED} and no row is written:
     * the row means "a reach happened".
     */
    public static Mono<Void> requireStaff(Reason reason, String operation, Set<StaffRole> roles) {
        requireHumanReason(reason);
        Set<String> admitted = roles.stream().map(StaffRole::authority).collect(Collectors.toUnmodifiableSet());
        return current().flatMap(access -> authentication()
                .flatMap(auth -> heldAmong(auth, admitted)
                        .map(role -> access.audit(auth.getName(), role, reason, operation).thenReturn(true))
                        .orElseGet(() -> access.<Boolean>refuse(reason, auth.getName())))
                .switchIfEmpty(Mono.defer(() -> access.<Boolean>refuse(reason, null))))
                .then();
    }

    /**
     * Records that a staff member performed an act the caller has already authorized, such as
     * proposing or confirming a finance recovery. Completes empty and never refuses: the decision
     * was made by the rule that called this, which is why this only writes the record.
     */
    public static Mono<Void> recordStaffAct(Reason reason, String operation) {
        return isPlatformStaff(reason, operation, StaffRole.all()).then();
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
        return heldAmong(authentication, authorities);
    }

    /** The authority among {@code admitted} the caller holds; the same one every time for the same set. */
    private static Optional<String> heldAmong(Authentication authentication, Set<String> admitted) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(admitted::contains)
                .min(Comparator.naturalOrder());
    }

    /**
     * Hands the row to the sink. A sink that fails or hangs must not lock an administrator out, so
     * the reach carries on — but it is not quiet: the failure is an error line carrying the same
     * facts the row would have held (none of them personal), and a counter an operator can alert on.
     */
    private Mono<Void> audit(String actorSub, String role, Reason reason, String operation) {
        return Mono.deferContextual(ctx -> {
            Set<String> seen = ctx.getOrDefault(SEEN_KEY, null);
            // The same actor reaching for the same reason and operation again inside one request is
            // one reach: a staff member's list of fifty bookings is one row, not a row per field.
            if (seen != null && !seen.add(actorSub + '|' + role + '|' + reason + '|' + operation)) {
                return Mono.<Void>empty();
            }
            return write(new PlatformWideAuditRecord(actorSub, role, service, reason, operation, clock.instant()));
        });
    }

    private Mono<Void> write(PlatformWideAuditRecord record) {
        String actorSub = record.actorSub();
        String role = record.role();
        Reason reason = record.reason();
        String operation = record.operation();
        return Mono.defer(() -> sink.record(record)).timeout(SINK_TIMEOUT).onErrorResume(failure -> {
            log.error("securityIncident=false platform-wide reach NOT recorded in the audit store: actor={} role={} "
                            + "service={} reason={} operation={} at={} cause={}",
                    actorSub, role, service, reason, operation, record.at(), failure.getClass().getSimpleName());
            if (meters != null) {
                Counter.builder(FAILURE_METER)
                        .description("Platform-wide reaches that could not be written to the audit store")
                        .tag("service", service)
                        .tag("reason", reason.name())
                        .register(meters)
                        .increment();
            }
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
