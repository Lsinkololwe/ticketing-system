package com.pml.identity.domain.valueobject;

import java.util.Set;

/**
 * Roles within an organization (tenant-scoped).
 *
 * Role hierarchy:
 * OWNER → ADMIN → MANAGER/MARKETER → CONTRIBUTOR
 */
public enum OrganizationRole {
    /**
     * Single person who owns the organization.
     * Full access including ownership transfer and deletion.
     * Only ONE owner per organization.
     */
    OWNER,

    /**
     * Full administrative access except ownership operations.
     * Can invite/remove team members, manage all events, view financials.
     */
    ADMIN,

    /**
     * Event management focus.
     * Can create/manage events, view event analytics, limited financial access.
     */
    MANAGER,

    /**
     * Marketing and analytics focus.
     * Can view events, manage promotions, view analytics.
     * Cannot create or edit events.
     */
    MARKETER,

    /**
     * Limited access contributor.
     * View-only access to events, can assist with check-in.
     */
    CONTRIBUTOR;

    /**
     * Check if this role can invite team members
     */
    public boolean canInviteMembers() {
        return this == OWNER || this == ADMIN;
    }

    /**
     * Check if this role can remove team members
     */
    public boolean canRemoveMembers() {
        return this == OWNER || this == ADMIN;
    }

    /**
     * Check if this role can change member roles
     */
    public boolean canChangeRoles() {
        return this == OWNER || this == ADMIN;
    }

    /**
     * Check if this role can create events
     */
    public boolean canCreateEvents() {
        return this == OWNER || this == ADMIN || this == MANAGER;
    }

    /**
     * Check if this role can publish events
     */
    public boolean canPublishEvents() {
        return this == OWNER || this == ADMIN || this == MANAGER;
    }

    /**
     * Check if this role can delete events
     */
    public boolean canDeleteEvents() {
        return this == OWNER || this == ADMIN;
    }

    /**
     * Check if this role can view financials
     */
    public boolean canViewFinancials() {
        return this == OWNER || this == ADMIN || this == MANAGER;
    }

    /**
     * Check if this role can request payouts
     */
    public boolean canRequestPayouts() {
        return this == OWNER || this == ADMIN;
    }

    /**
     * Check if this role can manage promotions
     */
    public boolean canManagePromotions() {
        return this == OWNER || this == ADMIN || this == MANAGER || this == MARKETER;
    }

    /**
     * Check if this role can scan tickets
     */
    public boolean canScanTickets() {
        return true; // All roles can scan tickets
    }

    /**
     * Get the roles that this role inherits from (has all permissions of)
     */
    public Set<OrganizationRole> getInheritedRoles() {
        return switch (this) {
            case OWNER -> Set.of(ADMIN, MANAGER, MARKETER, CONTRIBUTOR);
            case ADMIN -> Set.of(MANAGER, MARKETER, CONTRIBUTOR);
            case MANAGER -> Set.of(CONTRIBUTOR);
            case MARKETER -> Set.of(CONTRIBUTOR);
            case CONTRIBUTOR -> Set.of();
        };
    }

    /**
     * Check if this role is higher than or equal to another role
     */
    public boolean isAtLeast(OrganizationRole other) {
        if (this == other) return true;
        return getInheritedRoles().contains(other);
    }
}
