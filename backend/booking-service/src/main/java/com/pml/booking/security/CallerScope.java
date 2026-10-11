package com.pml.booking.security;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.TenantScope;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Resolves which organizations a {@code my*} query may read from.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>The argument selects; it never grants</h2>
 * The escrow and payout caller-scoped queries take an optional
 * {@code organizationId} — {@code myPayoutRequests(organizationId, status, page)}. That argument
 * exists because a user may belong to several organizations and needs to say which one they are
 * looking at. It is a <b>selector over the caller's own memberships</b>, and this is the method
 * that keeps it one.
 *
 * <p>The distinction is the whole point. {@code payoutRequestsByOrganizer(organizerId)} looks
 * almost identical and is a different thing: there the argument decides <em>whose</em> data comes
 * back. Here it can only ever narrow a set the token already established, so passing somebody
 * else's organization id returns exactly what an id that was never issued returns.
 *
 * <h2>Absent means all of mine, not all of everyone's</h2>
 * Omitting the argument widens to every organization the caller belongs to — never beyond it. A
 * caller with no memberships gets an empty set and therefore no rows, which is why the refusal
 * path below is reached rather than a query that quietly matches everything.
 */
public final class CallerScope {

    private CallerScope() {
    }

    /**
     * The organization ids this request may read, honouring an optional selector.
     *
     * @param requested the {@code organizationId} argument, or {@code null} for all of the
     *                  caller's organizations
     * @param unknown   the {@code *_UNKNOWN} code an organization that was never issued would
     *                  produce — normally {@code ORGANIZATION_UNKNOWN}, since the rejected value
     *                  is an organization id. {@link TenantBoundary} refuses anything else, which
     *                  is what stops a call site reaching for the more descriptive
     *                  {@code *_STATE_INVALID} and reopening the enumeration oracle
     */
    public static Mono<Set<String>> organizationIds(String requested, ErrorCode unknown) {
        return CurrentTenantScope.get().flatMap(scope -> {
            if (requested == null || requested.isBlank()) {
                return PlatformWideAccess.isPlatformWide(scope, PlatformWideAccess.Reason.CALLER_SCOPED_QUERY)
                        .flatMap(platformWide -> platformWide
                                // An administrator without the selector is asking across the platform.
                                // Callers pass the empty set through to an unfiltered finder knowingly.
                                ? Mono.just(Set.<String>of())
                                : refuseIfNothing(scope, unknown));
            }
            if (scope.permits(requested)) {
                return Mono.just(Set.of(requested));
            }
            return Mono.error(TenantBoundary.refuse(unknown,
                    "organization " + requested + " requested by " + scope));
        });
    }

    private static Mono<Set<String>> refuseIfNothing(TenantScope scope, ErrorCode unknown) {
        if (scope.permitsNothing()) {
            // No memberships and no selector: there is nothing this caller can be shown. Refusing
            // rather than returning an empty page keeps it indistinguishable from asking after an
            // organization that does not exist.
            return Mono.error(TenantBoundary.refuse(unknown, "no organization for " + scope));
        }
        return Mono.just(scope.organizationIds());
    }

    /**
     * The authenticated subject, for a resource keyed to a <b>person</b> rather than an
     * organization.
     *
     * <p>{@code myRefundRequests(page)} is open to any authenticated caller — a customer seeing
     * their own refunds. There is no organization in that question and no argument either: a
     * buyer is not a tenant, and the only correct source for "whose refunds" is the token.
     * {@link #organizationIds} is the wrong tool here, and offering a {@code buyerId} argument
     * would be the CWE-639 mistake this whole class exists to avoid.
     *
     * <h2>Why this refuses differently from the tenant path</h2>
     * {@link TenantBoundary} exists to make "not yours" indistinguishable from "no such id",
     * because a caller who supplies an id and gets a specific answer learns which ids are real.
     * Here the caller supplies nothing. There is no id to enumerate and nothing to disguise, so
     * the honest answer is {@code ACTOR_NOT_AUTHENTICATED} — and dressing it as a
     * {@code *_UNKNOWN} would tell a signed-out user their own refunds do not exist.
     */
    public static Mono<String> subject() {
        return CurrentTenantScope.get().flatMap(scope -> {
            String subject = scope.subject();
            if (subject == null || subject.isBlank()) {
                return Mono.error(new TranslatedRefusal(
                        ErrorCode.ACTOR_NOT_AUTHENTICATED, "no authenticated subject"));
            }
            return Mono.just(subject);
        });
    }
}