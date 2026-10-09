package com.pml.booking.error;

import com.pml.booking.exception.*;
import com.pml.booking.infrastructure.gateway.exception.NoGatewayAvailableException;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.DuplicateKeys;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.RefusalTranslator;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Every exception booking-service declares, mapped to its platform {@link ErrorCode}.
 *
 * <h2>Why the messages here are not the exceptions' own</h2>
 * These exception types build their messages from data —
 * {@code "Ticket not found: %s (%s)"}, {@code "Insufficient balance: needed %s, had %s"}.
 * Those strings are for the log, and {@code DomainRefusal} keeps them there.
 * What the client receives is the code, the classification, {@code retryable}
 * and the details listed below, all chosen at this one reviewable site.
 *
 * <h2>Escrow balances are the case worth naming</h2>
 * {@code ESCROW_INSUFFICIENT_BALANCE} carries no amounts. An organizer may know
 * their own balance; the exception message states both the shortfall and the
 * available figure, and the ids in a request are not proof the caller owns the
 * account named. The client needs the code to render "this payout cannot be
 * covered" — it does not need the number to do it.
 *
 * <p>{@code BookingRefusalCoverageTest} fails when a new exception class appears
 * in this service without a row here, because the failure mode otherwise is
 * silent: an unmapped exception is a defect, so the caller gets
 * {@code INTERNAL_ERROR} for a perfectly ordinary business refusal and the
 * on-call sees an ERROR for something that was working as designed.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Component
public class BookingRefusalTranslator implements RefusalTranslator {

    @Override
    public Optional<DomainRefusal> translate(Throwable exception) {
        return Optional.ofNullable(switch (exception) {

            // ---- Reservations --------------------------------------------
            case ReservationNotFoundException ignored -> new TranslatedRefusal(
                    ErrorCode.RESERVATION_UNKNOWN, "reservation not found");

            case ReservationExpiredException ignored -> new TranslatedRefusal(
                    // Not retryable: the hold is gone and the seats are back in
                    // inventory. A retry would be a fresh reservation attempt
                    // that may find the tier sold out, which is a different
                    // outcome than "try that again".
                    ErrorCode.RESERVATION_EXPIRED, "reservation hold expired");

            case NoGatewayAvailableException ignored -> new TranslatedRefusal(
                    // The mirror case: nobody answered. Retryable, because the
                    // instruction was never judged.
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                    "no payment gateway available for this request");

            case ChargebackProcessingException ignored -> new TranslatedRefusal(
                    ErrorCode.CHARGEBACK_STATE_INVALID, "chargeback not in a processable state");

            // ---- Escrow and ledger ----------------------------------------
            case AccountNotFoundException ignored -> new TranslatedRefusal(
                    ErrorCode.ESCROW_ACCOUNT_UNKNOWN, "escrow account not found");

            case InactiveAccountException ignored -> new TranslatedRefusal(
                    ErrorCode.ESCROW_NOT_ACTIVE, "escrow account is not active");

            case InsufficientEscrowBalanceException ignored -> new TranslatedRefusal(
                    ErrorCode.ESCROW_INSUFFICIENT_BALANCE, "escrow balance below requested amount");

            case ProviderUnavailableException ignored -> new TranslatedRefusal(
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "the payment provider could not be reached; try again shortly");

            case UnbalancedJournalEntryException ignored -> new TranslatedRefusal(
                    // Debits not equal to credits is a defect in whatever built
                    // the entry, and it is classified as a refusal only so the
                    // ledger's own invariant is named in the log rather than
                    // arriving as an anonymous internal error. Nothing about the
                    // caller's request can fix it, hence not retryable.
                    ErrorCode.JOURNAL_UNBALANCED, "journal entry does not balance");

            case ReconciliationDiscrepancyException ignored -> new TranslatedRefusal(
                    ErrorCode.RECONCILIATION_IN_PROGRESS, "reconciliation discrepancy outstanding");

            // ---- Input ------------------------------------------------------
            case BusinessValidationException ignored -> new TranslatedRefusal(
                    ErrorCode.COMMAND_NOT_WELL_FORMED, "command failed business validation");

            // ---- Idempotency ------------------------------------------------
            // Answered here rather than by the platform because only this service
            // knows which of its unique indexes guards an idempotency key. The
            // platform's fallback for any duplicate key is a retryable
            // RESOURCE_CONFLICT, and retrying this one is the double charge the
            // key exists to prevent.
            case Throwable duplicate when guardsAnIdempotencyKey(duplicate) ->
                    new TranslatedRefusal(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                            "idempotency key already used by an earlier request");

            default -> null;
        });
    }

    /**
     * Whether a duplicate-key failure came from an idempotency guard.
     *
     * <p>The index name is the only signal the driver carries, so it is read
     * from the message. That is brittle in principle — and the alternative is
     * treating every duplicate key as retryable, which on a payment path means
     * advising the client to repeat a charge the database just refused. The
     * names are asserted against {@code BookingIndexInitializer} by
     * {@code BookingIndexRegistryTest}, so a rename fails the build before it
     * can quietly turn this into a fallback.</p>
     *
     * <p>Unrecognised duplicate keys deliberately fall through to the platform's
     * retryable {@code RESOURCE_CONFLICT}: guessing "idempotency" for an unknown
     * index would tell a caller a key was reused when they never sent one.</p>
     */
    private static boolean guardsAnIdempotencyKey(Throwable thrown) {
        if (!DuplicateKeys.isDuplicateKey(thrown)) {
            return false;
        }
        String message = DuplicateKeys.describe(thrown);
        return IDEMPOTENCY_INDEXES.stream().anyMatch(message::contains);
    }

    /** Index names whose uniqueness is an idempotency guard. */
    private static final List<String> IDEMPOTENCY_INDEXES = List.of(
            "idx_idempotencyKey",   // booking_payment_intents, booking_payout_requests
            "idx_providerEventId",  // booking_webhook_receipts — webhook replay
            "idx_scanId");          // booking_checkins — offline scan upload replay
}
