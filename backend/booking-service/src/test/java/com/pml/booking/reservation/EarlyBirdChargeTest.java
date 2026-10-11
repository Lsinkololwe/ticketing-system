package com.pml.booking.reservation;

import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.dto.InventoryReservationResult;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.service.impl.ReservationServiceImpl;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.web.graphql.dto.TicketSelectionInput;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The reservation charges the price the storefront advertised, at the two minutes that
 * decide it.
 *
 * <p>Catalog advertises through {@code TicketTier.getCurrentPrice}, which is early-bird aware and
 * pinned by catalog's {@code EarlyBirdPricingTest}. This is the other half: the quote booking writes
 * onto the reservation, from the mirrored tier, against booking's injected clock. One assertion
 * either side of the boundary, because only the pair catches an implementation that always or never
 * discounts.
 */
@Tag("L1")
@Tag("ET-TKT-001")
@DisplayName("F-023 · a reservation is priced at the early-bird rate until the window closes")
class EarlyBirdChargeTest {

    private static final Instant WINDOW_CLOSES = Instant.parse("2026-09-10T23:59:59Z");
    private static final BigDecimal FULL = new BigDecimal("200.00");
    private static final BigDecimal EARLY = new BigDecimal("150.00");

    @Test
    @DisplayName("a minute before the window closes, the reservation is quoted at the early-bird price")
    void aMinuteBefore() {
        TicketReservation held = reserveTwoAt(TestClock.frozenAt(WINDOW_CLOSES.minusSeconds(60)));

        assertThat(held.getItems().get(0).getUnitPrice()).isEqualByComparingTo(EARLY);
        assertThat(held.getTotalAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("a minute after, it is quoted at the full price")
    void aMinuteAfter() {
        TicketReservation held = reserveTwoAt(TestClock.frozenAt(WINDOW_CLOSES.plusSeconds(60)));

        assertThat(held.getItems().get(0).getUnitPrice()).isEqualByComparingTo(FULL);
        assertThat(held.getTotalAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("at the closing instant itself, the full price applies")
    void atTheBoundary() {
        TicketReservation held = reserveTwoAt(TestClock.frozenAt(WINDOW_CLOSES));

        assertThat(held.getItems().get(0).getUnitPrice()).isEqualByComparingTo(FULL);
    }

    @Test
    @DisplayName("a tier with no early-bird window is quoted at the full price")
    void noWindow() {
        EventSummaryDto.TicketCategoryDto tier = EventSummaryDto.TicketCategoryDto.builder()
                .code("GA").price(FULL).build();

        assertThat(tier.priceAt(WINDOW_CLOSES.minusSeconds(3600))).isEqualByComparingTo(FULL);
    }

    private static TicketReservation reserveTwoAt(TestClock clock) {
        TicketReservationRepository reservations = mock(TicketReservationRepository.class);
        CatalogServiceClient catalog = mock(CatalogServiceClient.class);

        EventSummaryDto event = EventSummaryDto.builder()
                .id("evt-1")
                .organizerId("org-user-1")
                .organizationId("org-1")
                .ticketCategories(List.of(EventSummaryDto.TicketCategoryDto.builder()
                        .code("GA")
                        .name("General")
                        .price(FULL)
                        .earlyBirdPrice(EARLY)
                        .earlyBirdEndsAt(WINDOW_CLOSES)
                        .capacity(100)
                        .sold(0)
                        .active(true)
                        .build()))
                .build();

        when(reservations.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(reservations.findByUserIdAndStatus(anyString(), any())).thenReturn(Flux.empty());
        when(reservations.save(any(TicketReservation.class)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(catalog.getEventById("evt-1")).thenReturn(Mono.just(event));
        when(catalog.reserveInventory(anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(new InventoryReservationResult(true, null, "GA")));

        ReservationServiceImpl service = new ReservationServiceImpl(
                reservations, clock, catalog, mock(PurchaseService.class),
                com.pml.shared.testing.IdempotencyPassthrough.guard(), new com.fasterxml.jackson.databind.ObjectMapper());

        return service.createReservation("buyer-1",
                        new ReserveTicketsInput("evt-1", List.of(new TicketSelectionInput("GA", 2)), null, "key-1", null, null, null),
                        "res-1")
                .block();
    }
}
