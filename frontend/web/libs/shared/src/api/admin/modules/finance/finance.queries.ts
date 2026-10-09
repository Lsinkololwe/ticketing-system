/**
 * Admin finance queries — payout requests, refund requests, escrow accounts.
 *
 * <h2>Which pagination fields are real</h2>
 * The GraphQL `PaginationInfo` type declares ten fields, but booking-service's
 * Java record backs only six: `totalCount`, `pageSize`, `currentPage`,
 * `totalPages`, `hasNextPage`, `hasPreviousPage`. The other four
 * (`pageNumber`, `totalElements`, `hasNext`, `hasPrevious`) resolve to null on
 * every response. Selecting them would put a permanent null in the cache that
 * reads as "no more pages" — so this file selects only the six that exist.
 *
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 * @see backend/booking-service/src/main/java/com/pml/booking/web/graphql/dto/PaginationInfo.java
 */

import { gql } from '@apollo/client';

/** The six fields the server actually populates. */
const PAGINATION_FIELDS = gql`
  fragment FinancePaginationFields on PaginationInfo {
    totalCount
    pageSize
    currentPage
    totalPages
    hasNextPage
    hasPreviousPage
  }
`;

// =============================================================================
// PAYOUT REQUESTS
// =============================================================================

export const PAYOUT_LIST_FIELDS = gql`
  fragment PayoutListFields on PayoutRequest {
    id
    requestId
    organizerId
    organizerName
    eventTitle
    requestedAmount
    settledAmount
    currency
    status
    payoutMethod
    requestedAt
    approvedAt
    rejectedAt
    rejectionReason
    processedAt
    bankName
    accountNumber
    notes
  }
`;

export const ADMIN_PAYOUT_REQUESTS = gql`
  ${PAYOUT_LIST_FIELDS}
  ${PAGINATION_FIELDS}
  query AdminPayoutRequests(
    $filter: PayoutRequestFilterInput!
    $pagination: OffsetPaginationInput
  ) {
    payoutRequests(filter: $filter, pagination: $pagination) {
      data {
        ...PayoutListFields
      }
      pagination {
        ...FinancePaginationFields
      }
    }
  }
`;

/**
 * Platform-wide payout counters.
 *
 * `organizerId` is optional on the server; omitting it scopes to the whole
 * platform, which is what an admin tile means.
 */
export const PAYOUT_REQUEST_STATS = gql`
  query PayoutRequestStats {
    payoutRequestStats {
      totalPayoutRequests
      pendingPayoutRequests
      approvedPayoutRequests
      processingPayoutRequests
      completedPayoutRequests
      failedPayoutRequests
      totalPayoutAmount
      pendingPayoutAmount
    }
  }
`;

// =============================================================================
// REFUND REQUESTS
// =============================================================================

export const REFUND_LIST_FIELDS = gql`
  fragment RefundListFields on RefundRequest {
    id
    requestId
    ticketId
    ticketNumber
    eventId
    buyerId
    refundAmount
    netRefundAmount
    processingFee
    currency
    status
    requestType
    reason
    requestedAt
    reviewedAt
    rejectionReason
    processedAt
    policyApplied
  }
`;

export const ADMIN_REFUND_REQUESTS = gql`
  ${REFUND_LIST_FIELDS}
  ${PAGINATION_FIELDS}
  query AdminRefundRequests(
    $filter: RefundRequestFilterInput!
    $pagination: OffsetPaginationInput
  ) {
    refundRequests(filter: $filter, pagination: $pagination) {
      data {
        ...RefundListFields
      }
      pagination {
        ...FinancePaginationFields
      }
    }
  }
`;

/**
 * One refund count for one status.
 *
 * There is no platform-wide refund summary query — `eventRefundSummary` is
 * per-event — so the refund tiles ask for a single row per status and read
 * `totalCount`. Counting the rows of a page would only ever count that page.
 */
export const REFUND_STATUS_COUNT = gql`
  query RefundStatusCount($filter: RefundRequestFilterInput!) {
    refundRequests(
      filter: $filter
      pagination: { page: 0, size: 1 }
    ) {
      pagination {
        totalCount
      }
    }
  }
`;

// =============================================================================
// ESCROW ACCOUNTS
// =============================================================================

export const ESCROW_LIST_FIELDS = gql`
  fragment EscrowListFields on EventEscrowAccount {
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
    currency
    status
    lockUntil
    payoutEligibleAt
    closedAt
    createdAt
  }
`;

export const ADMIN_ESCROW_ACCOUNTS = gql`
  ${ESCROW_LIST_FIELDS}
  ${PAGINATION_FIELDS}
  query AdminEscrowAccounts(
    $filter: EscrowAccountFilterInput
    $pagination: OffsetPaginationInput
  ) {
    escrowAccounts(filter: $filter, pagination: $pagination) {
      data {
        ...EscrowListFields
      }
      pagination {
        ...FinancePaginationFields
      }
    }
  }
`;
