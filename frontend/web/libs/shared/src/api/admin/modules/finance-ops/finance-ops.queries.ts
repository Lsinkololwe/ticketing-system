/**
 * Finance operations — queries and mutations for the platform-admin Finance
 * module that the base `finance` module does not cover (rich payout and refund
 * rows, payout recovery actions, escrow lock/close, chargebacks).
 *
 * Operation names and arguments are copied from booking-service
 * `schema.graphqls`; nothing here is invented.
 */

import { gql } from '@apollo/client';

const PAGE = gql`
  fragment FinanceOpsPage on PaginationInfo {
    totalCount
    pageSize
    currentPage
    totalPages
    hasNextPage
    hasPreviousPage
  }
`;

// ----------------------------------------------------------------- payouts

export const PAYOUT_OPS_FIELDS = gql`
  fragment PayoutOpsFields on PayoutRequest {
    id
    requestId
    organizerId
    organizerName
    eventId
    eventTitle
    escrowAccountId
    bankAccountId
    requestedAmount
    taxAmount
    settledAmount
    currency
    status
    payoutMethod
    requestedAt
    requestedById
    approvedAt
    approvedBy
    rejectedAt
    rejectionReason
    processedAt
    expectedPayoutDate
    paymentReference
    externalTransactionId
    bankName
    accountNumber
    retryCount
    lastError
    notes
    issueType
    reviewStatus
    isStuck
    stuckReason
    reviewNotes
    resolutionNotes
    bankAccount {
      id
      isVerified
      status
      accountHolderName
    }
  }
`;

export const PAYOUT_OPS_LIST = gql`
  ${PAYOUT_OPS_FIELDS}
  ${PAGE}
  query PayoutOpsList($filter: PayoutRequestFilterInput!, $pagination: OffsetPaginationInput) {
    payoutRequests(filter: $filter, pagination: $pagination) {
      data {
        ...PayoutOpsFields
      }
      pagination {
        ...FinanceOpsPage
      }
    }
  }
`;

export const PAYOUT_OPS_DETAIL = gql`
  ${PAYOUT_OPS_FIELDS}
  query PayoutOpsDetail($id: ID!) {
    payoutRequest(id: $id) {
      ...PayoutOpsFields
    }
  }
`;

export const PROCESS_PAYOUT_REQUEST = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation ProcessPayoutRequest($payoutRequestId: ID!) {
    processPayoutRequest(payoutRequestId: $payoutRequestId) {
      ...PayoutOpsFields
    }
  }
`;

export const COMPLETE_PAYOUT_REQUEST = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation CompletePayoutRequest($payoutRequestId: ID!, $bankReference: String!) {
    completePayoutRequest(payoutRequestId: $payoutRequestId, bankReference: $bankReference) {
      ...PayoutOpsFields
    }
  }
`;

export const RETRY_PAYOUT_REQUEST = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation RetryPayoutRequest($payoutRequestId: ID!) {
    retryPayoutRequest(payoutRequestId: $payoutRequestId) {
      ...PayoutOpsFields
    }
  }
`;

export const RESUME_PAYOUT_REQUEST = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation ResumePayoutRequest($payoutRequestId: ID!) {
    resumePayoutRequest(payoutRequestId: $payoutRequestId) {
      ...PayoutOpsFields
    }
  }
`;

export const MARK_PAYOUT_FOR_REVIEW = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation MarkPayoutForReview($payoutRequestId: ID!, $issueType: PayoutIssueType!, $notes: String) {
    markPayoutForReview(payoutRequestId: $payoutRequestId, issueType: $issueType, notes: $notes) {
      ...PayoutOpsFields
    }
  }
`;

export const RESOLVE_PAYOUT_ISSUE = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation ResolvePayoutIssue($payoutRequestId: ID!, $resolutionType: PayoutResolutionType!, $notes: String!) {
    resolvePayoutIssue(payoutRequestId: $payoutRequestId, resolutionType: $resolutionType, notes: $notes) {
      ...PayoutOpsFields
    }
  }
`;

export const ESCALATE_PAYOUT_REQUEST = gql`
  ${PAYOUT_OPS_FIELDS}
  mutation EscalatePayoutRequest($payoutRequestId: ID!, $reason: String!) {
    escalatePayoutRequest(payoutRequestId: $payoutRequestId, reason: $reason) {
      ...PayoutOpsFields
    }
  }
`;

