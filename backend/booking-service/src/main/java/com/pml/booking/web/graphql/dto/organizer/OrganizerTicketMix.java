package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Breakdown of an organizer's sold tickets by ticket tier.
 *
 * <p>Backs the dashboard's ticket-mix tile. Tiers below {@code MIN_SHARE_PCT}
 * of the total are folded into a single "Other" row so no bar is too short to
 * carry a readable label.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerTicketMix {

    /**
     * Total tickets sold across all tiers. This is the denominator every row's
     * share is computed against, and the client prints it verbatim.
     */
    @Builder.Default
    private Integer totalSold = 0;

    /**
     * Total revenue across all tiers, so the client can state a tier's revenue
     * share alongside its volume share.
     */
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "ZMW";

    /**
     * Tiers, sorted by count descending. Sorting server-side keeps the ordering
     * identical everywhere the mix is rendered.
     */
    @Builder.Default
    private List<OrganizerShareRow> rows = List.of();

    public static OrganizerTicketMix empty() {
        return OrganizerTicketMix.builder().build();
    }
}
