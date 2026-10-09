/**
 * Payments operations documents (booking-service): payment attempt search and recovery,
 * payment risk, dual control, commission records, gateway settlements, platform account
 * transfers, payout hold and release, purchase heat map and bookings by buyer.
 */
import { gql } from '@apollo/client';

const PAGINATION = gql`
  fragment OpsPaginationFields on PaginationInfo {
    totalCount
    pageSize
    currentPage
    totalPages
    hasNextPage
    hasPreviousPage
  }
`;

export const OPS_ATTEMPT_FIELDS = gql`
  fragment OpsAttemptFields on PaymentAttempt {
    id
    depositId
    attemptNumber
    ticketId
    eventId
    buyerId
    amount
    currency
    provider
    payerPhone
    status
    providerStatus
    providerTransactionId
    failureCode
    failureMessage
    webhookProcessed
    retryCount
    lastError
    fulfilled
    reviewStatus
    reviewedBy
    reviewedAt
    reviewNotes
    notes
    riskScore
    riskLevel
    riskFlags
    createdAt
    updatedAt
    expiresAt
  }
`;

export const OPS_PAYMENT_ATTEMPT_SEARCH = gql`
  ${OPS_ATTEMPT_FIELDS}
  ${PAGINATION}
  query OpsPaymentAttemptSearch($filter: PaymentAttemptFilterInput, $pagination: OffsetPaginationInput) {
    paymentAttemptSearch(filter: $filter, pagination: $pagination) {
      data {
        ...OpsAttemptFields
      }
      pagination {
        ...OpsPaginationFields
      }
    }
  }
`;

export const OPS_STUCK_TRANSACTIONS = gql`
  ${OPS_ATTEMPT_FIELDS}
  ${PAGINATION}
  query OpsStuckTransactions($minutes: Int, $pagination: OffsetPaginationInput) {
    stuckTransactions(minutes: $minutes, pagination: $pagination) {
      data {
        ...OpsAttemptFields
      }
      pagination {
        ...OpsPaginationFields
      }
    }
  }
`;

export const OPS_RESUME_PAYMENT_ATTEMPT = gql`
  ${OPS_ATTEMPT_FIELDS}
  mutation OpsResumePaymentAttempt($depositId: String!) {
    resumePaymentAttempt(depositId: $depositId) {
      ...OpsAttemptFields
    }
  }
`;

export const OPS_RETRY_PAYMENT_ATTEMPTS = gql`
  mutation OpsRetryPaymentAttempts($depositIds: [String!]!) {
    retryPaymentAttempts(depositIds: $depositIds) {
      depositId
      result
      detail
    }
  }
`;

export const OPS_RECOVERY_PROPOSAL_FIELDS = gql`
  fragment OpsRecoveryProposalFields on RecoveryProposal {
    id
    action
    amount
    canConfirm
    confirmationReason
    confirmedAt
    confirmedById
    expiresAt
    failureReason
    outcome
    proposalReason
    proposedAt
    proposedById
    status
    subjectIds
    subjectType
  }
`;

export const OPS_FORCE_COMPLETE_ATTEMPTS = gql`
  ${OPS_RECOVERY_PROPOSAL_FIELDS}
  mutation OpsForceCompletePaymentAttempts($depositIds: [String!]!, $reason: String!) {
    forceCompletePaymentAttempts(depositIds: $depositIds, reason: $reason) {
      ...OpsRecoveryProposalFields
    }
  }
`;

export const OPS_DUAL_CONTROL_QUEUE = gql`
  ${OPS_RECOVERY_PROPOSAL_FIELDS}
  query OpsDualControlQueue {
    dualControlQueue {
      ...OpsRecoveryProposalFields
    }
  }
`;

export const OPS_CONFIRM_RECOVERY_ACTION = gql`
  ${OPS_RECOVERY_PROPOSAL_FIELDS}
  mutation OpsConfirmRecoveryAction($proposalId: ID!, $reason: String!) {
    confirmRecoveryAction(proposalId: $proposalId, reason: $reason) {
      ...OpsRecoveryProposalFields
    }
  }
`;