export const BULK_RETRY_FAILED_PAYOUTS = gql`
  mutation BulkRetryFailedPayouts($payoutRequestIds: [ID!]!) {
    bulkRetryFailedPayouts(payoutRequestIds: $payoutRequestIds) {
      processedCount
      failedCount
      failedPayoutIds
    }
  }
`;

// ----------------------------------------------------------------- refunds

export const REFUND_OPS_FIELDS = gql`
  fragment RefundOpsFields on RefundRequest {
    id
    requestId
    ticketId
    ticketNumber
    eventId
    buyerId
    originalTicketPrice
    refundAmount
    refundPercentage
    platformRetains
    processingFee
    netRefundAmount
    currency
    status
    requestType
    reason
    additionalNotes
    requestedById
    requestedAt
    reviewedBy
    reviewedAt
    reviewComments
    rejectionReason
    processedAt
    paymentReference
    originalPaymentMethod
    daysBeforeEvent
    policyApplied
  }
`;

export const REFUND_OPS_LIST = gql`
  ${REFUND_OPS_FIELDS}
  ${PAGE}
  query RefundOpsList($filter: RefundRequestFilterInput!, $pagination: OffsetPaginationInput) {
    refundRequests(filter: $filter, pagination: $pagination) {
      data {
        ...RefundOpsFields
      }
      pagination {
        ...FinanceOpsPage
      }
    }
  }
`;

export const REFUND_OPS_DETAIL = gql`
  ${REFUND_OPS_FIELDS}
  query RefundOpsDetail($id: ID!) {
    refundRequest(id: $id) {
      ...RefundOpsFields
    }
  }
`;

export const PROCESS_REFUND_REQUEST = gql`
  ${REFUND_OPS_FIELDS}
  mutation ProcessRefundRequest($refundRequestId: ID!) {
    processRefundRequest(refundRequestId: $refundRequestId) {
      ...RefundOpsFields
    }
  }
`;

export const BULK_APPROVE_REFUNDS = gql`
  mutation BulkApproveRefunds($refundRequestIds: [ID!]!) {
    bulkApproveRefunds(refundRequestIds: $refundRequestIds) {
      processedCount
      failedCount
    }
  }
`;

export const CREATE_ADMIN_REFUND_REQUEST = gql`
  ${REFUND_OPS_FIELDS}
  mutation CreateAdminRefundRequest($ticketId: ID!, $reason: String!, $bypassApproval: Boolean) {
    createAdminRefundRequest(ticketId: $ticketId, reason: $reason, bypassApproval: $bypassApproval) {
      ...RefundOpsFields
    }
  }
`;

// ------------------------------------------------------------------ escrow

export const ESCROW_OPS_DETAIL = gql`
  query EscrowOpsDetail($id: ID!) {
    escrowAccount(id: $id) {
      id
      accountNumber
      eventId
      eventTitle
      organizerId
      organizerName
      currentBalance
      totalDeposits
      totalWithdrawals
      totalRefunds
      totalCommissions
      pendingWithdrawals
      currency
      status
      lockUntil
      payoutEligibleAt
      closedAt
      createdAt
    }
  }
`;

export const ESCROW_TRANSACTIONS = gql`
  ${PAGE}
  query EscrowOpsTransactions($escrowAccountId: String!, $pagination: OffsetPaginationInput) {
    escrowTransactions(escrowAccountId: $escrowAccountId, pagination: $pagination) {
      data {
        id
        type
        category
        amount
        balanceAfter
        currency
        description
        payoutRequestId
        refundRequestId
        chargebackId
        timestamp
      }
      pagination {
        ...FinanceOpsPage
      }
    }
  }
`;

export const ESCROW_JOURNAL_VERIFICATION_ALL = gql`
  query EscrowJournalVerificationAll {
    escrowJournalVerificationAll {
      eventId
      escrowAccountId
      escrowBalance
      journalBalance
      variance
      isConsistent
      status
    }
  }
`;

export const LOCK_ESCROW_ACCOUNT = gql`
  mutation LockEscrowAccount($accountId: ID!, $lockUntil: DateTime!, $reason: String!) {
    lockEscrowAccount(accountId: $accountId, lockUntil: $lockUntil, reason: $reason) {
      id
      accountNumber
      status
      lockUntil
      payoutEligibleAt
      closedAt
      currentBalance
    }
  }
`;

