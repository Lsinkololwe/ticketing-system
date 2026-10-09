package com.pml.identity.service.impl;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The one place identity turns a user, a context and a permission into a decision. Role tables
 * live on {@link OrganizationRole} and {@link EventRole}; platform roles on {@link Permission}.
 */
@Service
@RequiredArgsConstructor
public class PermissionResolutionServiceImpl implements PermissionResolutionService {

    private static final String PLATFORM = "PLATFORM";
    private static final String EVENT = "EVENT";
    private static final String ORGANIZATION = "ORGANIZATION";
    private static final String NONE = "NONE";

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final EventAccessGrantRepository eventAccessRepository;
    private final Clock clock;

    @Override
    public Mono<Boolean> hasOrganizationPermission(String userId, String organizationId, Permission permission) {
        if (permission == null) {
            return Mono.just(false);
        }
        return platformPermissions(userId).flatMap(platform -> platform.contains(permission)
                ? Mono.just(true)
                : memberPermissions(userId, organizationId).map(held -> held.contains(permission)).defaultIfEmpty(false));
    }

    @Override
    public Mono<Void> requireOrganizationPermission(String userId, String organizationId, Permission permission) {
        return hasOrganizationPermission(userId, organizationId, permission)
                .flatMap(allowed -> allowed ? Mono.<Void>empty() : Mono.error(notPermitted(permission)));
    }

    @Override
    public Mono<Boolean> hasEventPermission(String userId, String eventId, String organizationId, Permission permission) {
        if (permission == null) {
            return Mono.just(false);
        }
        return platformPermissions(userId).flatMap(platform -> {
            if (platform.contains(permission)) {
                return Mono.just(true);
            }
            // A grant counts only on an event of the organization that issued it.
            return activeGrant(userId, eventId)
                    .filter(grant -> organizationId == null || organizationId.equals(grant.getOrganizationId()))
                    .map(grant -> grant.hasPermission(permission))
                    .switchIfEmpty(Mono.defer(() -> memberPermissions(userId, organizationId)
                            .map(held -> held.contains(permission))
                            .defaultIfEmpty(false)));
        });
    }

    @Override
    public Mono<Void> requireDelegable(String userId, String organizationId, Set<String> codes, Permission.Scope scope) {
        if (codes == null || codes.isEmpty()) {
            return Mono.empty();
        }
        EnumSet<Permission> requested = EnumSet.noneOf(Permission.class);
        for (String code : codes) {
            Permission permission = Permission.fromCode(code).orElse(null);
            if (permission == null) {
                return Mono.error(new TranslatedRefusal(ErrorCode.PERMISSION_UNKNOWN, "no permission is named " + code));
            }
            // Scope order is PLATFORM, ORGANIZATION, EVENT: a later scope is narrower.
            if (permission.scope().ordinal() < scope.ordinal()) {
                return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                        permission.code() + " cannot be granted at " + scope.name().toLowerCase() + " level"));
            }
            requested.add(permission);
        }
        return platformPermissions(userId)
                .flatMap(platform -> memberPermissions(userId, organizationId)
                        .defaultIfEmpty(Set.of())
                        .map(held -> {
                            EnumSet<Permission> missing = EnumSet.copyOf(requested);
                            missing.removeAll(platform);
                            missing.removeAll(held);
                            return missing;
                        }))
                .flatMap(missing -> missing.isEmpty()
                        ? Mono.<Void>empty()
                        : Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                                "cannot grant permissions you do not hold: " + Permission.codes(missing))));
    }

    @Override
    public Mono<OrganizationRole> getOrganizationRole(String userId, String organizationId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .map(OrganizationMember::getRole);
    }

    @Override
    public Mono<EventRole> getEventRole(String userId, String eventId) {
        return activeGrant(userId, eventId).map(EventAccessGrant::getEventRole);
    }

    @Override
    public Mono<EffectivePermissions> getEffectivePermissions(String userId, String organizationId, String eventId) {
        EffectivePermissions none = new EffectivePermissions(userId, organizationId, eventId, Set.of(), null, null, NONE);
        return platformPermissions(userId).flatMap(platform -> {
            if (!platform.isEmpty()) {
                return Mono.just(new EffectivePermissions(userId, organizationId, eventId, platform, null, null, PLATFORM));
            }
            Mono<EffectivePermissions> fromMembership = organizationId == null
                    ? Mono.just(none)
                    : activeMember(userId, organizationId)
                            .flatMap(member -> settingsOf(organizationId).map(settings -> new EffectivePermissions(
                                    userId, organizationId, eventId, member.permissions(settings), member.getRole(), null, ORGANIZATION)))
                            .defaultIfEmpty(none);
            if (eventId == null || eventId.isBlank()) {
                return fromMembership;
            }
            return activeGrant(userId, eventId)
                    .flatMap(grant -> getOrganizationRole(userId, organizationId == null ? grant.getOrganizationId() : organizationId)
                            .map(java.util.Optional::of)
                            .defaultIfEmpty(java.util.Optional.empty())
                            .map(role -> new EffectivePermissions(userId, organizationId, eventId, grant.permissions(),
                                    role.orElse(null), grant.getEventRole(), EVENT)))
                    .switchIfEmpty(fromMembership);
        });
    }

    /** What the user's platform roles grant; empty for anyone who holds none. */
    private Mono<Set<Permission>> platformPermissions(String userId) {
        if (userId == null) {
            return Mono.just(Set.of());
        }
        return userRepository.findById(userId)
                .map(user -> Permission.grantedByPlatformRoles(
                        user.getRoles() == null ? List.of() : user.getRoles().stream().map(UserType::name).toList()))
                .defaultIfEmpty(Set.of());
    }

    /** The active member's permissions under the organization's current settings; empty when not an active member. */
    private Mono<Set<Permission>> memberPermissions(String userId, String organizationId) {
        if (userId == null || organizationId == null) {
            return Mono.empty();
        }
        return activeMember(userId, organizationId)
                .flatMap(member -> settingsOf(organizationId).map(member::permissions));
    }

    private Mono<OrganizationMember> activeMember(String userId, String organizationId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(OrganizationMember::isActive);
    }

    /** The organization's settings; a new organization's defaults, every switch off, when it has none. */
    private Mono<OrganizationSettings> settingsOf(String organizationId) {
        return organizationRepository.findById(organizationId)
                .mapNotNull(Organization::getSettings)
                .defaultIfEmpty(new OrganizationSettings());
    }

    private Mono<EventAccessGrant> activeGrant(String userId, String eventId) {
        if (userId == null || eventId == null) {
            return Mono.empty();
        }
        return eventAccessRepository.findByUserIdAndEventId(userId, eventId)
                .filter(grant -> grant.isValid(clock.instant()));
    }

    private static TranslatedRefusal notPermitted(Permission permission) {
        return new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "requires " + permission.code());
    }
}
