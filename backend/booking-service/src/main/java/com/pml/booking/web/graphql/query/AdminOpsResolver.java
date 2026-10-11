package com.pml.booking.web.graphql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.DualControlRules;
import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.enums.RecoveryProposalStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.PlatformTransfer;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.service.AdminAudit;
import com.pml.booking.service.AdminFinanceReads;
import com.pml.booking.service.ChargebackRecoveryOps;
import com.pml.booking.service.DualControlService;
import com.pml.booking.service.PaymentOperations;
import com.pml.booking.service.PaymentRiskService;
import com.pml.booking.service.PlatformTransferService;
import com.pml.booking.web.graphql.dto.CommissionFilterInput;
import com.pml.booking.web.graphql.dto.GatewaySettlementFilterInput;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaymentAttemptFilterInput;
import com.pml.booking.web.graphql.dto.PaymentAttemptOffsetPage;
import com.pml.booking.web.graphql.dto.PlatformTransferInput;
import com.pml.booking.web.graphql.dto.ProposeRecoveryActionInput;
import com.pml.booking.web.graphql.dto.UpdateChargebackRecoveryInput;
import com.pml.shared.idempotency.Fingerprint;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Finance and payment operations for platform staff: the lists they work from, the recovery tools, and
 * the two-person actions. Every mutation here leaves an audit line; the actions that move platform
 * money or destroy a record go through {@link DualControlService}.
 */
@DgsComponent
@Validated
@FailClosedOnRevocation
@RequiredArgsConstructor
public class AdminOpsResolver {

    private final AdminFinanceReads reads;
    private final PaymentOperations payments;
    private final PaymentRiskService risk;
    private final DualControlService dualControl;
    private final PlatformTransferService transfers;
    private final ChargebackRecoveryOps chargebackRecovery;
    private final Clock clock;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper mapper;

    public record PlatformTransferResult(boolean executed, boolean requiresSecondApprover, PlatformTransfer transfer,
                                         RecoveryProposal proposal) {
    }

