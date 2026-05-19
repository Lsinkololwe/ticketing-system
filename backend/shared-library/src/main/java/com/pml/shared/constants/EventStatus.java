package com.pml.shared.constants;

/**
 * Event Status Enum
 *
 * Defines the different states of events in the system.
 */
public enum EventStatus {

    DRAFT("DRAFT", "Draft", "Event is in draft state"),
    PENDING_APPROVAL("PENDING_APPROVAL", "Pending Approval", "Event is pending approval"),
    CHANGES_REQUESTED("CHANGES_REQUESTED", "Changes Requested", "Reviewer requested changes to the event"),
    APPROVED("APPROVED", "Approved", "Event has been approved"),
    REJECTED("REJECTED", "Rejected", "Event has been rejected"),
    PUBLISHED("PUBLISHED", "Published", "Event is published and visible"),
    CANCELLED("CANCELLED", "Cancelled", "Event has been cancelled"),
    COMPLETED("COMPLETED", "Completed", "Event has been completed");

    private final String code;
    private final String displayName;
    private final String description;

    EventStatus(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static EventStatus fromCode(String code) {
        for (EventStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isActive() {
        return this == PUBLISHED;
    }

    public boolean isEditable() {
        return this == DRAFT || this == REJECTED || this == CHANGES_REQUESTED;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
