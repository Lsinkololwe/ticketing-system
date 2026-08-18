/**
 * Payout recovery queries — `Admin - Transaction Recovery.dc.html`.
 *
 * <h2>Scope</h2>
 * The design's queue gathers eight sources (stuck payments, orphaned webhooks,
 * disputed webhooks, failed payouts, unconfirmed payouts, reconciliation items,
 * failed refunds, dead letters). Only the payout sources are queryable today —
 * booking-service exposes `stuckPayoutRequests…`, `retryablePayoutRequests…`,
 * `payoutRequestsForReview…` and `payoutRecoverySummary`, and nothing else. The
 * remaining six have no field to select.
 *
 * <p>So this file asks for the payout slice and the screen says so. A queue
 * that silently covers a quarter of what it claims is worse than one that names
 * its scope: an operator who believes the queue is complete stops looking.
 *
 * @see backend/booking-service/src/main/java/com/pml/booking/web/graphql/query/PayoutRequestQueryResolver.java
 */

import { gql } from '@apollo/client';
import { PAYOUT_LIST_FIELDS } from './finance.queries';

/** Recovery-specific fields, on top of the shared payout list fragment. */
export const PAYOUT_RECOVERY_FIELDS = gql`
  fragment PayoutRecoveryFields on PayoutRequest {
    issueType
    reviewStatus
    isStuck
    stuckReason
    stuckAt
    reviewedAt
    reviewNotes
    retryCount
    lastError
  }
`;

export const PAYOUT_RECOVERY_SUMMARY = gql`
  query PayoutRecoverySummary {
    payoutRecoverySummary {
      totalPayoutsForReview
      pendingReviewCount
      underReviewCount
      stuckPayoutsCount
      retryablePayoutsCount
      recentlyResolvedCount
      averageResolutionTimeMinutes
      totalAmountAtRisk
      issuesByType {
        issueType
        count
        percentage
        unresolvedCount
        totalAmount
      }
    }
  }
`;

export const STUCK_PAYOUT_REQUESTS = gql`
  ${PAYOUT_LIST_FIELDS}
  ${PAYOUT_RECOVERY_FIELDS}
  query StuckPayoutRequests($pagination: OffsetPaginationInput) {
    stuckPayoutRequestsOffsetPagination(pagination: $pagination) {
      data {
        ...PayoutListFields
        ...PayoutRecoveryFields
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

export const RETRYABLE_PAYOUT_REQUESTS = gql`
  ${PAYOUT_LIST_FIELDS}
  ${PAYOUT_RECOVERY_FIELDS}
  query RetryablePayoutRequests($pagination: OffsetPaginationInput) {
    retryablePayoutRequestsOffsetPagination(pagination: $pagination) {
      data {
        ...PayoutListFields
        ...PayoutRecoveryFields
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

export const PAYOUTS_FOR_REVIEW = gql`
  ${PAYOUT_LIST_FIELDS}
  ${PAYOUT_RECOVERY_FIELDS}
  query PayoutsForReview(
    $reviewStatus: PayoutReviewStatus
    $pagination: OffsetPaginationInput
  ) {
    payoutRequestsForReviewOffsetPagination(
      reviewStatus: $reviewStatus
      pagination: $pagination
    ) {
      data {
        ...PayoutListFields
        ...PayoutRecoveryFields
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

/**
 * Retry a payout the platform believes is recoverable.
 *
 * Idempotency is enforced by the unique sparse index on `idempotencyKey` — a
 * double-click cannot produce a second payout of the same money.
 */
export const RETRY_PAYOUT_REQUEST = gql`
  mutation RetryPayoutRequest($payoutRequestId: ID!) {
    retryPayoutRequest(payoutRequestId: $payoutRequestId) {
      success
      message
      errors
    }
  }
`;

export const MARK_PAYOUT_FOR_REVIEW = gql`
  mutation MarkPayoutForReview(
    $payoutRequestId: ID!
    $issueType: PayoutIssueType!
    $notes: String
  ) {
    markPayoutForReview(
      payoutRequestId: $payoutRequestId
      issueType: $issueType
      notes: $notes
    ) {
      success
      message
      errors
    }
  }
`;
