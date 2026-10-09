package com.pml.identity.workflow.contactchange;

import com.pml.identity.account.ContactChangeKind;
import com.pml.identity.domain.enums.ContactType;
import io.temporal.activity.ActivityInterface;

/** The writes behind {@link ContactChangeWorkflow}. Every one is repeatable; inputs are ids and keys. */
@ActivityInterface(namePrefix = "ContactChange")
public interface ContactChangeActivities {

    /** Sets {@code pendingKind=CHANGING}; refuses an account that is not ACTIVE. */
    void begin(String accountId);

    /** Claims the new contact under the unique index; a lost claim is a {@code CONTACT_ALREADY_CLAIMED} refusal. */
    String claimContact(Claim claim);

    /** Writes the account's email to Keycloak as it will be after the switch. */
    void syncKeycloak(String accountId, String excludeContactId);

    /** Releases the old contact, decides the primary, writes the account event and outbox row, in one transaction. */
    void commit(Commit commit);

    void endSessions(String accountId);

    void clearMarker(String accountId);

    /** Best effort. */
    void notifyContacts(Notice notice);

    record Claim(String accountId, String changeId, String proofId, String contactKey, ContactType type) {
    }

    record Commit(String accountId, String changeId, ContactChangeKind kind, String contactId, String newContactId) {
    }

    record Notice(String accountId, ContactChangeKind kind, String contactId, String newContactId) {
    }
}
