package com.pml.identity.domain.enums;

/**
 * Status of an ownership transfer request.
 */
public enum TransferStatus {
    /**
     * Transfer initiated, awaiting new owner acceptance
     */
    PENDING,

    /**
     * New owner accepted
     */
    ACCEPTED,

    /**
     * Transfer was rejected by the recipient
     */
    REJECTED,

    /**
     * Transfer completed successfully
     */
    COMPLETED,

    /**
     * Transfer was cancelled by current owner
     */
    CANCELLED,

    /**
     * Transfer request expired
     */
    EXPIRED
}
