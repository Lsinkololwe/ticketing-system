package com.pml.identity.workflow.mirror;

import com.pml.identity.domain.valueobject.OrganizationGroups;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.shared.constants.UserType;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Repairs the Keycloak group mirror from MongoDB.
 *
 * <h2>MongoDB wins, always</h2>
 * The mirror is one-directional by design. Keycloak's group tree is a projection of membership for
 * the benefit of tokens and tooling; it is never consulted for an authorization decision, and
 * {@code GroupMirrorLintTest} proves that. So a disagreement is never a question
 * of which side is right, only of how quickly the wrong side is corrected.
 *
 * <h2>Bounded work, because the marker exists</h2>
 * A pass reads only rows carrying {@code mirrorPending}, at most {@link #BATCH} of them, so a
 * Keycloak outage that marks thousands of rows drains over several passes rather than one that
 * outlives its interval.
 *
 * <h2>The marker clears only on success</h2>
 * The repair uses the strict Keycloak writes, which raise, so a membership whose write failed stays
 * pending for the next pass — and the pending count keeps reporting a mirror that is still behind.
 *
 * <h2>What this half does not do</h2>
 * A group membership with no MongoDB row is not removed here: that direction needs a group-members
 * listing, which {@code KeycloakService} does not expose.
 */
@Slf4j
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class GroupMirrorActivitiesImpl implements GroupMirrorActivities {

    static final int BATCH = 200;
    private static final Duration AWAIT = Duration.ofMinutes(3);

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final KeycloakService keycloakService;

    public GroupMirrorActivitiesImpl(OrganizationMemberRepository memberRepository,
                                     OrganizationRepository organizationRepository,
                                     KeycloakService keycloakService) {
        this.memberRepository = memberRepository;
        this.organizationRepository = organizationRepository;
        this.keycloakService = keycloakService;
    }

    @Override
    public int repairPending() {
        try {
            Long repaired = memberRepository.findByMirrorPendingTrue()
                    .take(BATCH)
                    .flatMap(this::repair, 4)
                    .count()
                    .block(AWAIT);
            int count = repaired == null ? 0 : repaired.intValue();
            if (count > 0) {
                log.info("Group mirror repair brought {} membership(s) back into agreement", count);
            }
            return count;
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }

    /** Applies one membership to Keycloak, then clears its marker; a failure leaves it pending. */
    private Mono<OrganizationMember> repair(OrganizationMember member) {
        return organizationRepository.findById(member.getOrganizationId())
                .flatMap(organization -> apply(member, organization.getSlug()))
                .then(Mono.defer(() -> {
                    member.setMirrorPending(false);
                    return memberRepository.save(member);
                }))
                .onErrorResume(failed -> {
                    log.warn("Mirror repair failed for member {}; it stays pending: {}", member.getId(), failed.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * A removed member leaves the group their role names; an active one joins it.
     *
     * <p>A team member also holds the {@code ORGANIZER} realm role while they belong to any
     * organization. The platform's coarse gate (the organizer console and the {@code @auth(requires:
     * ORGANIZER)} operations) reads that role, and an invited administrator, manager, marketer or
     * contributor has a membership but was never an organization's applicant, so nothing else grants
     * it. It is a coarse gate only: what the member may do is decided by identity's permission check on
     * every operation. The owner's role is granted at approval, not here, so an applicant who has only
     * an owner row is not made an organizer before the application is decided.
     */
    private Mono<Void> apply(OrganizationMember member, String slug) {
        String group = OrganizationGroups.of(member.getRole());
        boolean owner = member.getRole() == OrganizationRole.OWNER;
        if (member.getStatus() == MemberStatus.REMOVED) {
            return keycloakService.leaveOrganizationGroup(member.getUserId(), slug, group)
                    .then(owner ? Mono.<Void>empty() : releaseOrganizerRole(member.getUserId()));
        }
        Mono<Void> grant = owner ? Mono.<Void>empty()
                : keycloakService.grantRealmRole(member.getUserId(), UserType.ORGANIZER.name());
        // A role change moves the member between the organization's role groups, so every other group
        // is left before the current one is joined; leaving a group the member is not in holds nobody.
        Mono<Void> leaveOthers = Flux.fromIterable(OrganizationGroups.TREE)
                .filter(other -> !other.equals(group))
                .concatMap(other -> keycloakService.leaveOrganizationGroup(member.getUserId(), slug, other))
                .then();
        return grant.then(leaveOthers).then(keycloakService.joinOrganizationGroup(member.getUserId(), slug, group));
    }

    /** Takes the role away once the user belongs to no organization at all, as owner or as member. */
    private Mono<Void> releaseOrganizerRole(String userId) {
        return memberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
                .hasElements()
                .flatMap(stillBelongs -> stillBelongs
                        ? Mono.<Void>empty()
                        : keycloakService.revokeRealmRole(userId, UserType.ORGANIZER.name()));
    }
}
