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
 * <h2>One organization per person</h2>
 * A person belongs to one organization at a time; identity refuses a second membership and its
 * database holds the rule under a race. So the answer is never a choice. A person who somehow
 * appears in two is a data fault and is refused (fail closed), not resolved by picking the first.
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
                        // The platform keeps a person to one organization (the identity rule and its
                        // unique index), so more than one is a data fault. Picking one would put one
                        // organization's records in front of a member of another: refuse instead.
                        log.error("Actor {} belongs to {} organizations; refusing to choose between them",
                                actorUserId, organizations.size());
                        return Mono.<String>error(new NoOrganizationException(
                                "Actor belongs to more than one organization"));
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
