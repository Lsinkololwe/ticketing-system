package com.pml.identity.authorization;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.shared.constants.UserType;
import com.pml.shared.security.Permission;
import com.pml.shared.security.PlatformRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads what a decision needs and nothing else: the caller's platform roles, then, only when those
 * do not settle the question, their grant on the event, their membership and the organization.
 * It returns a {@link DecisionContext}; it makes no decision.
 */
@Component
@RequiredArgsConstructor
public class DecisionContextLoader {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final EventAccessGrantRepository eventAccessRepository;
    private final Clock clock;

    /**
     * Loads the context for {@code userId} in an organization, and for one event when
     * {@code eventId} is not null.
     *
     * @param includePlatformRoles whether the caller's platform roles are consulted at all
     * @param settledBy            a permission already known to be the question: when a platform
     *                             role grants it, no membership or grant is read, because nothing
     *                             they say can change the answer
     */
    public Mono<DecisionContext> load(String userId, String organizationId, String eventId,
                                      boolean includePlatformRoles, Permission settledBy) {
        boolean eventScoped = eventId != null;
        return platformGrants(userId, includePlatformRoles).flatMap(platform -> {
            if (settledBy != null && platform.contains(settledBy)) {
                return Mono.just(DecisionContext.platformOnly(platform, eventScoped, organizationId, clock.instant()));
            }
            Mono<Optional<EventAccessGrant>> grant = eventScoped
                    ? grantFor(userId, eventId).map(Optional::of).defaultIfEmpty(Optional.empty())
                    : Mono.just(Optional.empty());
            return grant.flatMap(foundGrant -> {
                // A caller that named no organization is answered by the grant alone; the grant's
                // organization is read only so the roles held there can be reported.
                String lookupOrganization = organizationId != null ? organizationId
                        : foundGrant.map(EventAccessGrant::getOrganizationId).orElse(null);
                Mono<Optional<OrganizationMember>> member = lookupOrganization == null || userId == null
                        ? Mono.just(Optional.empty())
                        : memberRepository.findByUserIdAndOrganizationId(userId, lookupOrganization)
                                .map(Optional::of).defaultIfEmpty(Optional.empty());
                Mono<Optional<Organization>> organization = lookupOrganization == null
                        ? Mono.just(Optional.empty())
                        : organizationRepository.findById(lookupOrganization)
                                .map(Optional::of).defaultIfEmpty(Optional.empty());
                return Mono.zip(member, organization).map(found -> new DecisionContext(
                        platform, eventScoped, organizationId, foundGrant.orElse(null),
                        found.getT1().orElse(null), found.getT2().orElse(null), clock.instant()));
            });
        });
    }

    /** What the caller's platform roles grant; empty for anyone who holds none, or when not consulted. */
    public Mono<Set<Permission>> platformGrants(String userId, boolean includePlatformRoles) {
        if (!includePlatformRoles || userId == null) {
            return Mono.just(Set.of());
        }
        return userRepository.findById(userId)
                .map(user -> PlatformRole.grantedBy(user.getRoles() == null
                        ? List.<String>of() : user.getRoles().stream().map(UserType::name).toList()))
                .defaultIfEmpty(Set.of());
    }

    private Mono<EventAccessGrant> grantFor(String userId, String eventId) {
        return userId == null || eventId.isBlank()
                ? Mono.empty() : eventAccessRepository.findByUserIdAndEventId(userId, eventId);
    }
}
