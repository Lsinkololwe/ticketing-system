import { gql } from '@apollo/client';

export const PLATFORM_CONFIG_FIELDS = gql`
  fragment PlatformConfigFields on PlatformConfiguration {
    id
    approvalSlaHours
    approvalWarningThresholdHours
    autoEscalationEnabled
    escalationDelayHours
    escalationRecipientRole
    escalationReminderIntervalHours
    maxEscalationReminders
    organizerNotificationChannel
    adminNotificationChannel
    sendSlaWarningNotifications
    sendEscalationNotifications
    requireCommentsOnRejection
    requireCommentsOnChangesRequested
    allowSelfApproval
    commissionDefault
    minimumPayout
    currency
    reservationHoldMinutes
    reservationGraceMinutes
    escrowHoldDays
    refundCutoffHours
    maxTicketsPerBooking
    rescheduleLimit
    refundPolicies {
      code
      label
      summary
      rules {
        daysBefore
        percent
      }
    }
    version
    updatedAt
    updatedBy
  }
`;

export const PLATFORM_CONFIGURATION = gql`
  ${PLATFORM_CONFIG_FIELDS}
  query PlatformConfiguration {
    platformConfiguration {
      ...PlatformConfigFields
    }
  }
`;

export const UPDATE_PLATFORM_CONFIGURATION = gql`
  ${PLATFORM_CONFIG_FIELDS}
  mutation UpdatePlatformConfiguration($input: UpdatePlatformConfigurationInput!) {
    updatePlatformConfiguration(input: $input) {
      ...PlatformConfigFields
    }
  }
`;
