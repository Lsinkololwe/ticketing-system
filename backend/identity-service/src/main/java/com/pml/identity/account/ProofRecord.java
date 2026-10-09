package com.pml.identity.account;

import com.pml.identity.domain.enums.ContactType;

/**
 * A proof of contact control as stored in Redis ({@code proof:{id}}, CONTRACT 6). Holds the
 * encrypted contact so the account workflow can claim it without the raw value in its input.
 *
 * @param state     {@code NEW} or {@code CONSUMED}
 * @param accountId set once the ensure step has linked the proof to an account (may be null)
 */
public record ProofRecord(String proofId, String contactKey, ContactType type, String valueEncrypted,
                          String valueMasked, String state, String accountId) {
    @Override
    public String toString() {
        return "ProofRecord[" + proofId + ", " + type + ", " + valueMasked + ", " + state + "]";
    }
}