    // ---- lists ---------------------------------------------------------------------------------

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<AdminFinanceReads.CommissionPage> commissionRecords(@Valid @InputArgument CommissionFilterInput filter,
                                                                    @Valid @InputArgument OffsetPaginationInput pagination) {
        return reads.commissions(filter == null ? null : new AdminFinanceReads.CommissionFilter(filter.status(), filter.eventId(),
                filter.organizationId(), filter.ticketId(), filter.createdAfter(), filter.createdBefore()), pagination);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<AdminFinanceReads.SettlementPage> gatewaySettlements(@Valid @InputArgument GatewaySettlementFilterInput filter,
                                                                     @Valid @InputArgument OffsetPaginationInput pagination) {
        return reads.settlements(filter == null ? null
                : new AdminFinanceReads.SettlementFilter(filter.from(), filter.to(), filter.settlementId()), pagination);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PaymentAttemptOffsetPage> paymentAttemptSearch(@Valid @InputArgument PaymentAttemptFilterInput filter,
                                                               @Valid @InputArgument OffsetPaginationInput pagination) {
        return payments.search(filter, pagination).map(slice -> new PaymentAttemptOffsetPage(slice.data(), slice.pagination()));
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PaymentAttemptOffsetPage> stuckTransactions(@InputArgument Integer minutes,
                                                            @Valid @InputArgument OffsetPaginationInput pagination) {
        return payments.stuck(minutes, pagination).map(slice -> new PaymentAttemptOffsetPage(slice.data(), slice.pagination()));
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PaymentRiskService.Summary> paymentRiskSummary(@InputArgument Integer windowHours) {
        int window = windowHours == null ? 24 : Math.max(1, Math.min(windowHours, 24 * 30));
        return risk.summary(window);
    }

    // ---- payment recovery ----------------------------------------------------------------------

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PaymentAttempt> resumePaymentAttempt(@InputArgument String depositId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> payments.resume(depositId)
                .doOnSuccess(attempt -> AdminAudit.record("PAYMENT_ATTEMPT_RESUMED", actor, "PAYMENT_ATTEMPT", depositId,
                        attempt == null ? "" : "status=" + attempt.getStatus())));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<List<PaymentOperations.Outcome>> retryPaymentAttempts(@InputArgument List<String> depositIds) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> payments.retryMany(depositIds)
                .doOnSuccess(outcomes -> AdminAudit.record("PAYMENT_ATTEMPTS_RETRIED", actor, "PAYMENT_ATTEMPT",
                        String.join(",", depositIds), outcomes.size() + " outcomes")));
    }

    /**
     * Proposes completing stuck collections by applying the provider's confirmed answer; a different
     * super administrator must confirm it. Nothing is completed on the strength of this call.
     */
    @DgsMutation
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Mono<RecoveryProposal> forceCompletePaymentAttempts(@InputArgument List<String> depositIds, @InputArgument String reason) {
        return dualControl.propose(RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS, depositIds, null, Map.of(), reason)
                .doOnSuccess(proposal -> audit("RECOVERY_PROPOSED", proposal));
    }

    // ---- two-person actions --------------------------------------------------------------------

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Flux<RecoveryProposal> dualControlQueue() {
        return dualControl.queue();
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Flux<RecoveryProposal> myRecoveryProposals() {
        return dualControl.mine();
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Flux<RecoveryProposal> pendingRecoveryProposals() {
        return dualControl.pending();
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RecoveryProposal> proposeRecoveryAction(@Valid @InputArgument ProposeRecoveryActionInput input) {
        return dualControl.propose(input.action(), input.subjectIds(), input.amount(), input.parameters(), input.reason())
                .doOnSuccess(proposal -> audit("RECOVERY_PROPOSED", proposal));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RecoveryProposal> confirmRecoveryAction(@InputArgument String proposalId, @InputArgument String reason) {
        return dualControl.confirm(proposalId, reason).doOnSuccess(proposal -> audit("RECOVERY_CONFIRMED", proposal));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RecoveryProposal> withdrawRecoveryProposal(@InputArgument String proposalId) {
        return dualControl.withdraw(proposalId).doOnSuccess(proposal -> audit("RECOVERY_WITHDRAWN", proposal));
    }

    /**
     * Moves the platform's money between its operating and reserve accounts. Up to the single-approver limit
     * it happens now; above it, it becomes a proposal and waits for a second person.
     *
     * <p>Runs under the shared {@link IdempotencyGuard}: a retry with the same key and the same body gets the
     * first answer back (the same transfer, or the same proposal) and moves nothing, and the same key with a
     * different body is refused rather than answered with a transfer of another sum. The transfer record's own
     * key stays beneath it as the last line.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PlatformTransferResult> transferBetweenPlatformAccounts(@Valid @InputArgument PlatformTransferInput input) {
        var malformed = PlatformTransferService.check(input.fromAccount(), input.toAccount(), input.amount(), input.reason());
        if (malformed != null) {
            return Mono.error(malformed);
        }
        String fingerprint = Fingerprint.of(mapper, input, Fingerprint.CLIENT_VARYING);
        return idempotencyGuard.execute("booking:transferBetweenPlatformAccounts", input.idempotencyKey(), fingerprint,
                PlatformTransferResult.class, () -> transferOnce(input));
    }

    private Mono<PlatformTransferResult> transferOnce(PlatformTransferInput input) {
        if (transfers.needsSecondPerson(input.amount())) {
            return dualControl.proposeTransfer(input.fromAccount(), input.toAccount(), input.amount(), input.reason())
                    .doOnSuccess(proposal -> audit("RECOVERY_PROPOSED", proposal))
                    .map(proposal -> new PlatformTransferResult(false, true, null, proposal));
        }
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> transfers.execute(input.fromAccount(), input.toAccount(),
                        input.amount(), input.reason(), input.idempotencyKey(), actor, null)
                .doOnSuccess(transfer -> AdminAudit.record("PLATFORM_TRANSFER", actor, "PLATFORM_ACCOUNT", transfer.getId(),
                        transfer.getFromAccount() + "->" + transfer.getToAccount() + " " + transfer.getAmount()))
                .map(transfer -> new PlatformTransferResult(true, false, transfer, null)));
    }

    // ---- chargebacks ---------------------------------------------------------------------------

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ChargebackRecord> updateChargebackRecovery(@InputArgument String id, @Valid @InputArgument UpdateChargebackRecoveryInput input) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> chargebackRecovery.apply(id, input.action(),
                        input.amount(), input.fundSource(), input.reference(), actor)
                .doOnSuccess(record -> AdminAudit.record("CHARGEBACK_RECOVERY_" + input.action(), actor, "CHARGEBACK", id,
                        "amount=" + input.amount() + " source=" + input.fundSource())));
    }

    // ---- field resolvers -----------------------------------------------------------------------

    @DgsData(parentType = "PaymentAttempt", field = "riskScore")
    public Mono<Integer> riskScore(DgsDataFetchingEnvironment dfe) {
        return risk.assess(dfe.getSource()).map(PaymentAttempt::getRiskScore);
    }

    @DgsData(parentType = "PaymentAttempt", field = "riskLevel")
    public Mono<String> riskLevel(DgsDataFetchingEnvironment dfe) {
        return risk.assess(dfe.getSource()).map(PaymentAttempt::getRiskLevel);
    }

    @DgsData(parentType = "PaymentAttempt", field = "riskFlags")
    public Mono<List<String>> riskFlags(DgsDataFetchingEnvironment dfe) {
        return risk.assess(dfe.getSource()).map(attempt -> attempt.getRiskFlags() == null ? List.<String>of() : attempt.getRiskFlags());
    }

    @DgsData(parentType = "RecoveryProposal", field = "status")
    public RecoveryProposalStatus proposalStatus(DgsDataFetchingEnvironment dfe) {
        RecoveryProposal proposal = dfe.getSource();
        return proposal.statusAt(clock.instant());
    }

    /** Whether the caller could confirm this proposal right now. */
    @DgsData(parentType = "RecoveryProposal", field = "canConfirm")
    public Mono<Boolean> canConfirm(DgsDataFetchingEnvironment dfe) {
        RecoveryProposal proposal = dfe.getSource();
        return SecurityContextUtils.getCurrentUserId().flatMap(me -> OrganizerAccess.authorities()
                        .map(authorities -> DualControlRules.checkConfirmation(proposal, me, authorities, clock.instant()) == null))
                .defaultIfEmpty(false);
    }

    private static void audit(String action, RecoveryProposal proposal) {
        if (proposal != null) {
            AdminAudit.record(action, proposal.getConfirmedById() != null ? proposal.getConfirmedById() : proposal.getProposedById(),
                    "RECOVERY_PROPOSAL", proposal.getId(), proposal.getAction() + " status=" + proposal.getStatus());
        }
    }
}
