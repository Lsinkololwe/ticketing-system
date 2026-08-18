'use client';

/**
 * Admin finance hooks — the three Financial ops tables and their counters.
 *
 * Every type here comes from codegen. Nothing in this file describes a shape
 * the schema does not already declare.
 */

import { useCallback, useMemo } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import type {
  EscrowAccountStatus,
  EventEscrowAccount,
  PayoutRequest,
  PayoutRequestStats,
  PayoutRequestStatus,
  RefundRequest,
  RefundRequestStatus,
} from '../../../../types/graphql';
import {
  ADMIN_ESCROW_ACCOUNTS,
  ADMIN_PAYOUT_REQUESTS,
  ADMIN_REFUND_REQUESTS,
  PAYOUT_REQUEST_STATS,
  REFUND_STATUS_COUNT,
} from './finance.queries';
import {
  APPROVE_PAYOUT_REQUEST,
  APPROVE_REFUND_REQUEST,
  REJECT_PAYOUT_REQUEST,
  REJECT_REFUND_REQUEST,
  UPDATE_ESCROW_ACCOUNT_STATUS,
} from './finance.mutations';

// =============================================================================
// SHARED SHAPES
// =============================================================================

/** The six pagination fields booking-service actually populates. */
export interface FinancePageInfo {
  totalCount: number;
  pageSize: number;
  currentPage: number;
  totalPages: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
}

interface OffsetPage<T> {
  data: T[];
  pagination: Partial<FinancePageInfo> | null;
}

export interface UseFinancePageOptions {
  page?: number;
  size?: number;
}

const DEFAULT_SIZE = 20;

function pageInfoOf(
  pagination: Partial<FinancePageInfo> | null | undefined,
  fallbackSize: number
): FinancePageInfo {
  return {
    totalCount: pagination?.totalCount ?? 0,
    pageSize: pagination?.pageSize ?? fallbackSize,
    currentPage: pagination?.currentPage ?? 0,
    totalPages: pagination?.totalPages ?? 0,
    hasNextPage: pagination?.hasNextPage ?? false,
    hasPreviousPage: pagination?.hasPreviousPage ?? false,
  };
}

function paginationVars(options: UseFinancePageOptions, sortBy: string) {
  return {
    page: options.page ?? 0,
    size: options.size ?? DEFAULT_SIZE,
    sortBy,
    sortDirection: 'DESC',
  };
}

// =============================================================================
// PAYOUT REQUESTS
// =============================================================================

export interface UsePayoutRequestsOptions extends UseFinancePageOptions {
  status?: PayoutRequestStatus | null;
  organizerId?: string | null;
}

