'use client';

/**
 * Finance operations hooks (platform admin). Types are local because the
 * generated schema types do not yet cover these operations.
 */

import type { OffsetPageInfo } from '../../../../types/pageInfo';
import type { BulkApproveRefundsMutation, BulkApproveRefundsMutationVariables, BulkRetryFailedPayoutsMutation, BulkRetryFailedPayoutsMutationVariables, ChargebackOpsListQuery, ChargebackOpsListQueryVariables, ChargebackOpsPendingQuery, ChargebackOpsPendingQueryVariables, ChargebackOpsStatsQuery, ChargebackOpsStatsQueryVariables, EscrowOpsDetailQuery, EscrowOpsDetailQueryVariables, EscrowOpsTransactionsQuery, EscrowOpsTransactionsQueryVariables, PayoutOpsDetailQuery, PayoutOpsDetailQueryVariables, PayoutOpsListQuery, PayoutOpsListQueryVariables, RefundOpsDetailQuery, RefundOpsDetailQueryVariables, RefundOpsListQuery, RefundOpsListQueryVariables } from '../../../../types/graphql';
import { useCallback, useRef } from 'react';
import { useApolloClient, useMutation, useQuery } from '@apollo/client/react';
import type { DocumentNode } from 'graphql';
import { resolveError, type GraphQLLikeError } from '../../../../lib/errors';
import { stableActionKey } from '../../../../lib/idempotency';
import type { DecisionResult } from '../finance/finance.hooks';
import * as Q from './finance-ops.queries';


export type OpsPageInfo = OffsetPageInfo;

const EMPTY_PAGE: OpsPageInfo = { totalCount: 0, pageSize: 20, currentPage: 0, totalPages: 0, hasNextPage: false, hasPreviousPage: false };
const num = (v: unknown): number => Number(v ?? 0);

// ------------------------------------------------------------------- shapes

export type PayoutIssueType =
  | 'BANK_REJECTED' | 'INVALID_ACCOUNT_DETAILS' | 'INSUFFICIENT_ESCROW' | 'COMPLIANCE_HOLD' | 'SUSPECTED_FRAUD'
  | 'TECHNICAL_ERROR' | 'PROVIDER_ERROR' | 'TIMEOUT' | 'DUPLICATE_REQUEST' | 'OTHER';
export type PayoutResolutionType =
  | 'AUTO_RESOLVED' | 'MANUAL_APPROVAL' | 'MANUAL_REJECTION' | 'RETRIED_SUCCESS' | 'ACCOUNT_UPDATED'
  | 'REFUNDED_TO_ESCROW' | 'WRITTEN_OFF' | 'ESCALATED';
export type PayoutReviewStatus = 'NONE' | 'PENDING_REVIEW' | 'UNDER_REVIEW' | 'REVIEWED' | 'ESCALATED';

export interface PayoutOpsRow {
  id: string;
  requestId: string;
  organizerId: string;
  organization: { id: string; name: string } | null;
  eventId: string | null;
  eventTitle: string | null;
  escrowAccountId: string;
  bankAccountId: string;
  requestedAmount: number | string;
  taxAmount: number | string | null;
  settledAmount: number | string;
  currency: string;
  status: string;
  payoutMethod: string | null;
  requestedAt: string;
  requestedById: string;
  approvedAt: string | null;
  approvedBy: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  processedAt: string | null;
  expectedPayoutDate: string | null;
  paymentReference: string | null;
  externalTransactionId: string | null;
  bankName: string | null;
  accountNumber: string | null;
  retryCount: number | null;
  lastError: string | null;
  notes: string | null;
  issueType: PayoutIssueType | null;
  reviewStatus: PayoutReviewStatus | null;
  isStuck: boolean | null;
  stuckReason: string | null;
  reviewNotes: string | null;
  resolutionNotes: string | null;
  bankAccount: { id: string; isVerified: boolean; status: string; accountHolderName: string } | null;
}

export interface RefundOpsRow {
  id: string;
  requestId: string;
  ticketId: string;
  ticketNumber: string;
  eventId: string;
  buyerId: string;
  originalTicketPrice: number | string | null;
  refundAmount: number | string;
  refundPercentage: number | null;
  platformRetains: number | string | null;
  processingFee: number | string | null;
  netRefundAmount: number | string | null;
  currency: string;
  status: string;
  requestType: string;
  reason: string;
  additionalNotes: string | null;
  requestedById: string | null;
  requestedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComments: string | null;
  rejectionReason: string | null;
  processedAt: string | null;
  paymentReference: string | null;
  originalPaymentMethod: string | null;
  daysBeforeEvent: number | null;
  policyApplied: string | null;
}

