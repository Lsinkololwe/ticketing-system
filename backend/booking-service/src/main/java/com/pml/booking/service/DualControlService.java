package com.pml.booking.service;

import com.pml.booking.domain.DualControlRules;
import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.enums.RecoveryProposalStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.booking.security.OrganizerAccess;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Maker-checker for the actions that move platform money or destroy a record.
 *
 * <p>One person proposes with a reason; a different person holding the right role confirms with a
 * reason of their own, within two hours. The proposal stores exactly what will be done, and confirming
 * applies exactly that. A confirmed action that is then refused applies nothing and leaves the proposal
 * {@code FAILED}, so a refused write-off is never half done and a second attempt is a new proposal.
 */
@Service
public class DualControlService {

    private final ReactiveMongoTemplate template;
    private final Clock clock;
    private final PaymentOutcomeService outcomes;
    private final ChargebackService chargebacks;
    private final PlatformTransferService transfers;

    public DualControlService(ReactiveMongoTemplate template, Clock clock, PaymentOutcomeService outcomes,
                              ChargebackService chargebacks, PlatformTransferService transfers) {
        this.template = template;
        this.clock = clock;
        this.outcomes = outcomes;
        this.chargebacks = chargebacks;
        this.transfers = transfers;
    }

    // ---- propose -------------------------------------------------------------------------------

