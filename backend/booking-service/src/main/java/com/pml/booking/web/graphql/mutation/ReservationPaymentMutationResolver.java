package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.PayReservationInput;
import com.pml.booking.web.graphql.dto.PaymentInitiationResponse;
import com.pml.booking.workflow.purchase.PurchaseProcess;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * Starts the mobile-money prompt for a held reservation.
 *
 * <h2>Asking to be charged is not asserting you paid</h2>
 * This creates a payment intent and asks the provider to prompt the buyer's handset. Whether the
 * money arrives is decided by the provider and verified by the callback path; nothing this mutation is told
 * causes a ticket to be issued.
 *
 * <h2>Why the response has no tickets in it</h2>
 * There are none yet. A mobile-money confirmation takes between eight seconds and four minutes and
 * fails about one time in six. The client polls the reservation, which moves to {@code CONFIRMED}
 * when, and only when, the money has arrived and the tickets exist.
 */
@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class ReservationPaymentMutationResolver {

    private final PurchaseProcess purchaseProcess;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<PaymentInitiationResponse> payReservation(@Valid @InputArgument PayReservationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> purchaseProcess.pay(userId, input));
    }
}
