package com.pml.identity.service;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * A person belongs to one organization at a time.
 *
 * <p>Everything a signed-in person sees and does is scoped to the organization they belong to, and
 * that scope is derived from the person, never chosen. A second organization would make the answer
 * ambiguous: a screen, a payout or a refund could land in the wrong organization's money. So the
 * platform refuses the second membership instead of picking one. This is the service-level answer
 * with a reason the person can act on; {@code uniq_one_active_organization_per_person} is the
 * database's, which holds when two acceptances race.
 *
 * <p>A removed membership does not count: the person has left. A suspended or inactive one does,
 * because they are still a member and may be reinstated.
 */
@Component
@RequiredArgsConstructor
public class OneOrganizationPerPerson {

    private final OrganizationMemberRepository members;

    /**
     * Completes when {@code userId} may join {@code organizationId}; errors when they already belong
     * to a different organization.
     */
    public Mono<Void> require(String userId, String organizationId) {
        return members.findByUserId(userId)
                .filter(member -> member.getStatus() != MemberStatus.REMOVED)
                .filter(member -> !member.getOrganizationId().equals(organizationId))
                .hasElements()
                .flatMap(belongsElsewhere -> belongsElsewhere
                        ? Mono.<Void>error(new TranslatedRefusal(ErrorCode.MEMBER_IN_ANOTHER_ORGANIZATION,
                                "this person already belongs to another organization"))
                        : Mono.<Void>empty());
    }
}
