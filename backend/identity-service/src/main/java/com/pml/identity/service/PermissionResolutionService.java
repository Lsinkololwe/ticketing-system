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
 * Anything not granted by one of these is refused. A member's permission is further subject to
 * the organization's lifecycle status: an organization that is not approved cannot publish or
 * request payouts whatever its members' roles say, and an organization that cannot be found
 * grants its members nothing.
 *
 * <p>This is the only implementation of that order. Other entry points (the cross-service
 * authorization checks) map their own request and response shapes onto {@code decide…}.
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
     * The decision for an organization-level permission, with the step that made it.
     *
     * @param includePlatformRoles whether platform roles may grant the permission; a caller that
     *                             answers only for organization membership passes {@code false}
     */
    Mono<Decision> decideOrganization(String userId, String organizationId, Permission permission,
                                      boolean includePlatformRoles);

    /**
     * The decision for a permission on one event: an active grant from the event's organization
     * decides alone; without one the organization membership decides. {@code organizationId} may be
     * {@code null} when the caller does not know it, in which case only a grant can allow.
     */
    Mono<Decision> decideEvent(String userId, String eventId, String organizationId, Permission permission,
                               boolean includePlatformRoles);

    /**
     * A decision and what produced it. {@code role} is the member's organization role or the
     * grant's event role (as a name) when the outcome turned on one; {@code organizationId} and
     * {@code organizationStatus} are set when an organization was consulted.
     */
    record Decision(Outcome outcome, String role, String organizationId, String organizationStatus) {

        public boolean allowed() {
            return outcome == Outcome.PLATFORM_ROLE || outcome == Outcome.EVENT_GRANT || outcome == Outcome.MEMBER;
        }

        public static Decision of(Outcome outcome) {
            return new Decision(outcome, null, null, null);
        }
    }

    /** Which step of the order decided, and whether it allowed or refused. */
    enum Outcome {
        PLATFORM_ROLE,
        EVENT_GRANT,
        MEMBER,
        PERMISSION_UNKNOWN,
        GRANT_LACKS_PERMISSION,
        MEMBER_LACKS_PERMISSION,
        ORGANIZATION_STATUS,
        NOT_A_MEMBER,
        ORGANIZATION_UNKNOWN,
        NO_ORGANIZATION
    }

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
