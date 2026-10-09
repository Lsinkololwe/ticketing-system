package com.pml.booking.service;

import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.domain.model.PlatformTransfer;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves the platform's own money between its operating and reserve accounts, and records it in the
 * ledger.
 *
 * <p>The balance moves, the journal entry and the transfer record commit together or not at all, and a
 * repeated idempotency key returns the first result rather than moving the money again. Above the
 * single-approver limit the move needs a second person ({@code DualControlService}); this class does
 * the move itself and does not decide who may order it.
 */
@Service
public class PlatformTransferService {

    private static final Set<PlatformAccountType> MOVABLE = Set.of(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE);

    private final PlatformAccountService accounts;
    private final JournalService journal;
    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final Clock clock;
    private final BigDecimal singleApproverLimit;

    public PlatformTransferService(PlatformAccountService accounts, JournalService journal, ReactiveMongoTemplate template,
                                   TransactionalOperator transaction, Clock clock,
                                   @Value("${booking.platform-transfer.single-approver-limit:1000.00}") BigDecimal singleApproverLimit) {
        this.accounts = accounts;
        this.journal = journal;
        this.template = template;
        this.transaction = transaction;
        this.clock = clock;
        this.singleApproverLimit = singleApproverLimit;
    }

    /** Whether a move of {@code amount} needs a second person to authorise it. */
    public boolean needsSecondPerson(BigDecimal amount) {
        return amount.compareTo(singleApproverLimit) > 0;
    }

    /** Null when the move is well formed; otherwise the refusal. */
    public static TranslatedRefusal check(PlatformAccountType from, PlatformAccountType to, BigDecimal amount, String reason) {
        if (from == null || to == null || !MOVABLE.contains(from) || !MOVABLE.contains(to)) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "money moves between the operating and reserve accounts only; the tax holding account moves with a tax remittance");
        }
        if (from == to) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "choose two different accounts");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "an amount is a positive sum of whole ngwee");
        }
        if (reason == null || reason.trim().length() < 10) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a transfer needs a reason of at least 10 characters");
        }
        return null;
    }

    public Mono<PlatformTransfer> execute(PlatformAccountType from, PlatformAccountType to, BigDecimal amount, String reason,
                                          String idempotencyKey, String executedBy, String proposalId) {
        TranslatedRefusal refusal = check(from, to, amount, reason);
        if (refusal != null) {
            return Mono.error(refusal);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a transfer needs an idempotency key"));
        }
        return existing(idempotencyKey).switchIfEmpty(Mono.defer(() -> move(from, to, amount, reason.trim(), idempotencyKey,
                        executedBy, proposalId).as(transaction::transactional)
                .onErrorResume(DuplicateKeyException.class, raced -> existing(idempotencyKey))));
    }

    private Mono<PlatformTransfer> existing(String key) {
        return template.findOne(Query.query(Criteria.where("idempotencyKey").is(key)), PlatformTransfer.class);
    }

    private Mono<PlatformTransfer> move(PlatformAccountType from, PlatformAccountType to, BigDecimal amount, String reason,
                                        String key, String executedBy, String proposalId) {
        String description = "Transfer " + from + " to " + to + ": " + reason;
        PlatformTransfer draft = PlatformTransfer.builder()
                .idempotencyKey(key).fromAccount(from).toAccount(to).amount(amount).currency("ZMW")
                .reason(reason).executedBy(executedBy).proposalId(proposalId).createdAt(clock.instant()).build();
        return template.insert(draft)
                .flatMap(saved -> accounts.transfer(from, to, amount, saved.getId(), description)
                        .then(journal.createAndPostEntry(saved.getId(), clock.instant(), description, JournalEntryType.STANDARD,
                                List.of(JournalLine.debit(to.getDefaultAccountCode(), "Platform " + to + " account", amount, description),
                                        JournalLine.credit(from.getDefaultAccountCode(), "Platform " + from + " account", amount, description)),
                                executedBy, Map.of("transactionType", "PLATFORM_TRANSFER", "proposalId", String.valueOf(proposalId))))
                        .flatMap(entry -> {
                            saved.setJournalEntryId(entry.getId());
                            return template.save(saved);
                        }));
    }
}