    public Mono<RecoveryProposal> propose(RecoveryAction action, List<String> subjectIds, BigDecimal amount,
                                          Map<String, String> parameters, String reason) {
        DomainRefusal unreasoned = DualControlRules.checkReason(reason, "a proposal");
        if (unreasoned != null) {
            return Mono.error(unreasoned);
        }
        if (action == null || subjectIds == null || subjectIds.isEmpty() || subjectIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a proposal names what it acts on"));
        }
        if (action == RecoveryAction.WRITE_OFF_CHARGEBACK && (subjectIds.size() != 1 || amount == null || amount.signum() <= 0)) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a write-off names one chargeback and a positive amount"));
        }
        if (action == RecoveryAction.TRANSFER_PLATFORM_FUNDS) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "propose a platform transfer through transferBetweenPlatformAccounts"));
        }
        return SecurityContextUtils.requireCurrentUserId().flatMap(proposer -> OrganizerAccess.authorities().flatMap(authorities -> {
            if (!DualControlRules.holdsRole(authorities, action)) {
                return Mono.<RecoveryProposal>error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                        "proposing " + action + " needs the " + action.requiredRole() + " role"));
            }
            return store(action, subjectIds, amount, parameters, reason, proposer);
        }));
    }

    /** Proposes a platform transfer; used by the transfer entry point once it knows a second person is needed. */
    public Mono<RecoveryProposal> proposeTransfer(PlatformAccountType from, PlatformAccountType to, BigDecimal amount, String reason) {
        TranslatedRefusal malformed = PlatformTransferService.check(from, to, amount, reason);
        if (malformed != null) {
            return Mono.error(malformed);
        }
        return SecurityContextUtils.requireCurrentUserId().flatMap(proposer -> OrganizerAccess.authorities().flatMap(authorities -> {
            if (!DualControlRules.holdsRole(authorities, RecoveryAction.TRANSFER_PLATFORM_FUNDS)) {
                return Mono.<RecoveryProposal>error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                        "proposing a platform transfer needs the FINANCE role"));
            }
            DomainRefusal unreasoned = DualControlRules.checkReason(reason, "a transfer above the single-approver limit");
            if (unreasoned != null) {
                return Mono.<RecoveryProposal>error(unreasoned);
            }
            return store(RecoveryAction.TRANSFER_PLATFORM_FUNDS, List.of(from.name(), to.name()), amount, Map.of(), reason, proposer);
        }));
    }

    private Mono<RecoveryProposal> store(RecoveryAction action, List<String> subjectIds, BigDecimal amount,
                                         Map<String, String> parameters, String reason, String proposer) {
        Instant now = clock.instant();
        return template.insert(RecoveryProposal.builder()
                .action(action)
                .subjectType(action.subjectType())
                .subjectIds(new ArrayList<>(new LinkedHashSet<>(subjectIds)))
                .amount(amount)
                .parameters(parameters == null ? Map.of() : parameters)
                .status(RecoveryProposalStatus.PENDING)
                .proposedById(proposer)
                .proposedAt(now)
                .proposalReason(reason.trim())
                .expiresAt(now.plus(DualControlRules.PROPOSAL_TTL))
                .createdAt(now)
                .build());
    }

    // ---- confirm -------------------------------------------------------------------------------

    public Mono<RecoveryProposal> confirm(String proposalId, String reason) {
        DomainRefusal unreasoned = DualControlRules.checkReason(reason, "a confirmation");
        if (unreasoned != null) {
            return Mono.error(unreasoned);
        }
        return SecurityContextUtils.requireCurrentUserId().flatMap(confirmer -> OrganizerAccess.authorities().flatMap(authorities ->
                load(proposalId).flatMap(proposal -> {
                    Instant now = clock.instant();
                    DomainRefusal refusal = DualControlRules.checkConfirmation(proposal, confirmer, authorities, now);
                    if (refusal != null) {
                        return Mono.<RecoveryProposal>error(refusal);
                    }
                    // Exactly one confirmer wins the move out of PENDING; the action runs once, for them.
                    return template.findAndModify(
                                    Query.query(Criteria.where("_id").is(proposalId).and("status").is(RecoveryProposalStatus.PENDING)
                                            .and("expiresAt").gt(now)),
                                    new Update().set("status", RecoveryProposalStatus.CONFIRMED).set("confirmedById", confirmer)
                                            .set("confirmedAt", now).set("confirmationReason", reason.trim()).inc("version", 1),
                                    FindAndModifyOptions.options().returnNew(true), RecoveryProposal.class)
                            .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                                    "the proposal was decided while you were confirming it", Map.of("currentStatus", "DECIDED"))))
                            .flatMap(confirmed -> run(confirmed, confirmer)
                                    .flatMap(outcome -> template.findAndModify(
                                            Query.query(Criteria.where("_id").is(proposalId)),
                                            new Update().set("outcome", outcome).inc("version", 1),
                                            FindAndModifyOptions.options().returnNew(true), RecoveryProposal.class))
                                    .onErrorResume(failure -> template.findAndModify(
                                                    Query.query(Criteria.where("_id").is(proposalId)),
                                                    new Update().set("status", RecoveryProposalStatus.FAILED)
                                                            .set("failureReason", failureText(failure)).inc("version", 1),
                                                    FindAndModifyOptions.options().returnNew(true), RecoveryProposal.class)));
                })));
    }

    private Mono<String> run(RecoveryProposal proposal, String confirmer) {
        return switch (proposal.getAction()) {
            case FORCE_COMPLETE_PAYMENT_ATTEMPTS -> forceComplete(proposal);
            case WRITE_OFF_CHARGEBACK -> chargebacks.writeOff(proposal.getSubjectIds().get(0), proposal.getAmount(), confirmer,
                            proposal.getProposalReason())
                    .map(record -> "Wrote off " + proposal.getAmount() + " of chargeback " + record.getChargebackId());
            case TRANSFER_PLATFORM_FUNDS -> transfers.execute(PlatformAccountType.valueOf(proposal.getSubjectIds().get(0)),
                            PlatformAccountType.valueOf(proposal.getSubjectIds().get(1)), proposal.getAmount(),
                            proposal.getProposalReason(), "proposal:" + proposal.getId(), confirmer, proposal.getId())
                    .map(transfer -> "Moved " + transfer.getAmount() + " from " + transfer.getFromAccount() + " to " + transfer.getToAccount());
        };
    }

    /**
     * Applies the provider's own answer to each stuck attempt and finishes the purchase it implies. It never
     * invents an outcome: an attempt the provider has not confirmed stays as it is, and a proposal in which
     * none of the attempts could be completed fails rather than reporting a success it did not have.
     */
    private Mono<String> forceComplete(RecoveryProposal proposal) {
        return Flux.fromIterable(proposal.getSubjectIds())
                .concatMap(depositId -> template.findOne(Query.query(Criteria.where("depositId").is(depositId)), PaymentAttempt.class)
                        .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                                "no payment attempt " + depositId)))
                        .flatMap(attempt -> outcomes.verifyAndApply(depositId)
                                .then(attempt.getReservationId() == null ? Mono.empty() : outcomes.resume(attempt.getReservationId()))
                                .then(template.findOne(Query.query(Criteria.where("depositId").is(depositId)), PaymentAttempt.class))
                                .map(after -> after.isFulfilled() || after.getStatus() == com.pml.booking.domain.enums.PaymentAttemptStatus.COMPLETED))
                        .onErrorReturn(false))
                .collectList()
                .flatMap(results -> {
                    long completed = results.stream().filter(Boolean::booleanValue).count();
                    if (completed == 0) {
                        return Mono.error(new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                                "the provider has not confirmed any of these payments, so none was completed"));
                    }
                    return Mono.just("Completed " + completed + " of " + results.size() + " payment attempts");
                });
    }

    private static String failureText(Throwable failure) {
        String text = failure instanceof DomainRefusal refusal ? refusal.errorCode() + ": " + refusal.getMessage() : String.valueOf(failure.getMessage());
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    // ---- withdraw ------------------------------------------------------------------------------

    public Mono<RecoveryProposal> withdraw(String proposalId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> load(proposalId).flatMap(proposal -> {
            if (!actor.equals(proposal.getProposedById())) {
                return Mono.<RecoveryProposal>error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                        "only the person who proposed an action can withdraw it"));
            }
            return template.findAndModify(
                            Query.query(Criteria.where("_id").is(proposalId).and("status").is(RecoveryProposalStatus.PENDING)),
                            new Update().set("status", RecoveryProposalStatus.WITHDRAWN).inc("version", 1),
                            FindAndModifyOptions.options().returnNew(true), RecoveryProposal.class)
                    .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSACTION_NOT_RECOVERABLE,
                            "the proposal is " + proposal.statusAt(clock.instant()),
                            Map.of("currentStatus", proposal.statusAt(clock.instant()).name()))));
        }));
    }

    // ---- reads ---------------------------------------------------------------------------------

    /** Proposals made by somebody else that the caller could confirm now. */
    public Flux<RecoveryProposal> queue() {
        return SecurityContextUtils.requireCurrentUserId().flatMapMany(me -> OrganizerAccess.authorities().flatMapMany(authorities ->
                template.find(new Query(Criteria.where("status").is(RecoveryProposalStatus.PENDING)
                                .and("expiresAt").gt(clock.instant())
                                .and("proposedById").ne(me))
                        .with(Sort.by(Sort.Direction.ASC, "expiresAt")), RecoveryProposal.class)
                        .filter(proposal -> DualControlRules.holdsRole(authorities, proposal.getAction()))));
    }

    public Flux<RecoveryProposal> mine() {
        return SecurityContextUtils.requireCurrentUserId().flatMapMany(me -> template.find(
                new Query(Criteria.where("proposedById").is(me)).with(Sort.by(Sort.Direction.DESC, "proposedAt")).limit(100),
                RecoveryProposal.class));
    }

    public Flux<RecoveryProposal> pending() {
        return template.find(new Query(Criteria.where("status").is(RecoveryProposalStatus.PENDING)
                        .and("expiresAt").gt(clock.instant())).with(Sort.by(Sort.Direction.ASC, "expiresAt")), RecoveryProposal.class);
    }

    private Mono<RecoveryProposal> load(String id) {
        return template.findById(id, RecoveryProposal.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.RECOVERY_PROPOSAL_UNKNOWN, "no proposal " + id)));
    }
}