export interface EscrowDetail {
  id: string;
  accountNumber: string;
  eventId: string;
  eventTitle: string | null;
  organizerId: string;
  organization: { id: string; name: string } | null;
  currentBalance: number | string;
  totalDeposits: number | string;
  totalWithdrawals: number | string;
  totalRefunds: number | string;
  totalCommissions: number | string;
  pendingWithdrawals: number | string | null;
  currency: string;
  status: string;
  lockUntil: string | null;
  payoutEligibleAt: string | null;
  closedAt: string | null;
  createdAt: string | null;
}

export interface EscrowTxRow {
  id: string;
  type: string;
  category: string;
  amount: number | string;
  balanceAfter: number | string;
  currency: string;
  description: string | null;
  payoutRequestId: string | null;
  refundRequestId: string | null;
  chargebackId: string | null;
  timestamp: string;
}

export interface EscrowVerification {
  eventId: string;
  escrowAccountId: string | null;
  escrowBalance: number | string | null;
  journalBalance: number | string | null;
  variance: number | string | null;
  isConsistent: boolean;
  status: string | null;
}

export interface ChargebackRow {
  id: string;
  chargebackId: string;
  originalTransactionId: string;
  ticketId: string;
  eventId: string;
  organizerId: string;
  customerId: string;
  originalAmount: number | string;
  chargebackAmount: number | string;
  chargebackFee: number | string;
  currency: string;
  reason: string;
  status: string;
  receivedAt: string;
  responseDeadline: string;
  evidenceSubmitted: string | null;
  resolvedAt: string | null;
  recoveryStatus: string;
  recoveredAmount: number | string | null;
  fundSource: string | null;
  createdAt: string;
}

export interface ChargebackStatsData {
  totalCount: number;
  pendingCount: number;
  disputedCount: number;
  wonCount: number;
  lostCount: number;
  totalAmount: number | string;
  recoveredAmount: number | string;
  writtenOffAmount: number | string;
  winRate: number;
}

// ----------------------------------------------------------- helper: decide

async function decide<T = unknown>(run: () => Promise<{ data?: T | null }>): Promise<DecisionResult & { data?: T | null }> {
  try {
    const res = await run();
    return { success: true, message: null, errorCode: null, data: res?.data };
  } catch (error) {
    const r = resolveError(error as GraphQLLikeError);
    return { success: false, message: r.message, errorCode: r.code };
  }
}

export interface PageOptions {
  page?: number;
  size?: number;
}

// ------------------------------------------------------------------ payouts

export interface UsePayoutOpsListOptions extends PageOptions {
  status?: string | null;
  payoutMethod?: string | null;
}

