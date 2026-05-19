package com.pml.catalog.domain.enums;

/**
 * ApprovalNotificationType Enum
 *
 * Defines the different types of notifications in the approval workflow.
 */
public enum ApprovalNotificationType {

    SUBMISSION_RECEIVED("SUBMISSION_RECEIVED", "Submission Received", "Organizer: your event was submitted"),
    REVIEW_ASSIGNED("REVIEW_ASSIGNED", "Review Assigned", "Admin: event assigned to you"),
    APPROVAL_GRANTED("APPROVAL_GRANTED", "Approval Granted", "Organizer: your event was approved"),
    REJECTION_ISSUED("REJECTION_ISSUED", "Rejection Issued", "Organizer: your event was rejected"),
    CHANGES_REQUESTED("CHANGES_REQUESTED", "Changes Requested", "Organizer: changes requested"),
    ESCALATION_TRIGGERED("ESCALATION_TRIGGERED", "Escalation Triggered", "Senior Admin: SLA breach escalation"),
    REMINDER_PENDING("REMINDER_PENDING", "Reminder Pending", "Admin: reminder for pending review"),
    SLA_WARNING("SLA_WARNING", "SLA Warning", "Admin: approaching SLA deadline");

    private final String code;
    private final String displayName;
    private final String description;

    ApprovalNotificationType(String code, String displayName, String description) {
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

    public static ApprovalNotificationType fromCode(String code) {
        for (ApprovalNotificationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this notification is for the organizer
     */
    public boolean isForOrganizer() {
        return this == SUBMISSION_RECEIVED || this == APPROVAL_GRANTED ||
               this == REJECTION_ISSUED || this == CHANGES_REQUESTED;
    }

    /**
     * Check if this notification is for an admin
     */
    public boolean isForAdmin() {
        return this == REVIEW_ASSIGNED || this == ESCALATION_TRIGGERED ||
               this == REMINDER_PENDING || this == SLA_WARNING;
    }

    /**
     * Check if this is an urgent notification that should be prioritized
     */
    public boolean isUrgent() {
        return this == ESCALATION_TRIGGERED || this == SLA_WARNING;
    }
}
