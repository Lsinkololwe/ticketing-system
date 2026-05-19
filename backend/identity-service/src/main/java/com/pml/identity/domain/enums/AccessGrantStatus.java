package com.pml.identity.domain.enums;

/**
 * Status of an event access grant.
 */
public enum AccessGrantStatus {
    /**
     * Access is active
     */
    ACTIVE,

    /**
     * Access is suspended temporarily
     */
    SUSPENDED,

    /**
     * Access has been revoked
     */
    REVOKED,

    /**
     * Access has expired (time-limited access)
     */
    EXPIRED
}