export function usePayoutOpsList(options: UsePayoutOpsListOptions = {}) {
  const size = options.size ?? 20;
  const { data, loading, error, refetch } = useQuery<PayoutOpsListQuery, PayoutOpsListQueryVariables>(Q.PAYOUT_OPS_LIST, {
    variables: {
      filter: {
        status: options.status ?? null,
        payoutMethod: options.payoutMethod ?? null,
        organizerId: null, eventId: null, escrowAccountId: null, startDate: null, endDate: null,
      },
      pagination: { page: options.page ?? 0, size, sortBy: 'requestedAt', sortDirection: 'DESC' },
    } as PayoutOpsListQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    payouts: data?.payoutRequests?.data ?? [],
    pageInfo: data?.payoutRequests?.pagination ?? { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export function usePayoutOpsDetail(id: string | null) {
  const { data, loading, error, refetch } = useQuery<PayoutOpsDetailQuery, PayoutOpsDetailQueryVariables>(Q.PAYOUT_OPS_DETAIL, {
    variables: { id: id ?? '' } as PayoutOpsDetailQueryVariables,
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { payout: data?.payoutRequest ?? null, loading, error: error as Error | undefined, refetch: () => void refetch() };
}

export function usePayoutOps() {
  // Keyed by payoutRequestId: a retry of the same stuck payout replays or cleanly re-runs; it
  // never needs a different key for the same id, since retrying is always the same request.
  const retryKeys = useRef(new Map<string, string>());
  const [process, s1] = useMutation(Q.PROCESS_PAYOUT_REQUEST);
  const [complete, s2] = useMutation(Q.COMPLETE_PAYOUT_REQUEST);
  const [retry, s3] = useMutation(Q.RETRY_PAYOUT_REQUEST);
  const [resume, s4] = useMutation(Q.RESUME_PAYOUT_REQUEST);
  const [mark, s5] = useMutation(Q.MARK_PAYOUT_FOR_REVIEW);
  const [resolve, s6] = useMutation(Q.RESOLVE_PAYOUT_ISSUE);
  const [escalate, s7] = useMutation(Q.ESCALATE_PAYOUT_REQUEST);
  const [bulk, s8] = useMutation<BulkRetryFailedPayoutsMutation, BulkRetryFailedPayoutsMutationVariables>(Q.BULK_RETRY_FAILED_PAYOUTS);
  return {
    processPayout: useCallback((payoutRequestId: string) => decide(() => process({ variables: { payoutRequestId } })), [process]),
    completePayout: useCallback((payoutRequestId: string, bankReference: string) => decide(() => complete({ variables: { payoutRequestId, bankReference } })), [complete]),
    retryPayout: useCallback(
      (payoutRequestId: string) => decide(() => retry({ variables: { payoutRequestId, idempotencyKey: stableActionKey(retryKeys.current, payoutRequestId) } })),
      [retry]
    ),
    resumePayout: useCallback((payoutRequestId: string) => decide(() => resume({ variables: { payoutRequestId } })), [resume]),
    markForReview: useCallback((payoutRequestId: string, issueType: PayoutIssueType, notes?: string) => decide(() => mark({ variables: { payoutRequestId, issueType, notes: notes ?? null } })), [mark]),
    resolveIssue: useCallback((payoutRequestId: string, resolutionType: PayoutResolutionType, notes: string) => decide(() => resolve({ variables: { payoutRequestId, resolutionType, notes } })), [resolve]),
    escalatePayout: useCallback((payoutRequestId: string, reason: string) => decide(() => escalate({ variables: { payoutRequestId, reason } })), [escalate]),
    bulkRetry: useCallback((payoutRequestIds: string[]) => decide(() => bulk({ variables: { payoutRequestIds } })), [bulk]),
    submitting: [s1, s2, s3, s4, s5, s6, s7, s8].some((s) => s.loading),
  };
}

// ------------------------------------------------------------------ refunds

export interface UseRefundOpsListOptions extends PageOptions {
  status?: string | null;
  requestType?: string | null;
}

export function useRefundOpsList(options: UseRefundOpsListOptions = {}) {
  const size = options.size ?? 20;
  const { data, loading, error, refetch } = useQuery<RefundOpsListQuery, RefundOpsListQueryVariables>(Q.REFUND_OPS_LIST, {
    variables: {
      filter: {
        status: options.status ?? null,
        requestType: options.requestType ?? null,
        ticketId: null, buyerId: null, eventId: null, organizerId: null, startDate: null, endDate: null,
      },
      pagination: { page: options.page ?? 0, size, sortBy: 'requestedAt', sortDirection: 'DESC' },
    } as RefundOpsListQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    refunds: data?.refundRequests?.data ?? [],
    pageInfo: data?.refundRequests?.pagination ?? { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

/**
 * Pending refund queue used by the tiles and escalation counts. Reads up to
 * `limit` oldest-first rows; `total` is the server's count so the UI can tell
 * when the sum covers only part of the queue.
 */
export function usePendingRefunds(limit = 100) {
  const { data, loading, error } = useQuery<RefundOpsListQuery, RefundOpsListQueryVariables>(Q.REFUND_OPS_LIST, {
    variables: {
      filter: { status: 'PENDING', requestType: null, ticketId: null, buyerId: null, eventId: null, organizerId: null, startDate: null, endDate: null },
      pagination: { page: 0, size: limit, sortBy: 'requestedAt', sortDirection: 'ASC' },
    } as RefundOpsListQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const rows = data?.refundRequests?.data ?? [];
  return { rows, total: data?.refundRequests?.pagination?.totalCount ?? null, loading, error: error as Error | undefined };
}

export function useRefundOpsDetail(id: string | null) {
  const { data, loading, error, refetch } = useQuery<RefundOpsDetailQuery, RefundOpsDetailQueryVariables>(Q.REFUND_OPS_DETAIL, {
    variables: { id: id ?? '' } as RefundOpsDetailQueryVariables,
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { refund: data?.refundRequest ?? null, loading, error: error as Error | undefined, refetch: () => void refetch() };
}

export function useRefundOps() {
  // Keyed by (ticketId, reason): a retry of the same request replays or cleanly re-runs; a
  // second, genuinely different refund raised for the same ticket later (a different reason)
  // gets its own key rather than being mistaken for a repeat of the first.
  const createKeys = useRef(new Map<string, string>());
  const [process, s1] = useMutation(Q.PROCESS_REFUND_REQUEST);
  const [bulk, s2] = useMutation<BulkApproveRefundsMutation, BulkApproveRefundsMutationVariables>(Q.BULK_APPROVE_REFUNDS);
  const [create, s3] = useMutation(Q.CREATE_ADMIN_REFUND_REQUEST);
  return {
    processRefund: useCallback((refundRequestId: string) => decide(() => process({ variables: { refundRequestId } })), [process]),
    bulkApprove: useCallback((refundRequestIds: string[]) => decide(() => bulk({ variables: { refundRequestIds } })), [bulk]),
    /** Always created pending: a second person approves (bypassApproval is never sent as true). */
    createAdminRefund: useCallback(
      (ticketId: string, reason: string) =>
        decide(() => create({ variables: { ticketId, reason, bypassApproval: false, idempotencyKey: stableActionKey(createKeys.current, ticketId, reason) } })),
      [create]
    ),
    submitting: s1.loading || s2.loading || s3.loading,
  };
}

// ------------------------------------------------------------------- escrow

export function useEscrowOpsDetail(id: string | null) {
  const { data, loading, error, refetch } = useQuery<EscrowOpsDetailQuery, EscrowOpsDetailQueryVariables>(Q.ESCROW_OPS_DETAIL, {
    variables: { id: id ?? '' } as EscrowOpsDetailQueryVariables,
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { account: data?.escrowAccount ?? null, loading, error: error as Error | undefined, refetch: () => void refetch() };
}

export function useEscrowTransactions(accountId: string | null, options: PageOptions = {}) {
  const size = options.size ?? 6;
  const { data, loading, error, refetch } = useQuery<EscrowOpsTransactionsQuery, EscrowOpsTransactionsQueryVariables>(Q.ESCROW_TRANSACTIONS, {
    variables: { escrowAccountId: accountId ?? '', pagination: { page: options.page ?? 0, size, sortBy: 'timestamp', sortDirection: 'DESC' } } as EscrowOpsTransactionsQueryVariables,
    skip: !accountId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    transactions: data?.escrowTransactions?.data ?? [],
    pageInfo: data?.escrowTransactions?.pagination ?? { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export function useEscrowOps() {
  const [lock, s1] = useMutation(Q.LOCK_ESCROW_ACCOUNT);
  const [unlock, s2] = useMutation(Q.UNLOCK_ESCROW_ACCOUNT);
  const [eligible, s3] = useMutation(Q.MARK_PAYOUT_ELIGIBLE);
  const [close, s4] = useMutation(Q.CLOSE_ESCROW_ACCOUNT);
  return {
    lockEscrow: useCallback((accountId: string, lockUntil: string, reason: string) => decide(() => lock({ variables: { accountId, lockUntil, reason } })), [lock]),
    unlockEscrow: useCallback((accountId: string, reason: string) => decide(() => unlock({ variables: { accountId, reason } })), [unlock]),
    markEligible: useCallback((accountId: string) => decide(() => eligible({ variables: { accountId } })), [eligible]),
    closeEscrow: useCallback((accountId: string, reason: string) => decide(() => close({ variables: { accountId, reason } })), [close]),
    submitting: s1.loading || s2.loading || s3.loading || s4.loading,
  };
}

/** Runs `escrowJournalVerificationAll` on demand (the consistency check dialog). */
export function useEscrowConsistencyCheck() {
  const client = useApolloClient();
  return useCallback(async (): Promise<{ rows: EscrowVerification[]; error: string | null }> => {
    try {
      const res = await client.query<{ escrowJournalVerificationAll: EscrowVerification[] }>({
        query: Q.ESCROW_JOURNAL_VERIFICATION_ALL as DocumentNode,
        fetchPolicy: 'network-only',
      });
      return { rows: res.data?.escrowJournalVerificationAll ?? [], error: null };
    } catch (e) {
      return { rows: [], error: resolveError(e as GraphQLLikeError).message };
    }
  }, [client]);
}

// -------------------------------------------------------------- chargebacks

export interface UseChargebacksOptions extends PageOptions {
  status?: string | null;
  recoveryStatus?: string | null;
  eventId?: string | null;
  skip?: boolean;
}

export function useChargebackList(options: UseChargebacksOptions = {}) {
  const size = options.size ?? 20;
  const { data, loading, error, refetch } = useQuery<ChargebackOpsListQuery, ChargebackOpsListQueryVariables>(Q.CHARGEBACK_LIST, {
    variables: {
      filter: { status: options.status ?? null, recoveryStatus: options.recoveryStatus ?? null, eventId: options.eventId ?? null, organizerId: null, startDate: null, endDate: null },
      pagination: { page: options.page ?? 0, size, sortBy: 'receivedAt', sortDirection: 'DESC' },
    } as ChargebackOpsListQueryVariables,
    skip: options.skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    chargebacks: data?.chargebacks?.data ?? [],
    pageInfo: data?.chargebacks?.pagination ?? { ...EMPTY_PAGE, pageSize: size },
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

/** Open (pending) chargebacks and the recovery queue, for the tiles and the tab count. */
export function useChargebackQueue() {
  const { data, loading, error } = useQuery<ChargebackOpsPendingQuery, ChargebackOpsPendingQueryVariables>(Q.CHARGEBACK_PENDING, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  const open = data?.pendingChargebacks ?? [];
  return {
    open,
    openAmount: open.reduce((a, c) => a + num(c.chargebackAmount), 0),
    recoveryCount: data?.chargebacksPendingRecovery?.length ?? 0,
    loaded: !!data,
    loading,
    error: error as Error | undefined,
  };
}

export function useChargebackStatsQuery() {
  const { data, loading, error } = useQuery<ChargebackOpsStatsQuery, ChargebackOpsStatsQueryVariables>(Q.CHARGEBACK_STATS, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  return { stats: data?.chargebackStats ?? null, loading, error: error as Error | undefined };
}

export interface ReceiveChargebackValues {
  chargebackId: string;
  originalTransactionId: string;
  ticketId: string;
  eventId: string;
  organizerId: string;
  customerId: string;
  originalAmount: number;
  chargebackAmount: number;
  chargebackFee: number;
  currency: string;
  reason: string;
  responseDeadline: string;
}

export function useChargebackOps() {
  const [receive, s1] = useMutation(Q.RECEIVE_CHARGEBACK);
  const [review, s2] = useMutation(Q.START_CHARGEBACK_REVIEW);
  const [accept, s3] = useMutation(Q.ACCEPT_CHARGEBACK);
  const [dispute, s4] = useMutation(Q.DISPUTE_CHARGEBACK);
  const [outcome, s5] = useMutation(Q.RECORD_CHARGEBACK_OUTCOME);
  const refetch = ['ChargebackOpsList', 'ChargebackOpsPending', 'ChargebackOpsStats'];
  return {
    receiveChargeback: useCallback((input: ReceiveChargebackValues) => decide(() => receive({ variables: { input }, refetchQueries: refetch })), [receive]), // eslint-disable-line react-hooks/exhaustive-deps
    startReview: useCallback((id: string, notes?: string) => decide(() => review({ variables: { id, notes: notes ?? null }, refetchQueries: refetch })), [review]), // eslint-disable-line react-hooks/exhaustive-deps
    acceptChargeback: useCallback((id: string, reason?: string) => decide(() => accept({ variables: { id, reason: reason ?? null }, refetchQueries: refetch })), [accept]), // eslint-disable-line react-hooks/exhaustive-deps
    disputeChargeback: useCallback((id: string, notes: string) => decide(() => dispute({ variables: { id, input: { notes } }, refetchQueries: refetch })), [dispute]), // eslint-disable-line react-hooks/exhaustive-deps
    recordOutcome: useCallback((id: string, won: boolean, notes?: string) => decide(() => outcome({ variables: { id, won, notes: notes ?? null }, refetchQueries: refetch })), [outcome]), // eslint-disable-line react-hooks/exhaustive-deps
    submitting: [s1, s2, s3, s4, s5].some((s) => s.loading),
  };
}

// ------------------------------------------------------------ bank accounts

/** Admin verification of one payout account. The pending queue itself has no admin query yet. */
export function useVerifyBankAccount() {
  const [verify, state] = useMutation(Q.VERIFY_BANK_ACCOUNT);
  return { verifyBankAccount: useCallback((id: string) => decide(() => verify({ variables: { id } })), [verify]), submitting: state.loading };
}
