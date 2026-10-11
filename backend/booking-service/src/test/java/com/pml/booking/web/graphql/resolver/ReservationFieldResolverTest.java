package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.TicketReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The countdown on a held reservation. It once declared a resolver parameter DGS cannot fill, so it
 * failed every reserve response in production while nothing in the suite called it. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("TicketReservation.remainingSeconds resolves from the source alone")
class ReservationFieldResolverTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private final ReservationFieldResolver resolver = new ReservationFieldResolver(Clock.fixed(NOW, ZoneOffset.UTC));

    private Integer remaining(TicketReservation reservation) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(reservation);
        return resolver.remainingSeconds(dfe);
    }

    @Test
    @DisplayName("a hold with time left reports the seconds, and an expired one reports zero")
    void countsDownAndFloorsAtZero() {
        TicketReservation live = new TicketReservation();
        live.setExpiresAt(NOW.plusSeconds(300));
        TicketReservation expired = new TicketReservation();
        expired.setExpiresAt(NOW.minusSeconds(5));

        assertThat(remaining(live)).isEqualTo(300);
        assertThat(remaining(expired)).isZero();
        assertThat(remaining(new TicketReservation())).as("no expiry set").isZero();
    }

    @Test
    @DisplayName("the resolver takes only the environment: DGS cannot supply anything else")
    void takesOnlyTheEnvironment() {
        assertThat(Arrays.stream(ReservationFieldResolver.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("remainingSeconds"))
                .map(method -> Arrays.asList(method.getParameterTypes())))
                .containsExactly(Arrays.asList(DgsDataFetchingEnvironment.class));
    }
}
