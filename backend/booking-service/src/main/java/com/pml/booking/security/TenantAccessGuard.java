package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Confirms an actor may act for the organization whose money is being touched.
 *
 * <h2>Why this exists separately from the query filter</h2>
 * Scoping a query by {@code organizationId} answers "which rows belong to this
 * tenant". It does not answer "does the person asking belong to this tenant" —
 * the organization id arrives in the request, so a filter built from it is a
 * filter the caller chose. Pass someone else's organization id and a
 * tenant-scoped query returns their escrow accounts, correctly filtered and
 * completely wrong.
 *
 * <p>So the two run together and mean different things: the filter decides WHAT
 * is returned, this decides WHETHER anything should be. That is the whole
 * pattern — neither is sufficient, and the filter is the one that looks
 * sufficient.
 *
 * <h2>Fails closed</h2>
 * An identity-service outage denies rather than allows. That is a deliberate
 * availability trade: an organizer briefly unable to view their balance is an
 * inconvenience, while a moment where every organization can read every other
 * organization's money is a breach. The error is distinguishable from a genuine
 * refusal so support can tell the two apart.
 *
 * <h2>OWASP A01:2021</h2>
 * Broken access control, specifically the horizontal case: an authenticated user
 * reaching another tenant's records by supplying their identifier. Every money
 * read scoped by organization passes through here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAccessGuard {

    private final IdentityServiceClient identityServiceClient;

    /** Raised when the actor may not act for the organization they named. */
    public static class TenantAccessDeniedException extends RuntimeException {
        public TenantAccessDeniedException(String message) {
            super(message);
        }
    }

    /**
     * @param userId         the actor, taken from the JWT and never from input
     * @param organizationId the tenant whose records are being reached for
     * @param permission     e.g. {@code payout:request}, {@code escrow:read}
     * @return the organizationId, so this composes into a reactive chain
     */
    public Mono<String> requireAccess(String userId, String organizationId, String permission) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new TenantAccessDeniedException("No authenticated actor"));
        }
        if (organizationId == null || organizationId.isBlank()) {
            // A blank tenant would make the downstream query unscoped, which
            // returns every organization's rows. Refuse rather than filter on
            // nothing.
            return Mono.error(new TenantAccessDeniedException(
                    "No organization specified — refusing an unscoped query"));
        }

        return identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                        .userId(userId)
                        .organizationId(organizationId)
                        .requiredPermission(permission)
                        .build())
                .flatMap(result -> {
                    if (!result.isAuthorized()) {
                        log.warn("Tenant access denied: actor={} organization={} permission={} reason={}",
                                userId, organizationId, permission, result.getReason());
                        return Mono.error(new TenantAccessDeniedException(
                                "Not permitted to act for this organization"));
                    }
                    return Mono.just(organizationId);
                })
                // One handler, type-checked. Two chained handlers do not work
                // here: the first re-raises the denial and the second catches it,
                // relabelling a legitimate refusal as an infrastructure failure —
                // so support chases an outage that never happened. Both paths
                // deny; they must not report the same cause.
                .onErrorResume(error -> {
                    if (error instanceof TenantAccessDeniedException denied) {
                        return Mono.error(denied);
                    }
                    log.error("Tenant access check failed for actor={} organization={}: {}",
                            userId, organizationId, error.getMessage());
                    return Mono.error(new TenantAccessDeniedException(
                            "Access could not be verified"));
                });
    }
}
