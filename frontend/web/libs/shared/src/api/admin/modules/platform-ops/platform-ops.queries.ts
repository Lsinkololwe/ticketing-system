/**
 * Platform operations documents (identity-service): service health, alerts, announcements,
 * audit log, staff accounts, own sessions and profile, growth series, payout-account queue,
 * per-organization commission. Every operation exists in identity-service schema.graphqls.
 */
import { gql } from '@apollo/client';

const PAGE_INFO = gql`
  fragment OpsPageInfoFields on PageInfo {
    totalCount
    pageSize
    currentPage
    totalPages
    hasNextPage
    hasPreviousPage
  }
`;

export const OPS_SERVICE_HEALTH = gql`
  query OpsServiceHealth {
    serviceHealth {
      name
      status
      latencyMillis
      checkedAt
      detail
    }
  }
`;

export const OPS_SYSTEM_ALERT_FIELDS = gql`
  fragment OpsSystemAlertFields on SystemAlert {
    id
    source
    key
    severity
    title
    message
    status
    occurrences
    raisedAt
    lastSeenAt
    acknowledgedAt
    acknowledgedBy
    resolvedAt
  }
`;

export const OPS_SYSTEM_ALERTS = gql`
  ${OPS_SYSTEM_ALERT_FIELDS}
  query OpsSystemAlerts($status: AlertStatus, $severity: AlertSeverity) {
    systemAlerts(status: $status, severity: $severity) {
      ...OpsSystemAlertFields
    }
  }
`;

export const OPS_ACKNOWLEDGE_ALERT = gql`
  ${OPS_SYSTEM_ALERT_FIELDS}
  mutation OpsAcknowledgeAlert($id: ID!) {
    acknowledgeAlert(id: $id) {
      ...OpsSystemAlertFields
    }
  }
`;

export const OPS_ANNOUNCEMENT_FIELDS = gql`
  fragment OpsAnnouncementFields on SystemAnnouncement {
    id
    title
    message
    segment
    severity
    startsAt
    endsAt
    cancelledAt
    createdAt
  }
`;

export const OPS_SYSTEM_ANNOUNCEMENTS = gql`
  ${OPS_ANNOUNCEMENT_FIELDS}
  query OpsSystemAnnouncements {
    systemAnnouncements {
      ...OpsAnnouncementFields
    }
  }
`;

export const OPS_BROADCAST_NOTIFICATION = gql`
  ${OPS_ANNOUNCEMENT_FIELDS}
  mutation OpsBroadcastNotification($input: BroadcastInput!) {
    broadcastNotification(input: $input) {
      ...OpsAnnouncementFields
    }
  }
`;

export const OPS_CANCEL_ANNOUNCEMENT = gql`
  ${OPS_ANNOUNCEMENT_FIELDS}
  mutation OpsCancelAnnouncement($id: ID!) {
    cancelAnnouncement(id: $id) {
      ...OpsAnnouncementFields
    }
  }
`;

export const OPS_AUDIT_LOGS = gql`
  ${PAGE_INFO}
  query OpsAuditLogs($filter: AuditLogFilterInput, $pagination: OffsetPaginationInput) {
    auditLogs(filter: $filter, pagination: $pagination) {
      content {
        id
        action
        actorId
        at
        metadata
        resourceId
        resourceType
        source
        status
        subjectId
      }
      pageInfo {
        ...OpsPageInfoFields
      }
    }
  }
`;

export const OPS_STAFF_ACCOUNTS = gql`
  ${PAGE_INFO}
  query OpsStaffAccounts($search: String, $role: UserType, $pagination: OffsetPaginationInput) {
    staffAccounts(search: $search, role: $role, pagination: $pagination) {
      content {
        id
        email
        fullName
        phoneNumber
        roles
        accountStatus
        locked
        twoFactorEnabled
        lastLoginAt
        createdAt
      }
      pageInfo {
        ...OpsPageInfoFields
      }
    }
  }
`;

export const OPS_CREATE_STAFF = gql`
  mutation OpsCreateStaff($input: CreateUserInput!) {
    createUser(input: $input) {
      id
      email
      fullName
      roles
    }
  }
`;

export const OPS_DELETE_USER = gql`
  mutation OpsDeleteUser($id: ID!) {
    deleteUser(id: $id) {
      id
      accountStatus
    }
  }
`;

export const OPS_MY_SESSIONS = gql`
  query OpsMySessions {
    mySessions {
      id
      current
      clients
      ipAddress
      startedAt
      lastAccessAt
    }
  }
`;

export const OPS_REVOKE_SESSION = gql`
  mutation OpsRevokeSession($sessionId: ID!) {
    revokeSession(sessionId: $sessionId)
  }
`;

export const OPS_UPDATE_MY_PROFILE = gql`
  mutation OpsUpdateMyProfile($input: UpdateUserInput!) {
    updateMyProfile(input: $input) {
      id
      firstName
      lastName
      fullName
      displayName
    }
  }
`;

export const OPS_ME_SECURITY = gql`
  query OpsMeSecurity {
    me {
      id
      firstName
      lastName
      displayName
      fullName
      email
      phoneNumber
      twoFactorEnabled
      lastLoginAt
    }
  }
`;

export const OPS_USER_GROWTH_SERIES = gql`
  query OpsUserGrowthSeries($from: DateTime!, $to: DateTime!, $bucket: GrowthBucket, $role: UserType) {
    userGrowthSeries(from: $from, to: $to, bucket: $bucket, role: $role) {
      bucketStart
      newUsers
      cumulative
    }
  }
`;

export const OPS_PAYOUT_ACCOUNTS = gql`
  ${PAGE_INFO}
  query OpsPayoutAccounts($filter: PayoutAccountFilterInput, $pagination: OffsetPaginationInput) {
    bankAccounts(filter: $filter, pagination: $pagination) {
      content {
        organizationId
        organizationName
        organizationSlug
        method
        status
        bankName
        accountHolderName
        accountNumberMasked
        network
        phoneMasked
        rejectionReason
        suspendedReason
        testDepositSentAt
        verificationAttemptsLeft
        updatedAt
      }
      pageInfo {
        ...OpsPageInfoFields
      }
    }
  }
`;

export const OPS_REJECT_BANK_ACCOUNT = gql`
  mutation OpsRejectBankAccount($organizationId: ID!, $reason: String!) {
    rejectBankAccount(organizationId: $organizationId, reason: $reason) {
      id
    }
  }
`;

export const OPS_SUSPEND_BANK_ACCOUNT = gql`
  mutation OpsSuspendBankAccount($organizationId: ID!, $reason: String!) {
    suspendBankAccount(organizationId: $organizationId, reason: $reason) {
      id
    }
  }
`;

export const OPS_REINSTATE_BANK_ACCOUNT = gql`
  mutation OpsReinstateBankAccount($organizationId: ID!) {
    reinstateBankAccount(organizationId: $organizationId) {
      id
    }
  }
`;

export const OPS_REJECT_PAYOUT_ACCOUNT = gql`
  mutation OpsRejectPayoutAccount($organizationId: ID!, $reason: String!) {
    rejectPayoutAccount(organizationId: $organizationId, reason: $reason) {
      id
    }
  }
`;

export const OPS_SET_ORG_COMMISSION = gql`
  mutation OpsSetOrganizationCommissionRate($organizationId: ID!, $rate: Float!, $reason: String) {
    setOrganizationCommissionRate(organizationId: $organizationId, rate: $rate, reason: $reason) {
      id
      commissionRate
    }
  }
`;
