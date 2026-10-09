'use client';

/**
 * Payments operations hooks (booking-service). Dual control: the proposer of a recovery action
 * cannot confirm it; `canConfirm` on the proposal is the server's answer and the UI follows it.
 */
import type {
  OpsConfirmRecoveryActionMutation,
  OpsConfirmRecoveryActionMutationVariables,
  OpsForceCompletePaymentAttemptsMutation,
  OpsForceCompletePaymentAttemptsMutationVariables,
  OpsHoldPayoutRequestMutation,
  OpsHoldPayoutRequestMutationVariables,
  OpsReleasePayoutHoldMutation,
  OpsReleasePayoutHoldMutationVariables,
  OpsResumePaymentAttemptMutation,
  OpsResumePaymentAttemptMutationVariables,
  OpsRetryPaymentAttemptsMutation,
  OpsRetryPaymentAttemptsMutationVariables,
  OpsTransferBetweenPlatformAccountsMutation,
  OpsTransferBetweenPlatformAccountsMutationVariables,
  OpsWithdrawRecoveryProposalMutation,
  OpsWithdrawRecoveryProposalMutationVariables,
  BookingStatus,
  CommissionStatus,
  OpsBookingsByBuyerQuery,
  OpsBookingsByBuyerQueryVariables,
  OpsCommissionRecordsQuery,
  OpsCommissionRecordsQueryVariables,
  OpsDualControlQueueQuery,
  OpsGatewaySettlementsQuery,
  OpsGatewaySettlementsQueryVariables,
  OpsPaymentAttemptSearchQuery,
  OpsPaymentAttemptSearchQueryVariables,
  OpsPaymentRiskSummaryQuery,
  OpsPurchasesByDayAndHourQuery,
  OpsPurchasesByDayAndHourQueryVariables,
  OpsStuckTransactionsQuery,
  OpsStuckTransactionsQueryVariables,
  PaymentAttemptFilterInput,
  PlatformAccountType,
} from '../../../../types/graphql';
import { useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import type { OffsetPageInfo } from '../../../../types/pageInfo';
import {
  OPS_BOOKINGS_BY_BUYER,
  OPS_COMMISSION_RECORDS,
  OPS_CONFIRM_RECOVERY_ACTION,
  OPS_DUAL_CONTROL_QUEUE,
  OPS_FORCE_COMPLETE_ATTEMPTS,
  OPS_GATEWAY_SETTLEMENTS,
  OPS_HOLD_PAYOUT,
  OPS_PAYMENT_ATTEMPT_SEARCH,
  OPS_PAYMENT_RISK_SUMMARY,
  OPS_PURCHASES_BY_DAY_AND_HOUR,
  OPS_RELEASE_PAYOUT_HOLD,
  OPS_RESUME_PAYMENT_ATTEMPT,
  OPS_RETRY_PAYMENT_ATTEMPTS,
  OPS_STUCK_TRANSACTIONS,
  OPS_TRANSFER_PLATFORM_ACCOUNTS,
  OPS_WITHDRAW_RECOVERY_PROPOSAL,
} from './payments-ops.queries';

type LoosePage = { totalCount?: number | null; pageSize?: number | null; currentPage?: number | null; totalPages?: number | null; hasNextPage?: boolean | null; hasPreviousPage?: boolean | null } | null | undefined;

function page(p: LoosePage, size: number): OffsetPageInfo {
  return {
    totalCount: p?.totalCount ?? 0,
    pageSize: p?.pageSize ?? size,
    currentPage: p?.currentPage ?? 0,
    totalPages: p?.totalPages ?? 0,
    hasNextPage: p?.hasNextPage ?? false,
    hasPreviousPage: p?.hasPreviousPage ?? false,
  };
}
const empty = (size: number): OffsetPageInfo => page(null, size);
const asError = (e: unknown): Error | undefined => (e ? (e as Error) : undefined);

/* ---------------------------------------------------------- attempts */

export type OpsAttemptRow = OpsPaymentAttemptSearchQuery['paymentAttemptSearch']['data'][number];

export function usePaymentAttemptSearch(o: { filter?: PaymentAttemptFilterInput; page?: number; size?: number } = {}) {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<OpsPaymentAttemptSearchQuery, OpsPaymentAttemptSearchQueryVariables>(OPS_PAYMENT_ATTEMPT_SEARCH, {
    variables: { filter: o.filter ?? {}, pagination: { page: o.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' } } as OpsPaymentAttemptSearchQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.paymentAttemptSearch.data ?? [],
    pageInfo: data ? page(data.paymentAttemptSearch.pagination, size) : empty(size),
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export function useStuckTransactions(o: { minutes?: number; page?: number; size?: number } = {}) {
  const size = o.size ?? 50;
  const { data, loading, error, refetch } = useQuery<OpsStuckTransactionsQuery, OpsStuckTransactionsQueryVariables>(OPS_STUCK_TRANSACTIONS, {
    variables: { minutes: o.minutes ?? 30, pagination: { page: o.page ?? 0, size } } as OpsStuckTransactionsQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.stuckTransactions.data ?? [],
    pageInfo: data ? page(data.stuckTransactions.pagination, size) : empty(size),
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export type RecoveryOutcome = { depositId: string; result: string; detail: string | null };

export function usePaymentRecoveryActions() {
  const opts = { refetchQueries: ['OpsStuckTransactions', 'OpsPaymentAttemptSearch', 'OpsDualControlQueue', 'TxPaymentAttempts'], errorPolicy: 'none' as const };
  const [resume, a] = useMutation<OpsResumePaymentAttemptMutation, OpsResumePaymentAttemptMutationVariables>(OPS_RESUME_PAYMENT_ATTEMPT, opts);
  const [retry, b] = useMutation<OpsRetryPaymentAttemptsMutation, OpsRetryPaymentAttemptsMutationVariables>(OPS_RETRY_PAYMENT_ATTEMPTS, opts);
  const [force, c] = useMutation<OpsForceCompletePaymentAttemptsMutation, OpsForceCompletePaymentAttemptsMutationVariables>(OPS_FORCE_COMPLETE_ATTEMPTS, opts);
  return {
    resume: useCallback(async (depositId: string) => (await resume({ variables: { depositId } })).data?.resumePaymentAttempt ?? null, [resume]),
    retry: useCallback(async (depositIds: string[]): Promise<RecoveryOutcome[]> => (await retry({ variables: { depositIds } })).data?.retryPaymentAttempts ?? [], [retry]),
    /** Proposes a force-complete; a second approver must confirm it from the dual control queue. */
    forceComplete: useCallback(async (depositIds: string[], reason: string) => (await force({ variables: { depositIds, reason } })).data?.forceCompletePaymentAttempts ?? null, [force]),
    busy: a.loading || b.loading || c.loading,
  };
}

/* ------------------------------------------------------ dual control */

export type RecoveryProposalRow = OpsDualControlQueueQuery['dualControlQueue'][number];

export function useDualControlQueue() {
  const { data, loading, error, refetch } = useQuery<OpsDualControlQueueQuery>(OPS_DUAL_CONTROL_QUEUE, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  return { proposals: data?.dualControlQueue ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

export function useDualControlActions() {
  const opts = { refetchQueries: ['OpsDualControlQueue', 'OpsStuckTransactions', 'LedgerPlatformAccounts'], errorPolicy: 'none' as const };
  const [confirm, a] = useMutation<OpsConfirmRecoveryActionMutation, OpsConfirmRecoveryActionMutationVariables>(OPS_CONFIRM_RECOVERY_ACTION, opts);
  const [withdraw, b] = useMutation<OpsWithdrawRecoveryProposalMutation, OpsWithdrawRecoveryProposalMutationVariables>(OPS_WITHDRAW_RECOVERY_PROPOSAL, opts);
  return {
    confirm: useCallback(async (proposalId: string, reason: string) => (await confirm({ variables: { proposalId, reason } })).data?.confirmRecoveryAction ?? null, [confirm]),
    withdraw: useCallback(async (proposalId: string) => (await withdraw({ variables: { proposalId } })).data?.withdrawRecoveryProposal ?? null, [withdraw]),
    busy: a.loading || b.loading,
  };
}

/** The maker cannot confirm their own proposal. The server decides (`canConfirm`); `isMaker` explains why in the UI. */
export function canConfirmProposal(p: Pick<RecoveryProposalRow, 'canConfirm' | 'status' | 'proposedById'>, staffId: string | null): boolean {
  return p.status === 'PENDING' && p.canConfirm && (staffId === null || p.proposedById !== staffId);
}

/* ---------------------------------------------------------- risk */

export type PaymentRiskSummaryRow = OpsPaymentRiskSummaryQuery['paymentRiskSummary'];

export function usePaymentRiskSummary(windowHours = 24) {
  const { data, loading, error, refetch } = useQuery<OpsPaymentRiskSummaryQuery>(OPS_PAYMENT_RISK_SUMMARY, {
    variables: { windowHours },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { summary: data?.paymentRiskSummary ?? null, loading, error: asError(error), refetch: () => void refetch() };
}

/* ------------------------------------------- commission & settlements */

export type CommissionRecordRow = OpsCommissionRecordsQuery['commissionRecords']['data'][number];

export function useCommissionRecords(o: { status?: CommissionStatus | null; eventId?: string | null; organizationId?: string | null; ticketId?: string | null; skip?: boolean; page?: number; size?: number } = {}) {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<OpsCommissionRecordsQuery, OpsCommissionRecordsQueryVariables>(OPS_COMMISSION_RECORDS, {
    variables: {
      filter: { status: o.status ?? null, eventId: o.eventId ?? null, organizationId: o.organizationId ?? null, ticketId: o.ticketId || null },
      pagination: { page: o.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' },
    } as OpsCommissionRecordsQueryVariables,
    skip: o.skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    records: data?.commissionRecords.data ?? [],
    totals: data?.commissionRecords.totals ?? null,
    pageInfo: data ? page(data.commissionRecords.pagination, size) : empty(size),
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export type GatewaySettlementRow = OpsGatewaySettlementsQuery['gatewaySettlements']['data'][number];

export function useGatewaySettlements(o: { page?: number; size?: number } = {}) {
  const size = o.size ?? 20;
  const { data, loading, error, refetch } = useQuery<OpsGatewaySettlementsQuery, OpsGatewaySettlementsQueryVariables>(OPS_GATEWAY_SETTLEMENTS, {
    variables: { filter: {}, pagination: { page: o.page ?? 0, size, sortBy: 'settlementDate', sortDirection: 'DESC' } } as OpsGatewaySettlementsQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    settlements: data?.gatewaySettlements.data ?? [],
    pageInfo: data ? page(data.gatewaySettlements.pagination, size) : empty(size),
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}

export interface PlatformTransferValues {
  fromAccount: PlatformAccountType;
  toAccount: PlatformAccountType;
  amount: string;
  reason: string;
  idempotencyKey: string;
}

export function useTransferBetweenPlatformAccounts() {
  const [mutate, { loading }] = useMutation<OpsTransferBetweenPlatformAccountsMutation, OpsTransferBetweenPlatformAccountsMutationVariables>(OPS_TRANSFER_PLATFORM_ACCOUNTS, { refetchQueries: ['LedgerPlatformAccounts', 'OpsDualControlQueue'], errorPolicy: 'none' });
  const transfer = useCallback(async (input: PlatformTransferValues) => (await mutate({ variables: { input } })).data?.transferBetweenPlatformAccounts ?? null, [mutate]);
  return { transfer, loading };
}

/* ------------------------------------------------------ payout hold */

export function usePayoutHoldActions() {
  const opts = { refetchQueries: ['PayoutOpsList', 'PayoutOpsDetail', 'PayoutsByEvent'], errorPolicy: 'none' as const };
  const [hold, a] = useMutation<OpsHoldPayoutRequestMutation, OpsHoldPayoutRequestMutationVariables>(OPS_HOLD_PAYOUT, opts);
  const [release, b] = useMutation<OpsReleasePayoutHoldMutation, OpsReleasePayoutHoldMutationVariables>(OPS_RELEASE_PAYOUT_HOLD, opts);
  return {
    hold: useCallback(async (payoutRequestId: string, reason: string) => void (await hold({ variables: { payoutRequestId, reason } })), [hold]),
    release: useCallback(async (payoutRequestId: string, note?: string) => void (await release({ variables: { payoutRequestId, note: note || null } })), [release]),
    busy: a.loading || b.loading,
  };
}

/* ------------------------------------------------------- analytics */

export type PurchaseHeatCellRow = OpsPurchasesByDayAndHourQuery['purchasesByDayAndHour'][number];

export function usePurchasesByDayAndHour(v: { from?: string | null; to?: string | null; eventId?: string | null; organizationId?: string | null } = {}) {
  const { data, loading, error, refetch } = useQuery<OpsPurchasesByDayAndHourQuery, OpsPurchasesByDayAndHourQueryVariables>(OPS_PURCHASES_BY_DAY_AND_HOUR, {
    variables: { from: v.from ?? null, to: v.to ?? null, eventId: v.eventId ?? null, organizationId: v.organizationId ?? null },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { cells: data?.purchasesByDayAndHour ?? [], loading, error: asError(error), refetch: () => void refetch() };
}

/* ------------------------------------------------- bookings by buyer */

export type AdminBuyerBookingRow = OpsBookingsByBuyerQuery['bookingsByBuyer']['data'][number];

export function useBookingsByBuyer(buyerId: string | null, o: { status?: BookingStatus | null; page?: number; size?: number } = {}) {
  const size = o.size ?? 10;
  const { data, loading, error, refetch } = useQuery<OpsBookingsByBuyerQuery, OpsBookingsByBuyerQueryVariables>(OPS_BOOKINGS_BY_BUYER, {
    variables: { buyerId: buyerId ?? '', filter: { status: o.status ?? null }, pagination: { page: o.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' } } as OpsBookingsByBuyerQueryVariables,
    skip: !buyerId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    bookings: data?.bookingsByBuyer.data ?? [],
    pageInfo: data ? page(data.bookingsByBuyer.pagination, size) : empty(size),
    loading,
    error: asError(error),
    refetch: () => void refetch(),
  };
}
