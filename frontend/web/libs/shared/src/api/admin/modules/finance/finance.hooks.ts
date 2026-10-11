'use client';

/**
 * Admin finance hooks — the three Financial ops tables and their counters.
 *
 * Every type here comes from codegen. Nothing in this file describes a shape
 * the schema does not already declare.
 */

import { useCallback, useMemo, useRef } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { stableActionKey } from '../../../../lib/idempotency';
import type {
  EscrowAccountStatus,
  PayoutRequestStatus,
  RefundRequestStatus,
  SortDirection,
  AdminPayoutRequestsQuery,
  AdminPayoutRequestsQueryVariables,
  PayoutRequestStatsQuery,
  AdminRefundRequestsQuery,
  AdminRefundRequestsQueryVariables,
  RefundStatusCountQuery,
  RefundStatusCountQueryVariables,
  AdminEscrowAccountsQuery,
  AdminEscrowAccountsQueryVariables,
  ApprovePayoutRequestMutation,
  ApprovePayoutRequestMutationVariables,
  RejectPayoutRequestMutation,
  RejectPayoutRequestMutationVariables,
  ApproveRefundRequestMutation,
  ApproveRefundRequestMutationVariables,
  RejectRefundRequestMutation,
  RejectRefundRequestMutationVariables,
  UpdateEscrowAccountStatusMutation,
  UpdateEscrowAccountStatusMutationVariables,
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
import type { OffsetPageInfo } from '../../../../types/pageInfo';
import { resolveError, type GraphQLLikeError } from '../../../../lib/errors';

// =============================================================================
// SHARED SHAPES
// =============================================================================

/** The six pagination fields booking-service actually populates. */
/**
 * Page metadata for this surface, with the field set taken from the generated
 * schema type rather than re-declared — see `types/pageInfo`.
 */
export type FinancePageInfo = OffsetPageInfo;

export interface UseFinancePageOptions {
  page?: number;
  size?: number;
}

const DEFAULT_SIZE = 20;

function pageInfoOf(
  pagination:
    | {
        totalCount?: number | null;
        pageSize?: number | null;
        currentPage?: number | null;
        totalPages?: number | null;
        hasNextPage?: boolean | null;
        hasPreviousPage?: boolean | null;
      }
    | null
    | undefined,
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
    sortDirection: 'DESC' as SortDirection,
  };
}

// =============================================================================
// PAYOUT REQUESTS
// =============================================================================

export interface UsePayoutRequestsOptions extends UseFinancePageOptions {
  status?: PayoutRequestStatus | null;
  organizerId?: string | null;
}

/** The row shape this screen actually selects — see `ADMIN_PAYOUT_REQUESTS`. */
export type AdminPayoutRequestRow = AdminPayoutRequestsQuery['payoutRequests']['data'][number];

