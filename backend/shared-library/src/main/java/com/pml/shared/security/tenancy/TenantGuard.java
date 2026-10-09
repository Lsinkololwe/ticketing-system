package com.pml.shared.security.tenancy;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.function.Function;

/**
 * Loads a tenant-owned resource, or refuses in a way that says nothing.
 * OWASP A01:2021 (CWE-639) · OWASP A09:2021.
 *
 * <h2>The filter belongs in the query</h2>
 * There are two ways to enforce a boundary. Load by id and compare the owner
 * afterwards — which works until one call site forgets the comparison, and
 * forgetting is invisible because the happy path is identical. Or filter in the
 * query, where a row belonging to someone else simply does not come back, and a
 * forgotten check is a compile error rather than a leak.
 *
 * <p>{@link #locate} takes the scoped lookup as a function of the caller's
 * organizations, so the only way to call it is to have written the filter.
 *
 * <h2>Refusing identically, and still seeing the attempt</h2>
 * A cross-tenant reach and an id that was never issued get the same
 * {@code *_UNKNOWN} code, the same message and the same empty details — otherwise
 * the error code is an enumeration oracle and anyone with a list of candidate ids
 * can sort the real ones from the invented ones without holding any access at
 * all.
 *
 * <p>That leaves a real operational problem: if the two answers are identical,
 * nobody can tell a probing client from a stale bookmark. So this separates the
 * <em>response</em> from the <em>record</em>. On the refusal path only, an
 * existence probe decides which of the two happened, and a confirmed cross-tenant
 * reach is logged at WARN with {@code securityIncident=true}. The bytes on the
 * wire are the same either way; only the log differs. That log is the thing that
 * makes a cross-tenant probe detectable rather than merely refused.
 */
