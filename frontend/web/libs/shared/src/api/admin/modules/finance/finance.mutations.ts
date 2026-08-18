/**
 * Admin finance decisions — approve and reject payouts and refunds.
 *
 * <h2>Rejection always carries a reason</h2>
 * Both reject mutations take `rejectionReason: String!`. That is not a form
 * nicety: the reason is what the organizer or customer is shown, and what the
 * next reviewer reads when the same request comes back. The UI must not send a
 * placeholder to satisfy the non-null.
 *
 * <h2>Approve is not the end of the money</h2>
 * `approvePayoutRequest` authorises the payout; `processPayoutRequest` and then
 * `completePayoutRequest(bankReference:)` actually move it and write the
 * accounting entries. Nothing here should imply funds have left.
 *
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import {
  ESCROW_LIST_FIELDS,
  PAYOUT_LIST_FIELDS,
  REFUND_LIST_FIELDS,
} from './finance.queries';

export const APPROVE_PAYOUT_REQUEST = gql`
  ${PAYOUT_LIST_FIELDS}
  mutation ApprovePayoutRequest($payoutRequestId: ID!, $notes: String) {
    approvePayoutRequest(payoutRequestId: $payoutRequestId, notes: $notes) {
      success
      message
      errors
      data {
        ...PayoutListFields
      }
    }
  }
`;

export const REJECT_PAYOUT_REQUEST = gql`
  ${PAYOUT_LIST_FIELDS}
  mutation RejectPayoutRequest($payoutRequestId: ID!, $rejectionReason: String!) {
    rejectPayoutRequest(
      payoutRequestId: $payoutRequestId
      rejectionReason: $rejectionReason
    ) {
      success
      message
      errors
      data {
        ...PayoutListFields
      }
    }
  }
`;

export const APPROVE_REFUND_REQUEST = gql`
  ${REFUND_LIST_FIELDS}
  mutation ApproveRefundRequest($refundRequestId: ID!, $reviewComments: String) {
    approveRefundRequest(
      refundRequestId: $refundRequestId
      reviewComments: $reviewComments
    ) {
      success
      message
      errors
      data {
        ...RefundListFields
      }
    }
  }
`;

export const REJECT_REFUND_REQUEST = gql`
  ${REFUND_LIST_FIELDS}
  mutation RejectRefundRequest($refundRequestId: ID!, $rejectionReason: String!) {
    rejectRefundRequest(
      refundRequestId: $refundRequestId
      rejectionReason: $rejectionReason
    ) {
      success
      message
      errors
      data {
        ...RefundListFields
      }
    }
  }
`;

/**
 * Suspend or reactivate an escrow account.
 *
 * ET-FIN-001 R4: SUSPENDED "ends when a person lifts it, not on a date" — which
 * is why this takes a reason and not an expiry. Locking for a fixed period is a
 * different operation (`lockEscrowAccount`) and is not this control.
 */
export const UPDATE_ESCROW_ACCOUNT_STATUS = gql`
  ${ESCROW_LIST_FIELDS}
  mutation UpdateEscrowAccountStatus(
    $accountId: ID!
    $status: EscrowAccountStatus!
    $reason: String
  ) {
    updateEscrowAccountStatus(accountId: $accountId, status: $status, reason: $reason) {
      success
      message
      errors
      data {
        ...EscrowListFields
      }
    }
  }
`;
