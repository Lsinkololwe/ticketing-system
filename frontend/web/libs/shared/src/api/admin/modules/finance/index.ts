/**
 * Finance Module (Admin App)
 *
 * Payout requests, refund requests and escrow accounts — the three views of
 * `Admin - Finance.dc.html`, all owned by booking-service.
 */

export {
  useAdminPayoutRequests,
  usePayoutRequestStats,
  useAdminRefundRequests,
  useRefundStatusCount,
  useAdminEscrowAccounts,
  useFinanceDecisions,
  type FinancePageInfo,
  type UseFinancePageOptions,
  type UsePayoutRequestsOptions,
  type UsePayoutRequestsResult,
  type UsePayoutStatsResult,
  type UseRefundRequestsOptions,
  type UseRefundRequestsResult,
  type UseEscrowAccountsOptions,
  type UseEscrowAccountsResult,
  type UseFinanceDecisionsResult,
  type DecisionResult,
} from './finance.hooks';

export {
  ADMIN_PAYOUT_REQUESTS,
  ADMIN_REFUND_REQUESTS,
  ADMIN_ESCROW_ACCOUNTS,
  PAYOUT_REQUEST_STATS,
  REFUND_STATUS_COUNT,
  PAYOUT_LIST_FIELDS,
  REFUND_LIST_FIELDS,
  ESCROW_LIST_FIELDS,
} from './finance.queries';

export {
  useRecoveryQueue,
  usePayoutRecoverySummary,
  type RecoveryBucket,
  type UseRecoveryQueueResult,
  type UseRecoverySummaryResult,
} from './recovery.hooks';

export {
  PAYOUT_RECOVERY_SUMMARY,
  STUCK_PAYOUT_REQUESTS,
  RETRYABLE_PAYOUT_REQUESTS,
  PAYOUTS_FOR_REVIEW,
  PAYOUT_RECOVERY_FIELDS,
} from './recovery.queries';

export {
  APPROVE_PAYOUT_REQUEST,
  REJECT_PAYOUT_REQUEST,
  APPROVE_REFUND_REQUEST,
  REJECT_REFUND_REQUEST,
  UPDATE_ESCROW_ACCOUNT_STATUS,
} from './finance.mutations';
