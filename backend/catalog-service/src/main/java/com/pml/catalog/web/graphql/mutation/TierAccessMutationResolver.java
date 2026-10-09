package com.pml.catalog.web.graphql.mutation;

import org.springframework.validation.annotation.Validated;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.TierAccessService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/** A buyer opens a hidden tier with the code the organizer gave them. */
@DgsComponent
@Validated
@RequiredArgsConstructor
public class TierAccessMutationResolver {

    private final TierAccessService access;

    @DgsMutation
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORGANIZER', 'ADMIN')")
    public Mono<TicketTier> unlockTierWithAccessCode(@InputArgument String eventId, @InputArgument String accessCode) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> access.unlock(eventId, accessCode, userId));
    }
}
