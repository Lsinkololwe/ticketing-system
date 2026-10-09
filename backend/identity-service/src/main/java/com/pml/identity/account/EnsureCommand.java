package com.pml.identity.account;

import com.pml.identity.domain.enums.ContactType;

import java.util.List;

/**
 * Input of the account-ensure process (CONTRACT 10, 12). Carries only the proof id and the contact
 * key - never the raw contact; the workflow reads the encrypted contact from the proof.
 */
public record EnsureCommand(String proofId, String contactKey, ContactType contactType, String clientId,
                            boolean issueHandle, String displayName, List<ConsentGrant> consents) {
}
