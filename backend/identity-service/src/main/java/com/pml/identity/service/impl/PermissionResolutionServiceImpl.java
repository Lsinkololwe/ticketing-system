package com.pml.identity.service.impl;

import com.pml.identity.authorization.Decision;
import com.pml.identity.authorization.DecisionContext;
import com.pml.identity.authorization.DecisionContextLoader;
import com.pml.identity.authorization.DecisionStep;
import com.pml.identity.authorization.DelegationPolicy;
import com.pml.identity.authorization.PermissionDecider;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Set;

/**
 * Entry point for every permission question identity answers. It loads the facts with
 * {@link DecisionContextLoader} and asks {@link PermissionDecider}; the order itself lives in the
 * decider, the role tables on {@code OrganizationRole}, {@code EventRole} and {@code PlatformRole},
 * and the rules for what may be delegated in {@link DelegationPolicy}.
 */
@Service
public class PermissionResolutionServiceImpl implements PermissionResolutionService {

    private final DecisionContextLoader loader;
    private final OrganizationMemberRepository memberRepository;
    private final EventAccessGrantRepository eventAccessRepository;
    private final Clock clock;

    public PermissionResolutionServiceImpl(DecisionContextLoader loader,
                                           OrganizationMemberRepository memberRepository,
                                           EventAccessGrantRepository eventAccessRepository,
                                           Clock clock) {
        this.loader = loader;
        this.memberRepository = memberRepository;
        this.eventAccessRepository = eventAccessRepository;
        this.clock = clock;
    }

    @Override
    public Mono<Boolean> hasOrganizationPermission(String userId, String organizationId, Permission permission) {
        return decideOrganization(userId, organizationId, permission, true).map(Decision::allowed);
    }

    @Override
    public Mono<Void> requireOrganizationPermission(String userId, String organizationId, Permission permission) {
        return hasOrganizationPermission(userId, organizationId, permission)
                .flatMap(allowed -> allowed ? Mono.<Void>empty() : Mono.error(notPermitted(permission)));
    }

    @Override
    public Mono<Boolean> hasEventPermission(String userId, String eventId, String organizationId, Permission permission) {
        return decideEvent(userId, eventId, organizationId, permission, true).map(Decision::allowed);
    }

    @Override
    public Mono<Decision> decideOrganization(String userId, String organizationId, Permission permission,
                                             boolean includePlatformRoles) {
        if (permission == null) {
            return Mono.just(Decision.of(DecisionStep.PERMISSION_UNKNOWN));
        }
        return loader.load(userId, organizationId, null, includePlatformRoles, permission)
                .map(context -> PermissionDecider.decide(permission, context));
    }

    @Override
    public Mono<Decision> decideEvent(String userId, String eventId, String organizationId, Permission permission,
                                      boolean includePlatformRoles) {
        if (permission == null) {
            return Mono.just(Decision.of(DecisionStep.PERMISSION_UNKNOWN));
        }
        // An event id is what makes the question event-scoped, so a blank one must not fall back to
        // an organization-only answer that could be wider than the caller asked for.
        String scoped = eventId == null || eventId.isBlank() ? "" : eventId;
        return loader.load(userId, organizationId, scoped, includePlatformRoles, permission)
                .map(context -> PermissionDecider.decide(permission, context));
    }

    @Override
    public Mono<Void> requireDelegable(String userId, String organizationId, Set<String> codes, Permission.Scope scope) {
        if (codes == null || codes.isEmpty()) {
            return Mono.empty();
        }
        Set<Permission> requested;
        try {
            requested = DelegationPolicy.requested(codes, scope);
        } catch (TranslatedRefusal refusal) {
            return Mono.error(refusal);
        }
        return loader.load(userId, organizationId, null, true, null)
                .map(PermissionDecider::allowed)
                .flatMap(held -> DelegationPolicy.missing(requested, held)
                        .<Mono<Void>>map(Mono::error)
                        .orElseGet(Mono::empty));
    }

    @Override
    public Mono<OrganizationRole> getOrganizationRole(String userId, String organizationId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .map(OrganizationMember::getRole);
    }

    @Override
    public Mono<EventRole> getEventRole(String userId, String eventId) {
        return eventAccessRepository.findByUserIdAndEventId(userId, eventId)
                .filter(grant -> grant.isValid(clock.instant()))
                .map(EventAccessGrant::getEventRole);
    }

    @Override
    public Mono<EffectivePermissions> getEffectivePermissions(String userId, String organizationId, String eventId) {
        String scoped = eventId == null || eventId.isBlank() ? null : eventId;
        return loader.load(userId, organizationId, scoped, true, null).map(context -> {
            var member = context.member() != null && context.member().isActive() ? context.member() : null;
            var grant = scoped != null && context.grant() != null && context.grant().isValid(context.now())
                    ? context.grant() : null;
            return new EffectivePermissions(userId, organizationId, eventId,
                    PermissionDecider.allowed(context),
                    member == null ? null : member.getRole(),
                    grant == null ? null : grant.getEventRole(),
                    PermissionDecider.source(context));
        });
    }

    private static TranslatedRefusal notPermitted(Permission permission) {
        return new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "requires " + permission.code());
    }
}
