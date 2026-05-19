package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.web.graphql.dto.ChargebackMutationResponse;
import com.pml.booking.web.graphql.dto.DisputeChargebackInput;
import com.pml.booking.web.graphql.dto.ReceiveChargebackInput;
import com.pml.booking.web.graphql.dto.RecoverChargebackInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Chargeback Operations.
 *
 * <p>Provides mutations for managing the chargeback lifecycle.
 * Chargebacks are involuntary refunds initiated by customers through
 * their payment provider.</p>
 *
 * <h2>Chargeback Lifecycle</h2>
 * <ol>
 *   <li>RECEIVE: Chargeback received from payment provider</li>
 *   <li>REVIEW: Platform reviews the chargeback</li>
 *   <li>ACCEPT/DISPUTE: Decision made on response</li>
 *   <li>OUTCOME: Win or lose the dispute</li>
 *   <li>RECOVER: Recover funds if lost</li>
 * </ol>
 *
 * <h2>Recovery Waterfall</h2>
 * <ol>
 *   <li>ORGANIZER_ESCROW: Deduct from organizer's current escrow</li>
 *   <li>ORGANIZER_FUTURE: Hold against future payouts</li>
 *   <li>PLATFORM_RESERVE: Use platform reserve funds</li>
 *   <li>WRITE_OFF: Write off as bad debt</li>
 * </ol>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ChargebackMutationResolver {

    private final ChargebackService chargebackService;

    /**
     * Receive a chargeback from the payment provider.
     * Schema: receiveChargeback(input: ReceiveChargebackInput!): ChargebackMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackMutationResponse> receiveChargeback(
            @InputArgument ReceiveChargebackInput input
    ) {
        log.info("GraphQL mutation: receiveChargeback(chargebackId={})", input.chargebackId());

        return chargebackService.receiveChargeback(
                        input.chargebackId(),
                        input.originalTransactionId(),
                        input.ticketId(),
                        input.eventId(),
                        input.organizerId(),
                        input.organizationId(),
                        input.customerId(),
                        input.originalAmount(),
                        input.chargebackAmount(),
                        input.chargebackFee(),
                        input.currency(),
                        input.reason(),
                        input.responseDeadline(),
                        java.util.Collections.emptyMap() // gatewayMetadata
                )
                .map(chargeback -> ChargebackMutationResponse.success(
                        "Chargeback " + input.chargebackId() + " received successfully", chargeback))
                .onErrorResume(e -> {
                    log.error("Failed to receive chargeback {}: {}", input.chargebackId(), e.getMessage());
                    return Mono.just(ChargebackMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Start review of a chargeback.
     * Schema: startChargebackReview(id: ID!): ChargebackMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackMutationResponse> startChargebackReview(
            @InputArgument String id,
            @InputArgument(name = "notes") String notes
    ) {
        log.info("GraphQL mutation: startChargebackReview({})", id);

        // TODO: Get authenticated user from security context
        String reviewedBy = "system";

        return chargebackService.startReview(id, reviewedBy, notes != null ? notes : "Review initiated")
                .map(chargeback -> ChargebackMutationResponse.success(
                        "Chargeback review started", chargeback))
                .onErrorResume(e -> {
                    log.error("Failed to start chargeback review {}: {}", id, e.getMessage());
                    return Mono.just(ChargebackMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Accept a chargeback (no dispute).
     * Schema: acceptChargeback(id: ID!): ChargebackMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackMutationResponse> acceptChargeback(
            @InputArgument String id,
            @InputArgument(name = "reason") String reason
    ) {
        log.info("GraphQL mutation: acceptChargeback({})", id);

        // TODO: Get authenticated user from security context
        String acceptedBy = "system";

        return chargebackService.acceptChargeback(id, acceptedBy, reason != null ? reason : "Accepted by admin")
                .map(chargeback -> ChargebackMutationResponse.success(
                        "Chargeback accepted, recovery process initiated", chargeback))
                .onErrorResume(e -> {
                    log.error("Failed to accept chargeback {}: {}", id, e.getMessage());
                    return Mono.just(ChargebackMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Dispute a chargeback with evidence.
     * Schema: disputeChargeback(id: ID!, input: DisputeChargebackInput!): ChargebackMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackMutationResponse> disputeChargeback(
            @InputArgument String id,
            @InputArgument DisputeChargebackInput input
    ) {
        log.info("GraphQL mutation: disputeChargeback(id={})", id);

        // TODO: Get authenticated user from security context
        String disputedBy = "system";

        // Build evidence bundle
        ChargebackService.DisputeEvidence evidenceBundle = new ChargebackService.DisputeEvidence(
                input.ticketValidationProof(),
                input.customerCommunicationLog(),
                input.deliveryConfirmation(),
                input.termsAcceptanceProof(),
                input.additionalDocuments(),
                java.util.Collections.emptyMap()
        );

        return chargebackService.disputeChargeback(id, disputedBy, evidenceBundle, input.notes())
                .map(chargeback -> ChargebackMutationResponse.success(
                        "Chargeback disputed with evidence", chargeback))
                .onErrorResume(e -> {
                    log.error("Failed to dispute chargeback {}: {}", id, e.getMessage());
                    return Mono.just(ChargebackMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Record the outcome of a chargeback dispute.
     * Schema: recordChargebackOutcome(id: ID!, won: Boolean!, notes: String): ChargebackMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackMutationResponse> recordChargebackOutcome(
            @InputArgument String id,
            @InputArgument Boolean won,
            @InputArgument(name = "notes") String notes
    ) {
        log.info("GraphQL mutation: recordChargebackOutcome(id={}, won={})", id, won);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String outcomeNotes = notes != null ? notes : (won ? "Dispute won" : "Dispute lost");

        Mono<ChargebackRecord> outcome = won
                ? chargebackService.recordWin(id, now, outcomeNotes)
                : chargebackService.recordLoss(id, now, outcomeNotes);

        return outcome
                .map(chargeback -> {
                    String message = won
                            ? "Chargeback dispute won, funds retained"
                            : "Chargeback dispute lost, recovery process initiated";
                    return ChargebackMutationResponse.success(message, chargeback);
                })
                .onErrorResume(e -> {
                    log.error("Failed to record chargeback outcome {}: {}", id, e.getMessage());
                    return Mono.just(ChargebackMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Recover funds for a lost chargeback.
     * Schema: recoverChargebackFunds(id: ID!, input: RecoverChargebackInput!): ChargebackMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackMutationResponse> recoverChargebackFunds(
            @InputArgument String id,
            @InputArgument RecoverChargebackInput input
    ) {
        log.info("GraphQL mutation: recoverChargebackFunds(id={}, source={})", id, input.fundSource());

        return chargebackService.recoverFromSource(id, input.fundSource(), input.amount())
                .map(chargeback -> ChargebackMutationResponse.success(
                        "Chargeback recovery completed via " + input.fundSource(), chargeback))
                .onErrorResume(e -> {
                    log.error("Failed to recover chargeback funds {}: {}", id, e.getMessage());
                    return Mono.just(ChargebackMutationResponse.error(e.getMessage()));
                });
    }
}
