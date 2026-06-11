package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Finance overview for organizer's finance dashboard.
 *
 * Provides comprehensive financial information:
 * - Balance breakdown (available, pending, total earned)
 * - Payout information (pending requests, last payout)
 * - Revenue breakdown (gross revenue, refunds, fees, net)
 * - Monthly comparison metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerFinanceOverview {

    /**
     * Balance available for immediate withdrawal
     */
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    /**
     * Balance currently being processed (in escrow, pending payouts)
     */
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    /**
     * Total amount earned all time
     */
    @Builder.Default
    private BigDecimal totalEarned = BigDecimal.ZERO;

    /**
     * Currency for all amounts
     */
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Number of pending payout requests
     */
    @Builder.Default
    private Integer pendingPayoutRequests = 0;

    /**
     * Date of the most recent completed payout
     */
    private LocalDateTime lastPayoutDate;

    /**
     * Amount of the most recent completed payout
     */
    private BigDecimal lastPayoutAmount;

    /**
     * Total gross revenue from ticket sales
     */
    @Builder.Default
    private BigDecimal totalTicketRevenue = BigDecimal.ZERO;

    /**
     * Total refunds issued
     */
    @Builder.Default
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    /**
     * Total platform fees deducted
     */
    @Builder.Default
    private BigDecimal platformFees = BigDecimal.ZERO;

    /**
     * Net earnings after fees and refunds
     */
    @Builder.Default
    private BigDecimal netEarnings = BigDecimal.ZERO;

    /**
     * Total earnings in the current month
     */
    @Builder.Default
    private BigDecimal earningsThisMonth = BigDecimal.ZERO;

    /**
     * Total earnings in the previous month
     */
    @Builder.Default
    private BigDecimal earningsLastMonth = BigDecimal.ZERO;

    /**
     * Month-over-month growth percentage
     */
    private Float monthlyGrowth;

    /**
     * Creates empty finance overview for organizers with no data
     */
    public static OrganizerFinanceOverview empty() {
        return OrganizerFinanceOverview.builder()
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .currency("ZMW")
                .pendingPayoutRequests(0)
                .totalTicketRevenue(BigDecimal.ZERO)
                .totalRefunds(BigDecimal.ZERO)
                .platformFees(BigDecimal.ZERO)
                .netEarnings(BigDecimal.ZERO)
                .earningsThisMonth(BigDecimal.ZERO)
                .earningsLastMonth(BigDecimal.ZERO)
                .build();
    }
}
