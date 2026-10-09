package com.pml.booking.error;

import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.PlatformRefusalTranslator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import org.bson.BsonDocument;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two conflicts that must never be confused.
 *
 * <h2>Why this is the money test</h2>
 * A lock conflict and a reused idempotency key are both "a unique constraint
 * said no", and they call for opposite advice. Retrying a lost lock race is
 * correct and usually succeeds. Retrying a reused idempotency key is the exact
 * double charge the key was introduced to prevent — and because the client
 * decides from {@code extensions.retryable} alone, getting this wrong does not
 * produce an error anyone sees. It produces a second payment.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R7 · lock conflicts retry, reused idempotency keys never do")
class IdempotencyConflictTest {

    private final BookingRefusalTranslator booking = new BookingRefusalTranslator();
    private final PlatformRefusalTranslator platform = new PlatformRefusalTranslator();

    /** The chain as wired: service translator first, platform as fallback. */
    private DomainRefusal translate(Throwable thrown) {
        return booking.translate(thrown)
                .or(() -> platform.translate(thrown))
                .orElseThrow(() -> new AssertionError("nothing translated " + thrown));
    }

    /** A duplicate-key failure as the driver reports it, naming the index. */
    private static DuplicateKeyException duplicateOn(String collection, String index) {
        return new DuplicateKeyException(
                "E11000 duplicate key error collection: ticketing." + collection
                        + " index: " + index + " dup key: { idempotencyKey: \"abc-123\" }");
    }

    @Test
    @DisplayName("a reused payment idempotency key is IDEMPOTENCY_KEY_REUSED and never retryable")
    void reusedPaymentKeyIsNotRetryable() {
        DomainRefusal refusal = translate(
                duplicateOn("booking_payment_intents", "idx_idempotencyKey"));

        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(refusal.retryable())
                .as("retryable: true here tells the client to repeat a charge the database "
                        + "just refused — the double payment the key exists to prevent")
                .isFalse();
    }

    @Test
    @DisplayName("a reused payout idempotency key is treated identically")
    void reusedPayoutKeyIsNotRetryable() {
        // Same index name, different collection: payouts move money outward, so
        // the consequence of a wrongly-advised retry is if anything worse.
        DomainRefusal refusal = translate(
                duplicateOn("booking_payout_requests", "idx_idempotencyKey"));

        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(refusal.retryable()).isFalse();
    }

    @Test
    @DisplayName("a replayed webhook and a replayed offline scan are idempotency guards too")
    void replayGuardsAreIdempotencyGuards() {
        for (String index : new String[]{"idx_providerEventId", "idx_scanId"}) {
            assertThat(translate(duplicateOn("booking_x", index)).errorCode())
                    .as("%s guards against replay; a retry re-admits or re-credits", index)
                    .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    @Test
    @DisplayName("a lock conflict is RESOURCE_CONFLICT and is retryable")
    void lockConflictIsRetryable() {
        DomainRefusal refusal = translate(
                new OptimisticLockingFailureException("version 4 did not match 5"));

        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(refusal.retryable())
                .as("the write lost a race; the same request may well win the next")
                .isTrue();
    }

    @Test
    @DisplayName("a duplicate key on some other index falls back to retryable RESOURCE_CONFLICT")
    void unrecognisedDuplicateKeyIsNotAssumedToBeIdempotency() {
        // Guessing "idempotency" for an unknown index would tell a caller a key
        // was reused when they never sent one. The fallback is the honest answer.
        DomainRefusal refusal = translate(
                duplicateOn("booking_tickets", "idx_ticketReference"));

        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(refusal.retryable()).isTrue();
    }

    @Test
    @DisplayName("the service translator answers before the platform's fallback")
    void serviceWinsOverPlatform() {
        DuplicateKeyException idempotency =
                duplicateOn("booking_payment_intents", "idx_idempotencyKey");

        // Ordering is what makes the distinction possible at all: the platform
        // alone cannot know which index guards a key, so it calls every duplicate
        // retryable. If the chain ever ran platform-first, the two tests above
        // would still pass in isolation and the wire would be wrong.
        assertThat(booking.translate(idempotency))
                .map(DomainRefusal::errorCode)
                .contains(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(platform.translate(idempotency))
                .map(DomainRefusal::errorCode)
                .contains(ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    @DisplayName("the driver's own MongoWriteException is recognised, not just Spring's")
    void driverExceptionIsRecognised() {
        // A reactive write can surface MongoWriteException untranslated. Matching
        // only Spring's DuplicateKeyException sends it down the defect path as a
        // retryable INTERNAL_ERROR — so the client is told an internal error
        // occurred and that retrying may help, and it retries. That is the double
        // charge the key exists to prevent, arrived at through the error contract.
        MongoWriteException driverError = new MongoWriteException(
                new WriteError(11000,
                        "E11000 duplicate key error collection: ticketing.booking_payment_intents "
                                + "index: idx_idempotencyKey dup key: { idempotencyKey: \"abc\" }",
                        new BsonDocument()),
                new ServerAddress(),
                java.util.Collections.emptyList());

        DomainRefusal refusal = translate(driverError);

        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(refusal.retryable()).isFalse();
    }

    @Test
    @DisplayName("a write failure that is not a duplicate key is left alone")
    void nonDuplicateWriteErrorIsNotAConflict() {
        // MongoWriteException covers every write failure. Treating them all as
        // duplicates would call a document-validation rejection a uniqueness
        // conflict and offer a retry that fails identically forever.
        MongoWriteException validationFailure = new MongoWriteException(
                new WriteError(121, "Document failed validation", new BsonDocument()),
                new ServerAddress(),
                java.util.Collections.emptyList());

        assertThat(booking.translate(validationFailure)).isEmpty();
        assertThat(platform.translate(validationFailure)).isEmpty();
    }

    @Test
    @DisplayName("a duplicate key with no message does not crash the translator")
    void nullMessageIsSafe() {
        Optional<DomainRefusal> refusal = booking.translate(new DuplicateKeyException(null));
        assertThat(refusal).isEmpty();
    }
}
