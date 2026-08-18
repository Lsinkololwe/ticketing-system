package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Resolves which organization an authenticated actor is acting for.
 *
 * <h2>Derived, never accepted</h2>
 * {@link TenantAccessGuard} exists for the case where a caller NAMES an
 * organization — it has to, because a supplied id can be anyone's. The dashboard
 * is the other shape: the organizer is asking about their own organization, so
 * there is no id to supply and nothing to spoof. Deriving it from the JWT
 * subject removes the attack rather than checking for it.
 *
 * <p>Which is why this returns an id rather than taking one. A method signature
 * that accepted {@code organizationId} would invite a caller to pass one, and
 * the next endpoint written against it would forget the guard.
 *
 * <h2>Multiple organizations</h2>
 * A user may belong to several. This returns the first, which is correct for
 * today's single-organization organizers and wrong the moment someone runs two.
 * Called out rather than hidden: the fix is an explicit organization selector in
 * the UI, and until that exists a silent "first" is the honest approximation —
 * but it is an approximation, and a user with two organizations will see one
 * dashboard and not be told why.
 *
 * <h2>No organization is an error, not an empty dashboard</h2>
 * An organizer with no organization cannot have escrow, so returning empty would
 * render a plausible zero. It is far more likely to mean the account is
 * mis-provisioned, which someone should hear about.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActorOrganizationResolver {

    private final IdentityServiceClient identityServiceClient;

    /** Raised when the actor belongs to no organization at all. */
    public static class NoOrganizationException extends RuntimeException {
        public NoOrganizationException(String message) {
            super(message);
        }
    }

    /**
     * @param actorUserId the JWT subject — never a value from request input
     * @return the organization whose records the actor may read
     */
    public Mono<String> resolve(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            return Mono.error(new NoOrganizationException("No authenticated actor"));
        }

        return identityServiceClient.getUserOrganizations(actorUserId)
                .flatMap(response -> {
                    var organizations = response == null ? null : response.organizations();
                    if (organizations == null || organizations.isEmpty()) {
                        return Mono.<String>error(new NoOrganizationException(
                                "Actor belongs to no organization"));
                    }
                    if (organizations.size() > 1) {
                        log.warn("Actor {} belongs to {} organizations; showing the first. "
                                        + "An organization selector is needed before this is correct.",
                                actorUserId, organizations.size());
                    }
                    return Mono.just(organizations.get(0).organizationId());
                })
                .onErrorResume(error -> {
                    if (error instanceof NoOrganizationException) {
                        return Mono.error(error);
                    }
                    // Fails closed, like the guard: an unresolvable organization
                    // must not fall through to an unscoped query.
                    log.error("Could not resolve organization for actor {}: {}",
                            actorUserId, error.getMessage());
                    return Mono.error(new NoOrganizationException(
                            "Organization could not be resolved"));
                });
    }
}
