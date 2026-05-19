package com.pml.identity.domain.valueobject;

import java.util.Set;

/**
 * Event-level roles that override organization-level permissions for a specific event.
 */
public enum EventRole {
    /**
     * Person who created the event.
     * Full control including deletion and role assignment.
     */
    EVENT_OWNER,

    /**
     * Full access to this specific event.
     * Can edit everything, issue refunds, assign roles (except EVENT_OWNER).
     */
    EVENT_ADMIN,

    /**
     * Can edit event content and manage tickets.
     * Cannot issue refunds or assign roles.
     */
    EDITOR,

    /**
     * Venue staff for scanning tickets.
     * Can scan/validate tickets and view attendee list.
     */
    CHECK_IN,

    /**
     * Read-only access.
     * Can view event details and sales data.
     */
    VIEWER;

    /**
     * Check if this role can edit event details
     */
    public boolean canEditEvent() {
        return this == EVENT_OWNER || this == EVENT_ADMIN || this == EDITOR;
    }

    /**
     * Check if this role can manage tickets
     */
    public boolean canManageTickets() {
        return this == EVENT_OWNER || this == EVENT_ADMIN || this == EDITOR;
    }

    /**
     * Check if this role can issue refunds
     */
    public boolean canIssueRefunds() {
        return this == EVENT_OWNER || this == EVENT_ADMIN;
    }

    /**
     * Check if this role can scan tickets
     */
    public boolean canScanTickets() {
        return this == EVENT_OWNER || this == EVENT_ADMIN || this == EDITOR || this == CHECK_IN;
    }

    /**
     * Check if this role can view sales data
     */
    public boolean canViewSalesData() {
        return this == EVENT_OWNER || this == EVENT_ADMIN || this == EDITOR || this == VIEWER;
    }

    /**
     * Check if this role can send notifications to attendees
     */
    public boolean canSendNotifications() {
        return this == EVENT_OWNER || this == EVENT_ADMIN || this == EDITOR;
    }

    /**
     * Check if this role can cancel the event
     */
    public boolean canCancelEvent() {
        return this == EVENT_OWNER;
    }

    /**
     * Check if this role can delete the event
     */
    public boolean canDeleteEvent() {
        return this == EVENT_OWNER;
    }

    /**
     * Check if this role can assign event roles to others
     */
    public boolean canAssignRoles() {
        return this == EVENT_OWNER || this == EVENT_ADMIN;
    }

    /**
     * Get the roles that this role inherits from
     */
    public Set<EventRole> getInheritedRoles() {
        return switch (this) {
            case EVENT_OWNER -> Set.of(EVENT_ADMIN, EDITOR, CHECK_IN, VIEWER);
            case EVENT_ADMIN -> Set.of(EDITOR, CHECK_IN, VIEWER);
            case EDITOR -> Set.of(CHECK_IN, VIEWER);
            case CHECK_IN -> Set.of(VIEWER);
            case VIEWER -> Set.of();
        };
    }

    /**
     * Check if this role is higher than or equal to another role
     */
    public boolean isAtLeast(EventRole other) {
        if (this == other) return true;
        return getInheritedRoles().contains(other);
    }
}
