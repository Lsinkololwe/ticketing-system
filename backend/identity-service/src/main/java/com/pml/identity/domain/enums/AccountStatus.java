package com.pml.identity.domain.enums;

/**
 * User account status.
 */
public enum AccountStatus {
    /**
     * Account is active and operational
     */
    ACTIVE,

    /**
     * Account is inactive (user-initiated)
     */
    INACTIVE,

    /**
     * Account is temporarily locked (security)
     */
    LOCKED,

    /**
     * Account is suspended by admin
     */
    SUSPENDED,

    /**
     * Account is pending email/phone verification
     */
    PENDING_VERIFICATION,

    /**
     * Account is pending deletion (GDPR request)
     */
    PENDING_DELETION
}