export interface UsePayoutRequestsResult {
  payouts: PayoutRequest[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminPayoutRequests(
  options: UsePayoutRequestsOptions = {}
): UsePayoutRequestsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<{
    payoutRequestsOffsetPagination: OffsetPage<PayoutRequest>;
  }>(ADMIN_PAYOUT_REQUESTS, {
    variables: {
      // The server declares this filter non-null, so an unfiltered view sends
      // an object of nulls rather than omitting the argument.
      filter: {
        status: options.status ?? null,
        organizerId: options.organizerId ?? null,
      },
      pagination: paginationVars(options, 'requestedAt'),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.payoutRequestsOffsetPagination;

  return {
    payouts: page?.data ?? [],
    pageInfo: pageInfoOf(page?.pagination, size),
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

export interface UsePayoutStatsResult {
  stats: PayoutRequestStats | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function usePayoutRequestStats(): UsePayoutStatsResult {
  const { data, loading, error, refetch } = useQuery<{
    payoutRequestStats: PayoutRequestStats;
  }>(PAYOUT_REQUEST_STATS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    // Null rather than zeroes while in flight: a tile reading "0 pending"
    // before the data lands is a claim, not a placeholder.
    stats: data?.payoutRequestStats ?? null,
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

// =============================================================================
// REFUND REQUESTS
// =============================================================================

export interface UseRefundRequestsOptions extends UseFinancePageOptions {
  status?: RefundRequestStatus | null;
  eventId?: string | null;
}

export interface UseRefundRequestsResult {
  refunds: RefundRequest[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminRefundRequests(
  options: UseRefundRequestsOptions = {}
): UseRefundRequestsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<{
    refundRequestsOffsetPagination: OffsetPage<RefundRequest>;
  }>(ADMIN_REFUND_REQUESTS, {
    variables: {
      filter: {
        status: options.status ?? null,
        eventId: options.eventId ?? null,
      },
      pagination: paginationVars(options, 'requestedAt'),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.refundRequestsOffsetPagination;

  return {
    refunds: page?.data ?? [],
    pageInfo: pageInfoOf(page?.pagination, size),
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

/**
 * How many refunds sit in one status, platform-wide.
 *
 * Asks the server for a one-row page and reads `totalCount`. Counting rows on
 * the current page would answer "how many on this page", which is a different
 * and much smaller number.
 */
export function useRefundStatusCount(status: RefundRequestStatus | null): {
  count: number | null;
  loading: boolean;
} {
  const { data, loading } = useQuery<{
    refundRequestsOffsetPagination: { pagination: { totalCount: number | null } | null };
  }>(REFUND_STATUS_COUNT, {
    variables: { filter: { status } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const total = data?.refundRequestsOffsetPagination?.pagination?.totalCount;
  return { count: total ?? null, loading };
}

// =============================================================================
// ESCROW ACCOUNTS
// =============================================================================

export interface UseEscrowAccountsOptions extends UseFinancePageOptions {
  status?: EscrowAccountStatus | null;
  organizerId?: string | null;
}

export interface UseEscrowAccountsResult {
  accounts: EventEscrowAccount[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminEscrowAccounts(
  options: UseEscrowAccountsOptions = {}
): UseEscrowAccountsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<{
    escrowAccountsOffsetPagination: OffsetPage<EventEscrowAccount>;
  }>(ADMIN_ESCROW_ACCOUNTS, {
    variables: {
      filter: {
        status: options.status ?? null,
        organizerId: options.organizerId ?? null,
      },
      pagination: paginationVars(options, 'createdAt'),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.escrowAccountsOffsetPagination;

  return {
    accounts: page?.data ?? [],
    pageInfo: pageInfoOf(page?.pagination, size),
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

// =============================================================================
// DECISIONS
// =============================================================================

export interface DecisionResult {
  success: boolean;
  message: string | null;
  errors: string[];
}

interface MutationEnvelope {
  success: boolean;
  message: string | null;
  errors: string[];
}

/**
 * A mutation returned `success: false` with an `errors` array rather than
 * throwing. Treating that as a success is how a UI ends up reporting an
 * approval that the server refused.
 */
function envelopeOf(payload: MutationEnvelope | undefined, fallback: string): DecisionResult {
  if (!payload) {
    return { success: false, message: fallback, errors: [fallback] };
  }
  return {
    success: payload.success,
    message: payload.message ?? null,
    errors: payload.errors ?? [],
  };
}

export interface UseFinanceDecisionsResult {
  approvePayout: (id: string, notes?: string) => Promise<DecisionResult>;
  rejectPayout: (id: string, rejectionReason: string) => Promise<DecisionResult>;
  approveRefund: (id: string, reviewComments?: string) => Promise<DecisionResult>;
  rejectRefund: (id: string, rejectionReason: string) => Promise<DecisionResult>;
  setEscrowStatus: (
    id: string,
    status: EscrowAccountStatus,
    reason: string
  ) => Promise<DecisionResult>;
  submitting: boolean;
}

/**
 * The four approval decisions.
 *
 * Each refetches the list and counters it affects — an approved payout that
 * stays in the pending table invites a second approval of the same money.
 */
export function useFinanceDecisions(): UseFinanceDecisionsResult {
  const refetchPayouts = useMemo(
    () => [{ query: PAYOUT_REQUEST_STATS }],
    []
  );

  const [approvePayoutMutation, approvePayoutState] = useMutation(APPROVE_PAYOUT_REQUEST, {
    refetchQueries: refetchPayouts,
    awaitRefetchQueries: true,
  });
  const [rejectPayoutMutation, rejectPayoutState] = useMutation(REJECT_PAYOUT_REQUEST, {
    refetchQueries: refetchPayouts,
    awaitRefetchQueries: true,
  });
  const [approveRefundMutation, approveRefundState] = useMutation(APPROVE_REFUND_REQUEST);
  const [rejectRefundMutation, rejectRefundState] = useMutation(REJECT_REFUND_REQUEST);
  const [escrowStatusMutation, escrowStatusState] = useMutation(UPDATE_ESCROW_ACCOUNT_STATUS);

  const approvePayout = useCallback(
    async (id: string, notes?: string): Promise<DecisionResult> => {
      const { data } = await approvePayoutMutation({
        variables: { payoutRequestId: id, notes: notes ?? null },
      });
      return envelopeOf(
        (data as { approvePayoutRequest?: MutationEnvelope } | undefined)?.approvePayoutRequest,
        'The server did not confirm the approval.'
      );
    },
    [approvePayoutMutation]
  );

  const rejectPayout = useCallback(
    async (id: string, rejectionReason: string): Promise<DecisionResult> => {
      const { data } = await rejectPayoutMutation({
        variables: { payoutRequestId: id, rejectionReason },
      });
      return envelopeOf(
        (data as { rejectPayoutRequest?: MutationEnvelope } | undefined)?.rejectPayoutRequest,
        'The server did not confirm the rejection.'
      );
    },
    [rejectPayoutMutation]
  );

  const approveRefund = useCallback(
    async (id: string, reviewComments?: string): Promise<DecisionResult> => {
      const { data } = await approveRefundMutation({
        variables: { refundRequestId: id, reviewComments: reviewComments ?? null },
      });
      return envelopeOf(
        (data as { approveRefundRequest?: MutationEnvelope } | undefined)?.approveRefundRequest,
        'The server did not confirm the approval.'
      );
    },
    [approveRefundMutation]
  );

  const rejectRefund = useCallback(
    async (id: string, rejectionReason: string): Promise<DecisionResult> => {
      const { data } = await rejectRefundMutation({
        variables: { refundRequestId: id, rejectionReason },
      });
      return envelopeOf(
        (data as { rejectRefundRequest?: MutationEnvelope } | undefined)?.rejectRefundRequest,
        'The server did not confirm the rejection.'
      );
    },
    [rejectRefundMutation]
  );

  const setEscrowStatus = useCallback(
    async (
      id: string,
      status: EscrowAccountStatus,
      reason: string
    ): Promise<DecisionResult> => {
      const { data } = await escrowStatusMutation({
        variables: { accountId: id, status, reason },
      });
      return envelopeOf(
        (data as { updateEscrowAccountStatus?: MutationEnvelope } | undefined)
          ?.updateEscrowAccountStatus,
        'The server did not confirm the status change.'
      );
    },
    [escrowStatusMutation]
  );

  return {
    approvePayout,
    rejectPayout,
    approveRefund,
    rejectRefund,
    setEscrowStatus,
    submitting:
      approvePayoutState.loading ||
      rejectPayoutState.loading ||
      approveRefundState.loading ||
      rejectRefundState.loading ||
      escrowStatusState.loading,
  };
}
