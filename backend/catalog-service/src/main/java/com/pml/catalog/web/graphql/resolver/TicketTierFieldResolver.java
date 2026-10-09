package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;

/**
 * Field Resolver for TicketTier type.
 *
 * Resolves fields that need transformation or are not directly on the entity:
 * - currency: Default currency for pricing
 */
@Slf4j
@DgsComponent
public class TicketTierFieldResolver {

    private static final String DEFAULT_CURRENCY = "ZMW";

    /**
     * Resolve TicketTier.currency - default currency for the tier
     */
    @DgsData(parentType = "TicketTier", field = "currency")
    public String currency(DgsDataFetchingEnvironment dfe) {
        // Currently using a default currency - can be extended to store per-tier
        return DEFAULT_CURRENCY;
    }

    /**
     * The access code is the secret that opens a hidden tier, so only the organization that owns the
     * tier, and platform administrators, read it back. Everyone else gets null, including a buyer who
     * just unlocked the tier with it.
     */
    @DgsData(parentType = "TicketTier", field = "accessCode")
    public reactor.core.publisher.Mono<String> accessCode(DgsDataFetchingEnvironment dfe) {
        com.pml.catalog.domain.model.TicketTier tier = dfe.getSource();
        if (tier.getAccessCode() == null) {
            return reactor.core.publisher.Mono.empty();
        }
        return com.pml.shared.security.tenancy.CurrentTenantScope.get()
                .filter(scope -> scope.permits(tier.getOrganizationId()))
                .onErrorResume(error -> reactor.core.publisher.Mono.empty())
                .map(scope -> tier.getAccessCode());
    }
}
