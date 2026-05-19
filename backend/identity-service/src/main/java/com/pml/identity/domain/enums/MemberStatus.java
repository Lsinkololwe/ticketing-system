package com.pml.identity.domain.enums;

/**
 * Status of a team member within an organization.
 */
public enum MemberStatus {
    /**
     * Member is active and has full access based on their role
     */
    ACTIVE,

    /**
     * Member is deactivated (by admin)
     */
    INACTIVE,

    /**
     * Member is temporarily suspended
     */
    SUSPENDED,

    /**
     * Member has been removed from the organization
     */
    REMOVED
}
