package com.pml.identity.workflow.ensure;

import com.pml.identity.account.ConsentGrant;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import io.temporal.activity.ActivityInterface;

import java.util.List;

/**
 * The writes behind {@link AccountEnsureWorkflow}. Every one is repeatable: a retry after a crash
 * finds its work done and returns the same answer. Inputs carry ids and the proof id, never a contact.
 */
@ActivityInterface(namePrefix = "AccountEnsure")
public interface AccountEnsureActivities {

    /** Gives the contact an owner (or finds it); refuses a suspended, merging or gone account. */
    Claimed claimContact(Claim claim);

    /** Creates the Keycloak user named by the account id, or reads back the one an earlier attempt made. */
    String createKeycloakUser(String accountId);

    /** Writes the account id, the email contact and the CUSTOMER role onto the Keycloak user. */
    void applyAttributesAndRoles(String accountId, String keycloakUserId);

    /** PROVISIONING to ACTIVE with consents, the account event and the outbox row in one transaction. */
    AccountState activateAccount(Activation activation);

    /** Makes sure the activation fact is staged; staging it twice is one row. */
    void stageOutbox(String accountId);

    record Claim(String proofId, String contactKey, ContactType contactType) {
    }

    record Claimed(String accountId, AccountState state, boolean created) {
    }

    record Activation(String accountId, String keycloakUserId, String clientId, String displayName,
                      List<ConsentGrant> consents) {
    }
}
