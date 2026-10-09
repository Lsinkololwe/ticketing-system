package com.pml.identity.service;

import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.shared.security.Permission;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Decides what a user may do on the platform, in an organization, or on one event.
 *
 * <p>The order is fixed:
 * <ol>
 *   <li>the user's platform roles ({@code ADMIN}, {@code SUPER_ADMIN}, {@code FINANCE});</li>
 *   <li>for an event, an active access grant on that event — when one exists it is the whole
 *       answer, and the user's organization role is not consulted;</li>
 *   <li>active membership of the organization: the role's permissions under the organization's
 *       settings, plus the member's custom permissions, minus the denied ones.</li>
 * </ol>
 * Anything not granted by one of these is refused.
 */
public interface PermissionResolutionService {

    Mono<Boolean> hasOrganizationPermission(String userId, String organizationId, Permission permission);

    /** Completes when the user holds {@code permission} in the organization; otherwise refuses with {@code ACTOR_NOT_PERMITTED}. */
    Mono<Void> requireOrganizationPermission(String userId, String organizationId, Permission permission);

    Mono<Boolean> hasEventPermission(String userId, String eventId, String organizationId, Permission permission);

    /**
     * Completes when every code in {@code codes} is a catalogue permission of {@code scope} or
     * narrower that {@code userId} itself holds in the organization. Used before storing custom
     * permissions, so nobody hands out a permission they do not have.
     */
    Mono<Void> requireDelegable(String userId, String organizationId, Set<String> codes, Permission.Scope scope);

    Mono<OrganizationRole> getOrganizationRole(String userId, String organizationId);

    Mono<EventRole> getEventRole(String userId, String eventId);

    Mono<EffectivePermissions> getEffectivePermissions(String userId, String organizationId, String eventId);

    /**
     * What a user may do in a context, and which step of the order decided it:
     * {@code PLATFORM}, {@code EVENT}, {@code ORGANIZATION} or {@code NONE}.
     */
    record EffectivePermissions(
            String userId,
            String organizationId,
            String eventId,
            Set<Permission> permissions,
            OrganizationRole organizationRole,
            EventRole eventRole,
            String source
    ) {}
}