export const OPS_WITHDRAW_RECOVERY_PROPOSAL = gql`
  ${OPS_RECOVERY_PROPOSAL_FIELDS}
  mutation OpsWithdrawRecoveryProposal($proposalId: ID!) {
    withdrawRecoveryProposal(proposalId: $proposalId) {
      ...OpsRecoveryProposalFields
    }
  }
`;

export const OPS_PAYMENT_RISK_SUMMARY = gql`
  query OpsPaymentRiskSummary($windowHours: Int) {
    paymentRiskSummary(windowHours: $windowHours) {
      windowHours
      evaluated
      flagged
      high
      medium
      low
      amountAtRisk
      topFlags {
        flag
        count
      }
    }
  }
`;

export const OPS_COMMISSION_RECORDS = gql`
  ${PAGINATION}
  query OpsCommissionRecords($filter: CommissionFilterInput, $pagination: OffsetPaginationInput) {
    commissionRecords(filter: $filter, pagination: $pagination) {
      data {
        id
        eventId
        organizationId
        ticketId
        ticketPrice
        rate
        amount
        currency
        status
        earnedAt
        pendingAt
        cancelledAt
        clawedBackAt
        refundReason
        createdAt
      }
      pagination {
        ...OpsPaginationFields
      }
      totals {
        earned
        pending
        cancelled
        clawedBack
      }
    }
  }
`;

export const OPS_GATEWAY_SETTLEMENTS = gql`
  ${PAGINATION}
  query OpsGatewaySettlements($filter: GatewaySettlementFilterInput, $pagination: OffsetPaginationInput) {
    gatewaySettlements(filter: $filter, pagination: $pagination) {
      data {
        settlementId
        settlementDate
        grossAmount
        feeAmount
        netAmount
        currency
        bankReference
        entryNumber
        journalEntryId
        postedAt
      }
      pagination {
        ...OpsPaginationFields
      }
    }
  }
`;

export const OPS_TRANSFER_PLATFORM_ACCOUNTS = gql`
  ${OPS_RECOVERY_PROPOSAL_FIELDS}
  mutation OpsTransferBetweenPlatformAccounts($input: PlatformTransferInput!) {
    transferBetweenPlatformAccounts(input: $input) {
      executed
      requiresSecondApprover
      proposal {
        ...OpsRecoveryProposalFields
      }
      transfer {
        id
        fromAccount
        toAccount
        amount
        currency
        reason
        executedBy
        createdAt
      }
    }
  }
`;

export const OPS_HOLD_PAYOUT = gql`
  mutation OpsHoldPayoutRequest($payoutRequestId: ID!, $reason: String!) {
    holdPayoutRequest(payoutRequestId: $payoutRequestId, reason: $reason) {
      id
      status
    }
  }
`;

export const OPS_RELEASE_PAYOUT_HOLD = gql`
  mutation OpsReleasePayoutHold($payoutRequestId: ID!, $note: String) {
    releasePayoutHold(payoutRequestId: $payoutRequestId, note: $note) {
      id
      status
    }
  }
`;

export const OPS_PURCHASES_BY_DAY_AND_HOUR = gql`
  query OpsPurchasesByDayAndHour($from: DateTime, $to: DateTime, $eventId: ID, $organizationId: ID) {
    purchasesByDayAndHour(from: $from, to: $to, eventId: $eventId, organizationId: $organizationId) {
      dayOfWeek
      hour
      purchases
      tickets
      revenue
    }
  }
`;

export const OPS_BOOKINGS_BY_BUYER = gql`
  ${PAGINATION}
  query OpsBookingsByBuyer($buyerId: String!, $filter: BookingFilterInput, $pagination: OffsetPaginationInput) {
    bookingsByBuyer(buyerId: $buyerId, filter: $filter, pagination: $pagination) {
      data {
        id
        bookingNumber
        eventId
        eventTitle
        eventDate
        status
        ticketCount
        totalAmount
        currency
        refundedAmount
        createdAt
      }
      pagination {
        ...OpsPaginationFields
      }
    }
  }
`;
