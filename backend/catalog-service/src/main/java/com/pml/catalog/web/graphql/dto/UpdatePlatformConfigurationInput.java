package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.ApprovalNotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GraphQL input for updating platform configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlatformConfigurationInput {

    private Integer approvalSlaHours;
    private Integer approvalWarningThresholdHours;
    private Boolean autoEscalationEnabled;
    private Integer escalationDelayHours;
    private String escalationRecipientRole;
    private Integer escalationReminderIntervalHours;
    private Integer maxEscalationReminders;
    private ApprovalNotificationChannel organizerNotificationChannel;
    private ApprovalNotificationChannel adminNotificationChannel;
    private Boolean sendSlaWarningNotifications;
    private Boolean sendEscalationNotifications;
    private Boolean requireCommentsOnRejection;
    private Boolean requireCommentsOnChangesRequested;
    private Boolean allowSelfApproval;
}
