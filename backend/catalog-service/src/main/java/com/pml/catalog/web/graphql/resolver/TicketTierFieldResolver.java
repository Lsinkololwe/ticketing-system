package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.TicketTier;
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
}