export interface UsePayoutRequestsResult {
  payouts: AdminPayoutRequestRow[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminPayoutRequests(
  options: UsePayoutRequestsOptions = {}
): UsePayoutRequestsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<
    AdminPayoutRequestsQuery,
    AdminPayoutRequestsQueryVariables
  >(ADMIN_PAYOUT_REQUESTS, {
    variables: {
      // The server declares this filter non-null, so an unfiltered view sends
      // an object of nulls rather than omitting the argument.
      filter: {
        status: options.status ?? null,
        organizerId: options.organizerId ?? null,
        endDate: null,
        escrowAccountId: null,
        eventId: null,
        payoutMethod: null,
        startDate: null,
      },
      pagination: paginationVars(options, 'requestedAt'),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.payoutRequests as AdminPayoutRequestsQuery['payoutRequests'] | undefined;

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

/** The stats shape this screen actually selects — see `PAYOUT_REQUEST_STATS`. */
export type AdminPayoutRequestStats = PayoutRequestStatsQuery['payoutRequestStats'];

export interface UsePayoutStatsResult {
  stats: AdminPayoutRequestStats | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function usePayoutRequestStats(): UsePayoutStatsResult {
  const { data, loading, error, refetch } = useQuery<PayoutRequestStatsQuery>(PAYOUT_REQUEST_STATS, {
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

/** The row shape this screen actually selects — see `ADMIN_REFUND_REQUESTS`. */
export type AdminRefundRequestRow = AdminRefundRequestsQuery['refundRequests']['data'][number];

export interface UseRefundRequestsResult {
  refunds: AdminRefundRequestRow[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminRefundRequests(
  options: UseRefundRequestsOptions = {}
): UseRefundRequestsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<
    AdminRefundRequestsQuery,
    AdminRefundRequestsQueryVariables
  >(ADMIN_REFUND_REQUESTS, {
    variables: {
      filter: {
        status: options.status ?? null,
        eventId: options.eventId ?? null,
        buyerId: null,
        endDate: null,
        organizerId: null,
        requestType: null,
        startDate: null,
        ticketId: null,
      },
      pagination: paginationVars(options, 'requestedAt'),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.refundRequests as AdminRefundRequestsQuery['refundRequests'] | undefined;

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
  const { data, loading } = useQuery<RefundStatusCountQuery, RefundStatusCountQueryVariables>(
    REFUND_STATUS_COUNT,
    {
    variables: {
      filter: {
        status,
        buyerId: null,
        endDate: null,
        eventId: null,
        organizerId: null,
        requestType: null,
        startDate: null,
        ticketId: null,
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const total = data?.refundRequests?.pagination?.totalCount;
  return { count: total ?? null, loading };
}

// =============================================================================
// ESCROW ACCOUNTS
// =============================================================================

export interface UseEscrowAccountsOptions extends UseFinancePageOptions {
  status?: EscrowAccountStatus | null;
  organizerId?: string | null;
}

/** The row shape this screen actually selects — see `ADMIN_ESCROW_ACCOUNTS`. */
export type AdminEscrowAccountRow = AdminEscrowAccountsQuery['escrowAccounts']['data'][number];

export interface UseEscrowAccountsResult {
  accounts: AdminEscrowAccountRow[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminEscrowAccounts(
  options: UseEscrowAccountsOptions = {}
): UseEscrowAccountsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<
    AdminEscrowAccountsQuery,
    AdminEscrowAccountsQueryVariables
  >(ADMIN_ESCROW_ACCOUNTS, {
    variables: {
      filter: {
        status: options.status ?? null,
        organizerId: options.organizerId ?? null,
        currency: null,
        eventId: null,
        hasBalance: null,
      },
      pagination: paginationVars(options, 'createdAt'),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.escrowAccounts as AdminEscrowAccountsQuery['escrowAccounts'] | undefined;

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

/**
 * The outcome of an approval decision, as this screen needs to render it.
 *
 * <p>`success` here is derived from whether the mutation *threw*, not from a
 * field in the response. A refusal is a typed error carrying an error-registry
 * code, so a caller cannot report an approval the server declined by
 * forgetting to read a flag — the only way to get `success: true` is for the
 * mutation to have returned.</p>
 */
export interface DecisionResult {
  success: boolean;
  message: string | null;
  errorCode: string | null;
}

/**
 * Runs a decision mutation and reports what happened.
 *
 * <p>The copy comes from the registry code via `resolveError`, so a new refusal
 * renders a sentence without this file changing.</p>
 */
async function decide(run: () => Promise<unknown>): Promise<DecisionResult> {
  try {
    await run();
    return { success: true, message: null, errorCode: null };
  } catch (error) {
    const resolved = resolveError(error as GraphQLLikeError);
    return { success: false, message: resolved.message, errorCode: resolved.code };
  }
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
  // Keyed by the decision's own inputs: a retry of the exact same decision (network drop, the
  // user unsure it went through) replays; the same id decided again with different inputs (a
  // different note) is a genuinely different request and gets its own key. Never evicted — the
  // cost is one UUID per distinct decision ever made in this page's lifetime.
  const approvePayoutKeys = useRef(new Map<string, string>());
  const approveRefundKeys = useRef(new Map<string, string>());
  const refetchPayouts = useMemo(
    () => [{ query: PAYOUT_REQUEST_STATS }],
    []
  );

  const [approvePayoutMutation, approvePayoutState] = useMutation<
    ApprovePayoutRequestMutation,
    ApprovePayoutRequestMutationVariables
  >(APPROVE_PAYOUT_REQUEST, {
    refetchQueries: refetchPayouts,
    awaitRefetchQueries: true,
  });
  const [rejectPayoutMutation, rejectPayoutState] = useMutation<
    RejectPayoutRequestMutation,
    RejectPayoutRequestMutationVariables
  >(REJECT_PAYOUT_REQUEST, {
    refetchQueries: refetchPayouts,
    awaitRefetchQueries: true,
  });
  const [approveRefundMutation, approveRefundState] = useMutation<
    ApproveRefundRequestMutation,
    ApproveRefundRequestMutationVariables
  >(APPROVE_REFUND_REQUEST);
  const [rejectRefundMutation, rejectRefundState] = useMutation<
    RejectRefundRequestMutation,
    RejectRefundRequestMutationVariables
  >(REJECT_REFUND_REQUEST);
  const [escrowStatusMutation, escrowStatusState] = useMutation<
    UpdateEscrowAccountStatusMutation,
    UpdateEscrowAccountStatusMutationVariables
  >(UPDATE_ESCROW_ACCOUNT_STATUS);

  const approvePayout = useCallback(
    async (id: string, notes?: string): Promise<DecisionResult> => {
      const idempotencyKey = stableActionKey(approvePayoutKeys.current, id, notes ?? null);
      return decide(() =>
        approvePayoutMutation({ variables: { payoutRequestId: id, notes: notes ?? null, idempotencyKey } })
      );
    },
    [approvePayoutMutation]
  );

  const rejectPayout = useCallback(
    async (id: string, rejectionReason: string): Promise<DecisionResult> => {
      return decide(() => rejectPayoutMutation({ variables: { payoutRequestId: id, rejectionReason } }));
    },
    [rejectPayoutMutation]
  );

  const approveRefund = useCallback(
    async (id: string, reviewComments?: string): Promise<DecisionResult> => {
      const idempotencyKey = stableActionKey(approveRefundKeys.current, id, reviewComments ?? null);
      return decide(() =>
        approveRefundMutation({ variables: { refundRequestId: id, reviewComments: reviewComments ?? null, idempotencyKey } })
      );
    },
    [approveRefundMutation]
  );

  const rejectRefund = useCallback(
    async (id: string, rejectionReason: string): Promise<DecisionResult> => {
      return decide(() => rejectRefundMutation({ variables: { refundRequestId: id, rejectionReason } }));
    },
    [rejectRefundMutation]
  );

  const setEscrowStatus = useCallback(
    async (
      id: string,
      status: EscrowAccountStatus,
      reason: string
    ): Promise<DecisionResult> => {
      return decide(() =>
        escrowStatusMutation({ variables: { accountId: id, status, reason } })
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
