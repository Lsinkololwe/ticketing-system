package com.pml.identity.workflow.contactchange;

import com.pml.identity.account.ContactChangeKind;
import com.pml.identity.domain.enums.ContactType;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Adds, changes or removes a contact of an account, or moves its primary (ET-IDN-004 R3, R5).
 *
 * <p>Addressed as {@code contact-change/{accountId}}: one open change per account. A second start while
 * one is open fails, and the caller is told {@code CONTACT_CHANGE_IN_PROGRESS}. The input carries ids,
 * the contact key (a keyed hash) and a masked display value; never a contact, never a code. The codes
 * are checked by the caller against the challenge store, and only the resulting proof ids reach here.</p>
 *
 * <pre>
 *   begin (CHANGING) -> [wait for both proofs, 48 h] -> claimContact -> syncKeycloak -> commit (release) ->
 *   endSessions -> clearMarker -> notifyContacts
 * </pre>
 *
 * <p>The new contact is claimed under the unique index before the old one is released, and the old one
 * is released only after Keycloak holds the new email, so the account is reachable throughout. The
 * process moves forward: a transient failure is retried until it clears. Only a refusal (a lost claim,
 * an expired or exhausted confirmation, a cancel) ends it early, and then the marker is cleared and the
 * account is as it was.</p>
 */
@WorkflowInterface
public interface ContactChangeWorkflow {

    @WorkflowMethod
    Outcome run(ChangeCommand command);

    /** The code sent to the current primary contact was right: the change is authorised. */
    @UpdateMethod
    void authorise();

    @UpdateValidatorMethod(updateName = "authorise")
    void validateAuthorise();

    /** The code sent to the new contact was right; {@code proofId} is the proof of it. */
    @UpdateMethod
    void acceptNew(String proofId);

    @UpdateValidatorMethod(updateName = "acceptNew")
    void validateAcceptNew(String proofId);

    /** A wrong or expired code was presented; enough of them abort the change. */
    @SignalMethod
    void failedAttempt();

    /** A code was asked for again; the challenge for {@code target} (NEW or CURRENT) is now {@code challengeId}. */
    @SignalMethod
    void codeResent(String target, String challengeId);

    /** The person abandoned the change. */
    @SignalMethod
    void cancel();

    @QueryMethod
    Status status();

    /**
     * @param newContactKey  the keyed hash of the new contact (ADD, CHANGE), null otherwise
     * @param newMasked      display form of the new contact, for the pending-change view
     * @param proofId        the proof of the new contact for ADD; null for the others
     * @param newChallengeId  the challenge whose code goes to the new contact (CHANGE)
     * @param currentChallengeId the challenge whose code goes to the current primary contact (CHANGE)
     */
    record ChangeCommand(String changeId, String accountId, ContactChangeKind kind, String contactId, ContactType type,
                         String newContactKey, String newMasked, String proofId, String newChallengeId,
                         String currentChallengeId) {
    }

    /** {@code status} is COMPLETED or CANCELLED. */
    record Outcome(String changeId, String status) {
    }

    /** {@code phase}: STARTING, AWAITING_CODES, APPLYING, DONE. */
    record Status(String changeId, ContactChangeKind kind, String phase, String newMasked, long expiresAtMillis,
                  boolean authorised, boolean newAccepted, int attemptsRemaining, String newChallengeId,
                  String currentChallengeId, String newContactKey) {
    }
}
