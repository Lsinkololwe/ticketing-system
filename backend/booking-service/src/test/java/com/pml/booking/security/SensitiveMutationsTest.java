package com.pml.booking.security;

import com.netflix.graphql.dgs.DgsMutation;
import com.pml.booking.web.graphql.mutation.BankAccountMutationResolver;
import com.pml.booking.web.graphql.mutation.ChargebackMutationResolver;
import com.pml.booking.web.graphql.mutation.ChartOfAccountsMutationResolver;
import com.pml.booking.web.graphql.mutation.CheckInMutationResolver;
import com.pml.booking.web.graphql.mutation.EscrowMutationResolver;
import com.pml.booking.web.graphql.mutation.JournalEntryMutationResolver;
import com.pml.booking.web.graphql.mutation.PaymentAttemptMutationResolver;
import com.pml.booking.web.graphql.mutation.PayoutRequestMutationResolver;
import com.pml.booking.web.graphql.mutation.ReconciliationMutationResolver;
import com.pml.booking.web.graphql.mutation.RefundRequestMutationResolver;
import com.pml.booking.web.graphql.mutation.ReservationMutationResolver;
import com.pml.booking.web.graphql.mutation.ReservationPaymentMutationResolver;
import com.pml.booking.web.graphql.mutation.TicketMutationResolver;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every booking mutation that moves money, validates a ticket or recovers a transaction refuses to
 * run when the caller's token cannot be checked for revocation.
 *
 * <p>The list is written out rather than inferred, so a new mutation on one of these resolvers is
 * covered by the class, and a new sensitive resolver has to be added here on purpose.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("Booking's money-moving and check-in mutations fail closed on revocation")
class SensitiveMutationsTest {

    private static final List<Class<?>> SENSITIVE_RESOLVERS = List.of(
            BankAccountMutationResolver.class, ChargebackMutationResolver.class,
            ChartOfAccountsMutationResolver.class, CheckInMutationResolver.class,
            EscrowMutationResolver.class, JournalEntryMutationResolver.class,
            PaymentAttemptMutationResolver.class, PayoutRequestMutationResolver.class,
            ReconciliationMutationResolver.class, RefundRequestMutationResolver.class,
            TicketMutationResolver.class, ReservationPaymentMutationResolver.class);

    private static final Set<String> SENSITIVE_METHODS = Set.of("forceExpireReservation");

    private static Stream<Method> mutations(Class<?> resolver) {
        return Arrays.stream(resolver.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(DgsMutation.class));
    }

    @Test
    @DisplayName("each sensitive resolver carries the annotation, so all its mutations are covered")
    void resolversAreMarked() {
        assertThat(SENSITIVE_RESOLVERS)
                .allSatisfy(resolver -> assertThat(resolver.isAnnotationPresent(FailClosedOnRevocation.class))
                        .as(resolver.getSimpleName()).isTrue());
        assertThat(SENSITIVE_RESOLVERS.stream().mapToLong(r -> mutations(r).count()).sum())
                .as("mutations covered by class-level marking").isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("the operator release of a hold is marked on its own")
    void operatorReleaseIsMarked() {
        assertThat(mutations(ReservationMutationResolver.class)
                .filter(m -> SENSITIVE_METHODS.contains(m.getName())))
                .singleElement()
                .satisfies(m -> assertThat(m.isAnnotationPresent(FailClosedOnRevocation.class)).isTrue());
    }

    @Test
    @DisplayName("a customer's own hold and cancel stay ordinary: an outage must not stop checkout")
    void checkoutIsNotFailClosed() {
        assertThat(ReservationMutationResolver.class.isAnnotationPresent(FailClosedOnRevocation.class)).isFalse();
    }
}
