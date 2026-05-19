package com.pml.catalog.domain.enums;

/**
 * ApprovalAction Enum
 *
 * Defines the different actions that can occur in the event approval workflow.
 * Used in TimelineEvent to track the history of approval decisions.
 */
public enum ApprovalAction {

    SUBMITTED("SUBMITTED", "Submitted", "Organizer submitted event for review"),
    ASSIGNED("ASSIGNED", "Assigned", "Event assigned to reviewer"),
    VIEWED("VIEWED", "Viewed", "Reviewer viewed event details"),
    APPROVED("APPROVED", "Approved", "Event approved"),
    REJECTED("REJECTED", "Rejected", "Event rejected (final)"),
    CHANGES_REQUESTED("CHANGES_REQUESTED", "Changes Requested", "Reviewer requested changes"),
    RESUBMITTED("RESUBMITTED", "Resubmitted", "Organizer resubmitted after changes"),
    ESCALATED("ESCALATED", "Escalated", "Auto-escalated due to SLA breach"),
    ESCALATION_RESOLVED("ESCALATION_RESOLVED", "Escalation Resolved", "Escalation was handled"),
    COMMENT_ADDED("COMMENT_ADDED", "Comment Added", "Internal comment added");

    private final String code;
    private final String displayName;
    private final String description;

    ApprovalAction(String code, String displayName, String description) {
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

    public static ApprovalAction fromCode(String code) {
        for (ApprovalAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        return null;
    }

    /**
     * Check if this action results in a status change
     */
    public boolean changesStatus() {
        return this == SUBMITTED || this == APPROVED || this == REJECTED ||
               this == CHANGES_REQUESTED || this == RESUBMITTED;
    }

    /**
     * Check if this action is related to escalation
     */
    public boolean isEscalationRelated() {
        return this == ESCALATED || this == ESCALATION_RESOLVED;
    }
}
