package com.pml.booking.service;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * An operator's hand on a chargeback's recovery: start the waterfall, or record money that came in from
 * one of its sources. Writing a loss off is not here — it destroys a receivable, so it takes two people
 * ({@code DualControlService}).
 */
@Service
public class ChargebackRecoveryOps {

    public enum Action { START_RECOVERY, RECORD_RECOVERY }

    private static final Set<ChargebackFundSource> RECORDABLE = Set.of(
            ChargebackFundSource.ORGANIZER_ESCROW, ChargebackFundSource.ORGANIZER_FUTURE, ChargebackFundSource.PLATFORM_RESERVE);

    private final ReactiveMongoTemplate template;
    private final ChargebackService chargebacks;
    private final AccountingService accounting;
    private final TransactionalOperator transaction;

    public ChargebackRecoveryOps(ReactiveMongoTemplate template, ChargebackService chargebacks, AccountingService accounting,
                                 TransactionalOperator transaction) {
        this.template = template;
        this.chargebacks = chargebacks;
        this.accounting = accounting;
        this.transaction = transaction;
    }

    public Mono<ChargebackRecord> apply(String id, Action action, BigDecimal amount, ChargebackFundSource source,
                                        String reference, String actor) {
        return template.findById(id, ChargebackRecord.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.CHARGEBACK_STATE_INVALID, "no chargeback " + id)))
                .flatMap(record -> {
                    if (!record.isLoss()) {
                        return Mono.<ChargebackRecord>error(new TranslatedRefusal(ErrorCode.CHARGEBACK_STATE_INVALID,
                                "only a chargeback that was accepted or lost has anything to recover",
                                Map.of("currentStatus", String.valueOf(record.getStatus()))));
                    }
                    if (action == Action.START_RECOVERY) {
                        return record.getRecoveryStatus() == RecoveryStatus.NOT_STARTED
                                ? chargebacks.startRecovery(id)
                                : Mono.just(record);
                    }
                    return recordRecovery(record, amount, source, reference, actor);
                });
    }

    private Mono<ChargebackRecord> recordRecovery(ChargebackRecord record, BigDecimal amount, ChargebackFundSource source,
                                                  String reference, String actor) {
        TranslatedRefusal malformed = checkRecording(amount, source, reference);
        if (malformed != null) {
            return Mono.error(malformed);
        }
        // The reference names the money that came in; recording it twice (a retried or double-clicked request) is one recovery.
        String marker = marker(reference);
        if (alreadyRecorded(record, reference)) {
            return Mono.just(record);
        }
        if (record.getRecoveryStatus() == RecoveryStatus.RECOVERED || record.getRecoveryStatus() == RecoveryStatus.WRITTEN_OFF
                || amount.compareTo(record.getUnrecoveredAmount()) > 0) {
            return Mono.error(new TranslatedRefusal(ErrorCode.CHARGEBACK_STATE_INVALID,
                    "the amount exceeds what is still unrecovered, " + record.getUnrecoveredAmount().toPlainString(),
                    Map.of("currentStatus", String.valueOf(record.getRecoveryStatus()))));
        }
        return accounting.recordChargeback(record.getChargebackId(), record.getEventId(), record.getTicketId(), amount,
                        BigDecimal.ZERO, source.name(), record.getCurrency())
                .flatMap(entry -> {
                    if (record.getRecoveryStatus() == null || record.getRecoveryStatus() == RecoveryStatus.NOT_STARTED) {
                        record.setRecoveryStatus(RecoveryStatus.IN_PROGRESS);
                    }
                    record.recordRecovery(amount, source, actor);
                    record.setInternalNotes((record.getInternalNotes() == null ? "" : record.getInternalNotes() + "\n")
                            + marker + " " + amount.toPlainString() + " from " + source + " by " + actor);
                    record.setRecoveryJournalEntryId(entry.getId());
                    return template.save(record);
                })
                .as(transaction::transactional);
    }

    /** Null when a recovery of {@code amount} from {@code source} under {@code reference} is well formed; otherwise the refusal. */
    public static TranslatedRefusal checkRecording(BigDecimal amount, ChargebackFundSource source, String reference) {
        if (source == null || !RECORDABLE.contains(source)) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "recovery is recorded from the organizer's escrow, their future payouts or the platform reserve");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "an amount is a positive sum of whole ngwee");
        }
        if (reference == null || reference.isBlank()) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a recovery is recorded with its reference");
        }
        return null;
    }

    /** The line a recorded recovery leaves in the record's notes, so the same reference is recognised when it comes again. */
    public static String marker(String reference) {
        return "recovery-ref:" + reference.trim();
    }

    public static boolean alreadyRecorded(ChargebackRecord record, String reference) {
        return record.getInternalNotes() != null && record.getInternalNotes().contains(marker(reference));
    }
}