export const UNLOCK_ESCROW_ACCOUNT = gql`
  mutation UnlockEscrowAccount($accountId: ID!, $reason: String!) {
    unlockEscrowAccount(accountId: $accountId, reason: $reason) {
      id
      accountNumber
      status
      lockUntil
      payoutEligibleAt
      closedAt
      currentBalance
    }
  }
`;

export const MARK_PAYOUT_ELIGIBLE = gql`
  mutation MarkPayoutEligible($accountId: ID!) {
    markPayoutEligible(accountId: $accountId) {
      id
      accountNumber
      status
      lockUntil
      payoutEligibleAt
      closedAt
      currentBalance
    }
  }
`;

export const CLOSE_ESCROW_ACCOUNT = gql`
  mutation CloseEscrowAccount($accountId: ID!, $reason: String!) {
    closeEscrowAccount(accountId: $accountId, reason: $reason) {
      id
      accountNumber
      status
      lockUntil
      payoutEligibleAt
      closedAt
      currentBalance
    }
  }
`;

// ------------------------------------------------------------- chargebacks

export const CHARGEBACK_FIELDS = gql`
  fragment ChargebackOpsFields on ChargebackRecord {
    id
    chargebackId
    originalTransactionId
    ticketId
    eventId
    organizerId
    customerId
    originalAmount
    chargebackAmount
    chargebackFee
    currency
    reason
    status
    receivedAt
    responseDeadline
    evidenceSubmitted
    resolvedAt
    recoveryStatus
    recoveredAmount
    fundSource
    createdAt
  }
`;

export const CHARGEBACK_LIST = gql`
  ${CHARGEBACK_FIELDS}
  ${PAGE}
  query ChargebackOpsList($filter: ChargebackFilterInput, $pagination: OffsetPaginationInput) {
    chargebacks(filter: $filter, pagination: $pagination) {
      data {
        ...ChargebackOpsFields
      }
      pagination {
        ...FinanceOpsPage
      }
    }
  }
`;

export const CHARGEBACK_PENDING = gql`
  query ChargebackOpsPending {
    pendingChargebacks {
      id
      chargebackAmount
      responseDeadline
      status
    }
    chargebacksPendingRecovery {
      id
    }
  }
`;

export const CHARGEBACK_STATS = gql`
  query ChargebackOpsStats {
    chargebackStats {
      totalCount
      pendingCount
      disputedCount
      wonCount
      lostCount
      totalAmount
      recoveredAmount
      writtenOffAmount
      winRate
    }
  }
`;

export const RECEIVE_CHARGEBACK = gql`
  ${CHARGEBACK_FIELDS}
  mutation ReceiveChargeback($input: ReceiveChargebackInput!) {
    receiveChargeback(input: $input) {
      ...ChargebackOpsFields
    }
  }
`;

export const START_CHARGEBACK_REVIEW = gql`
  ${CHARGEBACK_FIELDS}
  mutation StartChargebackReview($id: ID!, $notes: String) {
    startChargebackReview(id: $id, notes: $notes) {
      ...ChargebackOpsFields
    }
  }
`;

export const ACCEPT_CHARGEBACK = gql`
  ${CHARGEBACK_FIELDS}
  mutation AcceptChargeback($id: ID!, $reason: String) {
    acceptChargeback(id: $id, reason: $reason) {
      ...ChargebackOpsFields
    }
  }
`;

export const DISPUTE_CHARGEBACK = gql`
  ${CHARGEBACK_FIELDS}
  mutation DisputeChargeback($id: ID!, $input: DisputeChargebackInput!) {
    disputeChargeback(id: $id, input: $input) {
      ...ChargebackOpsFields
    }
  }
`;

export const RECORD_CHARGEBACK_OUTCOME = gql`
  ${CHARGEBACK_FIELDS}
  mutation RecordChargebackOutcome($id: ID!, $won: Boolean!, $notes: String) {
    recordChargebackOutcome(id: $id, won: $won, notes: $notes) {
      ...ChargebackOpsFields
    }
  }
`;

// -------------------------------------------------------------- bank accts

export const VERIFY_BANK_ACCOUNT = gql`
  mutation VerifyBankAccount($id: ID!) {
    verifyBankAccount(id: $id) {
      id
      isVerified
      status
      verifiedAt
    }
  }
`;