public final class TenantGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantGuard.class);

    private TenantGuard() {
    }

    /**
     * Loads a resource the caller is entitled to, or refuses.
     *
     * @param scope           the caller's tenancy, from {@link CurrentTenantScope#get()}
     * @param unscopedById    lookup ignoring ownership. Used for a platform administrator,
     *                        and on the refusal path to classify the attempt for the log.
     *                        It never reaches a non-administrator caller.
     * @param scopedLookup    lookup filtered to the caller's organizations — the whole point
     * @param unknownCode     the code an id that was never issued would produce. Must end
     *                        {@code _UNKNOWN}; {@link TenantBoundary} enforces it
     * @param what            what was reached for, for the log only. Never sent to the caller
     */
    public static <T> Mono<T> locate(
            TenantScope scope,
            Mono<T> unscopedById,
            Function<Set<String>, Mono<T>> scopedLookup,
            ErrorCode unknownCode,
            String what) {

        if (scope.platformAdmin()) {
            return unscopedById.switchIfEmpty(refuse(unknownCode, what, scope, false));
        }
        if (scope.permitsNothing()) {
            // No memberships: nothing can match, so skip the query and refuse. The probe
            // still runs, because "an account with no organizations asking for a real id"
            // is exactly the shape worth seeing in a log.
            return Mono.defer(() -> classifyThenRefuse(unscopedById, unknownCode, what, scope));
        }
        return scopedLookup.apply(scope.organizationIds())
                .switchIfEmpty(Mono.defer(() -> classifyThenRefuse(unscopedById, unknownCode, what, scope)));
    }

    /**
     * Loads a tenant-owned resource the caller is entitled to <b>and</b> permitted to act on.
     * OWASP A01:2021.
     *
     * <h2>Two locks, and neither opens the other</h2>
     * They answer different questions, and the platform needs both answered.
     *
     * <ul>
     *   <li>{@code scopedLookup} asks <em>does this belong to an organization you are in?</em>
     *       It is a database predicate and knows nothing else.</li>
     *   <li>{@code permitted} asks <em>are you allowed to do this to it?</em> — resolved in
     *       identity-service in a fixed order: platform role, event grant, organization
     *       role, custom permission, explicit deny.</li>
     * </ul>
     *
     * <p>The filter cannot stand in for the check. This platform ships five organization roles
     * and a MARKETER "cannot create or edit events", a CONTRIBUTOR is view-only, and event-level
     * roles override the organization role for a single event. A filter that only knows
     * membership would hand every one of them owner-level power over every event the
     * organization runs, and would make an explicit deny mean nothing. Team roles exist so
     * that a one-person organization can become a team without becoming a security problem;
     * trusting the filter alone would undo that for the sake of an optimisation.
     *
     * <p>The check equally cannot stand in for the filter. It is a call to another service, and
     * a mutation that forgets to make it looks exactly like one that made it. The filter fails
     * closed by construction; a role check alone does not.
     *
     * <h2>Both arguments are required, and that is as far as a signature can go</h2>
     * Neither can be omitted, because neither has a default — the same reason {@link #locate}
     * takes the scoped lookup rather than building one. What a signature cannot prevent is a
     * caller passing {@code event -> Mono.empty()} and calling it a permission check. Nothing in
     * Java stops that, so it is a lint's job: {@code EventWriteGuardLintTest} asserts catalog's
     * event write path actually reaches {@code checkEventAccess}.
     *
     * <h2>Why the two refusals differ</h2>
     * A failed <em>lookup</em> refuses with {@code unknownCode} and says nothing, because the id
     * came from the caller and a distinct answer would confirm it exists. A failed
     * <em>permission</em> check may be specific: by then the caller has proved membership of the
     * owning organization, so there is no longer anything to conceal — they can see the event
     * exists, and telling them they lack {@code EVENT_EDIT} is honest and lets them ask the right
     * person for it.
     *
     * @param permitted refusal path for the caller who owns the resource but may not act on it.
     *                  Must signal refusal by erroring; completing empty is treated as permitted
     */
    public static <T> Mono<T> locateAndPermit(
            TenantScope scope,
            Mono<T> unscopedById,
            Function<Set<String>, Mono<T>> scopedLookup,
            Function<T, Mono<Void>> permitted,
            ErrorCode unknownCode,
            String what) {

        // The permission check runs for a platform administrator too. Permission resolution puts
        // the platform role first, so an administrator passes it — and skipping
        // it here would make the two locks one lock for exactly the accounts that can do the
        // most damage.
        return locate(scope, unscopedById, scopedLookup, unknownCode, what)
                .flatMap(resource -> permitted.apply(resource).thenReturn(resource));
    }

    /**
     * Asserts the caller may reach an already-loaded resource.
     *
     * <p>The weaker form, for a path that legitimately holds the object before the
     * boundary can be applied — an entity fetcher resolving a federation reference,
     * say. Prefer {@link #locate}: this one can be forgotten.
     */
    public static <T> Mono<T> require(
            TenantScope scope, T resource, String ownerOrganizationId,
            ErrorCode unknownCode, String what) {

        if (scope.permits(ownerOrganizationId)) {
            return Mono.just(resource);
        }
        logIncident(what, scope);
        return Mono.error(TenantBoundary.refuse(unknownCode, what));
    }

    private static <T> Mono<T> classifyThenRefuse(
            Mono<T> unscopedById, ErrorCode unknownCode, String what, TenantScope scope) {

        return unscopedById
                .hasElement()
                // The probe is for the log alone. If it fails, the refusal still stands —
                // a boundary that depends on a second query succeeding is not a boundary.
                .onErrorReturn(false)
                .flatMap(exists -> refuse(unknownCode, what, scope, exists));
    }

    private static <T> Mono<T> refuse(
            ErrorCode unknownCode, String what, TenantScope scope, boolean existsElsewhere) {

        if (existsElsewhere) {
            logIncident(what, scope);
        } else {
            log.debug("Refused {}: no such resource, for {}", what, scope);
        }
        return Mono.error(TenantBoundary.refuse(unknownCode, what));
    }

    private static void logIncident(String what, TenantScope scope) {
        // WARN with the securityIncident marker: this is a caller reaching for a resource
        // that exists and is not theirs.
        log.warn("securityIncident=true cross-tenant reach refused: {} by {}", what, scope);
    }
}
