package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One escrow account an organizer can currently draw a payout from.
 *
 * <p>Exists because {@code createPayoutRequest} requires an
 * {@code escrowAccountId}, and every existing escrow query is
 * {@code @tag(name: "admin")} — an organizer had no way to discover their own
 * account id, so the "Request payout" action could not be completed at all.
 *
 * <p>Only accounts already flipped to {@code PAYOUT_ELIGIBLE} with a positive
 * balance are returned. Offering an account that is still in its hold period
 * would produce a request the backend then rejects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerPayoutSource {

    /** The escrow account id, passed straight back in createPayoutRequest. */
    private String escrowAccountId;

    private String eventId;

    private String eventTitle;

    /** Withdrawable balance on this account — the cap for a request against it. */
    @Builder.Default
    private BigDecimal availableAmount = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "ZMW";

    /** When this account became eligible, so the UI can order oldest-first. */
    private Instant eligibleSince;
}
