package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.EventEscrowAccount;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Field Resolver for EventEscrowAccount type.
 *
 * Resolves fields that need transformation or have schema/entity name mismatches:
 * - totalCommissions: Platform commissions collected from this event
 * - lockReason: Reason for account lock
 * - closedAt: When the account was closed
 * - closedReason: Reason for closure
 */
@Slf4j
@DgsComponent
public class EventEscrowAccountFieldResolver {

    /**
     * Resolve EventEscrowAccount.totalCommissions - total platform commissions.
     * Returns BigDecimal.ZERO if not set (null-safe).
     */
    @DgsData(parentType = "EventEscrowAccount", field = "totalCommissions")
    public BigDecimal totalCommissions(DgsDataFetchingEnvironment dfe) {
        EventEscrowAccount account = dfe.getSource();
        return account.getTotalCommissions() != null
            ? account.getTotalCommissions()
            : BigDecimal.ZERO;
    }


    /**
     * Resolve EventEscrowAccount.closedAt - closure timestamp.
     */
    @DgsData(parentType = "EventEscrowAccount", field = "closedAt")
    public Instant closedAt(DgsDataFetchingEnvironment dfe) {
        EventEscrowAccount account = dfe.getSource();
        return account.getClosedAt();
    }

}
