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

    /** Platform default commission as a percentage, 0 to 50. */
    private java.math.BigDecimal commissionDefault;
    private java.math.BigDecimal minimumPayout;
    private Integer reservationHoldMinutes;
    private Integer reservationGraceMinutes;
    private Integer escrowHoldDays;
    private Integer refundCutoffHours;
    private Integer maxTicketsPerBooking;
    private Integer rescheduleLimit;
    private String currency;
    private java.util.List<RefundPolicyInput> refundPolicies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundPolicyInput {
        private String code;
        private String label;
        private String summary;
        private java.util.List<RefundRuleInput> rules;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRuleInput {
        private int daysBefore;
        private int percent;
    }
}
