package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.web.graphql.dto.DisputeChargebackInput;
import com.pml.booking.web.graphql.dto.ReceiveChargebackInput;
import com.pml.booking.workflow.chargeback.ChargebackProcess;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * GraphQL mutations for chargebacks.
 *
 * <p>Receipt and every decision reach the chargeback's workflow through {@link ChargebackProcess},
 * which owns the response deadline, the dispute count on the escrow and the recovery waterfall —
 * organizer escrow, future payouts, platform reserve, then write-off. No mutation moves money on a
 * chargeback outside that workflow. The actor is the authenticated caller.
 */
@Slf4j
@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class ChargebackMutationResolver {

    private final ChargebackProcess chargebackProcess;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> receiveChargeback(@Valid @InputArgument ReceiveChargebackInput input) {
        log.info("GraphQL mutation: receiveChargeback(chargebackId={})", input.chargebackId());
        return chargebackProcess.receive(input);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> startChargebackReview(@InputArgument String id, @InputArgument(name = "notes") String notes) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(reviewedBy -> chargebackProcess.startReview(id, reviewedBy, notes != null ? notes : "Review initiated"));
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> acceptChargeback(@InputArgument String id, @InputArgument(name = "reason") String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(acceptedBy -> chargebackProcess.accept(id, acceptedBy, reason != null ? reason : "Accepted by admin"));
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> disputeChargeback(@InputArgument String id, @Valid @InputArgument DisputeChargebackInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(disputedBy -> chargebackProcess.dispute(id, disputedBy, input));
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> recordChargebackOutcome(@InputArgument String id,
                                                          @InputArgument Boolean won,
                                                          @InputArgument(name = "notes") String notes) {
        boolean disputeWon = Boolean.TRUE.equals(won);
        String outcomeNotes = notes != null ? notes : (disputeWon ? "Dispute won" : "Dispute lost");
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> chargebackProcess.recordOutcome(id, actorId, disputeWon, outcomeNotes));
    }
}
