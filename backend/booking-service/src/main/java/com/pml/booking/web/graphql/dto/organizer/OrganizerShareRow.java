package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One labelled part of a composition breakdown (ticket tier, check-in outcome,
 * team role).
 *
 * <p>Carries the raw {@code count} rather than a pre-computed percentage. The
 * denominator lives on the parent type, so the client can print "1,044 of
 * 1,200" — a rate without its denominator is not verifiable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerShareRow {

    /**
     * Human-readable part name, already humanised (e.g. "General admission",
     * not {@code GENERAL_ADMISSION}).
     */
    private String name;

    /**
     * Raw count for this part.
     */
    @Builder.Default
    private Integer count = 0;

    /**
     * Revenue attributable to this part, where the breakdown is monetary.
     * Null for non-monetary breakdowns such as check-in outcome.
     */
    private BigDecimal revenue;
}
