package com.pml.identity.domain.enums;

/**
 * Status of an organization (tenant).
 */
public enum OrganizationStatus {
    /**
     * Organization is active and operational
     */
    ACTIVE,

    /**
     * Organization suspended by platform admin
     */
    SUSPENDED,

    /**
     * Organization deactivated by owner
     */
    INACTIVE,

    /**
     * Organization scheduled for deletion
     */
    PENDING_DELETION
}
