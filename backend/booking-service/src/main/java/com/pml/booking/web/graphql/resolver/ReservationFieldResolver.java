package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.TicketReservation;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Field Resolver for TicketReservation type.
 *
 * Resolves computed fields:
 * - remainingSeconds: Time until reservation expires
 * - currency: Default currency for the reservation
 * - promoCodeApplied: Maps from promoCode field
 */
@Slf4j
@DgsComponent
public class ReservationFieldResolver {

    private static final String DEFAULT_CURRENCY = "ZMW";

    /**
     * Resolve TicketReservation.remainingSeconds - seconds until expiration.
     *
     * @param dfe DataFetchingEnvironment containing the parent TicketReservation
     * @return Remaining seconds (0 if expired)
     */
    @DgsData(parentType = "TicketReservation", field = "remainingSeconds")
    public Integer remainingSeconds(DgsDataFetchingEnvironment dfe) {
        TicketReservation reservation = dfe.getSource();

        if (reservation.getExpiresAt() == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(reservation.getExpiresAt())) {
            return 0;
        }

        long seconds = Duration.between(now, reservation.getExpiresAt()).getSeconds();
        return (int) Math.max(0, seconds);
    }

    /**
     * Resolve TicketReservation.currency - default currency for the reservation.
     *
     * @param dfe DataFetchingEnvironment
     * @return Currency code (defaults to ZMW)
     */
    @DgsData(parentType = "TicketReservation", field = "currency")
    public String currency(DgsDataFetchingEnvironment dfe) {
        return DEFAULT_CURRENCY;
    }

    /**
     * Resolve TicketReservation.promoCodeApplied - maps from promoCode field.
     *
     * @param dfe DataFetchingEnvironment containing the parent TicketReservation
     * @return Promo code if applied, null otherwise
     */
    @DgsData(parentType = "TicketReservation", field = "promoCodeApplied")
    public String promoCodeApplied(DgsDataFetchingEnvironment dfe) {
        TicketReservation reservation = dfe.getSource();
        return reservation.getPromoCode();
    }

    /**
     * Resolve TicketReservation.updatedAt - audit field.
     * Maps to convertedAt for converted reservations or createdAt otherwise.
     *
     * @param dfe DataFetchingEnvironment containing the parent TicketReservation
     * @return Updated timestamp
     */
    @DgsData(parentType = "TicketReservation", field = "updatedAt")
    public LocalDateTime updatedAt(DgsDataFetchingEnvironment dfe) {
        TicketReservation reservation = dfe.getSource();
        return reservation.getConvertedAt() != null
                ? reservation.getConvertedAt()
                : reservation.getCreatedAt();
    }
}
