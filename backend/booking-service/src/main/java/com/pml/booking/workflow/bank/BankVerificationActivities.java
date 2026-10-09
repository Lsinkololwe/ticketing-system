package com.pml.booking.workflow.bank;

import com.pml.booking.workflow.bank.BankVerificationWorkflow.View;
import io.temporal.activity.ActivityInterface;

import java.math.BigDecimal;

/**
 * The writes and the deposit a verification makes. Each is idempotent.
 */
@ActivityInterface(namePrefix = "BankVerification")
public interface BankVerificationActivities {

    /** Records the deposit amount and moves the account to VERIFYING; a repeat with the same amount changes nothing. */
    View prepare(String bankAccountId, BigDecimal depositAmount);

    /** Sends the recorded deposit through the provider under a payout id that makes a retry the same deposit. */
    void sendDeposit(String bankAccountId, String providerPayoutId);

    /** Books a sent deposit as a platform verification cost; one journal entry per deposit. */
    void bookDeposit(String bankAccountId, String providerPayoutId);

    /** Asks the provider what became of a sent deposit; only a terminal answer carrying its reference is trusted. */
    BankVerificationWorkflow.DepositOutcome depositOutcome(String bankAccountId, String providerPayoutId);

    /** Reverses the booked cost of a deposit the provider confirmed failed; once per deposit. */
    void reverseDeposit(String bankAccountId, String providerPayoutId);

    /** A match verifies; a mismatch counts, and the third locks verification for a day. */
    View confirm(String bankAccountId, String actorId, BigDecimal amount);

    View unlock(String bankAccountId);

    View expire(String bankAccountId);
}
