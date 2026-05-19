package com.pml.identity.domain.enums;

/**
 * Status of a verification document.
 */
public enum DocumentStatus {
    /**
     * Document uploaded, awaiting review
     */
    PENDING,

    /**
     * Document verified and approved
     */
    APPROVED,

    /**
     * Document rejected
     */
    REJECTED,

    /**
     * Document has expired
     */
    EXPIRED
}
