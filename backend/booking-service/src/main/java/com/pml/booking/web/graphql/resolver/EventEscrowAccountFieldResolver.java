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
 * - totalDeposits, totalWithdrawals, totalRefunds, lockUntil: the schema's names for the ledger's
 *   totalCredited, totalDebited, totalRefunded and holdUntil
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


    /** What came into the account: ticket sales credited, under the name the ledger uses. */
    @DgsData(parentType = "EventEscrowAccount", field = "totalDeposits")
    public BigDecimal totalDeposits(DgsDataFetchingEnvironment dfe) {
        return orZero(((EventEscrowAccount) dfe.getSource()).getTotalCredited());
    }

    /** What has left the account to the organization: payouts debited. */
    @DgsData(parentType = "EventEscrowAccount", field = "totalWithdrawals")
    public BigDecimal totalWithdrawals(DgsDataFetchingEnvironment dfe) {
        return orZero(((EventEscrowAccount) dfe.getSource()).getTotalDebited());
    }

    /** What has been refunded to buyers out of the account. */
    @DgsData(parentType = "EventEscrowAccount", field = "totalRefunds")
    public BigDecimal totalRefunds(DgsDataFetchingEnvironment dfe) {
        return orZero(((EventEscrowAccount) dfe.getSource()).getTotalRefunded());
    }

    /** Until when the money is held: the hold the escrow carries after the event. */
    @DgsData(parentType = "EventEscrowAccount", field = "lockUntil")
    public Instant lockUntil(DgsDataFetchingEnvironment dfe) {
        return ((EventEscrowAccount) dfe.getSource()).getHoldUntil();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
