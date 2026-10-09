'use client';

/**
 * Payout recovery hooks. See `recovery.queries.ts` for why the scope is payouts
 * rather than the design's eight sources.
 */

import { useQuery } from '@apollo/client/react';
import type {
  PayoutRecoverySummaryQuery,
  StuckPayoutRequestsQuery,
  StuckPayoutRequestsQueryVariables,
} from '../../../../types/graphql';
import type { FinancePageInfo, UseFinancePageOptions } from './finance.hooks';
import {
  PAYOUTS_FOR_REVIEW,
  PAYOUT_RECOVERY_SUMMARY,
  RETRYABLE_PAYOUT_REQUESTS,
  STUCK_PAYOUT_REQUESTS,
} from './recovery.queries';

/** Which slice of the recovery queue to show. */
export type RecoveryBucket = 'stuck' | 'retryable' | 'review';

const QUERY_FOR: Record<RecoveryBucket, typeof STUCK_PAYOUT_REQUESTS> = {
  stuck: STUCK_PAYOUT_REQUESTS,
  retryable: RETRYABLE_PAYOUT_REQUESTS,
  review: PAYOUTS_FOR_REVIEW,
};

const FIELD_FOR: Record<RecoveryBucket, string> = {
  stuck: 'stuckPayoutRequests',
  retryable: 'retryablePayoutRequests',
  review: 'payoutRequestsForReview',
};

/**
 * The row shape all three recovery queries select — `STUCK_PAYOUT_REQUESTS`,
 * `RETRYABLE_PAYOUT_REQUESTS` and `PAYOUTS_FOR_REVIEW` share the same two
 * fragments, so `StuckPayoutRequestsQuery`'s row type describes all three.
 */
export type RecoveryPayoutRow = StuckPayoutRequestsQuery['stuckPayoutRequests']['data'][number];

/** The `{ data, pagination }` page shape every recovery query returns, by field name. */
type RecoveryPage = { data: RecoveryPayoutRow[]; pagination: Partial<FinancePageInfo> | null };

export interface UseRecoveryQueueResult {
  items: RecoveryPayoutRow[];
  pageInfo: FinancePageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useRecoveryQueue(
  bucket: RecoveryBucket,
  options: UseFinancePageOptions = {}
): UseRecoveryQueueResult {
  const size = options.size ?? 20;
  const { data, loading, error, refetch } = useQuery<
    Record<string, RecoveryPage>,
    StuckPayoutRequestsQueryVariables & { reviewStatus?: null }
  >(QUERY_FOR[bucket], {
    variables: {
      pagination: { page: options.page ?? 0, size, sortBy: 'requestedAt', sortDirection: 'DESC' },
      // Only the review query declares this argument; the other two ignore an
      // unused variable rather than erroring on it.
      ...(bucket === 'review' ? { reviewStatus: null } : {}),
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.[FIELD_FOR[bucket]];
  const p = page?.pagination;

  return {
    items: page?.data ?? [],
    pageInfo: {
      totalCount: p?.totalCount ?? 0,
      pageSize: p?.pageSize ?? size,
      currentPage: p?.currentPage ?? 0,
      totalPages: p?.totalPages ?? 0,
      hasNextPage: p?.hasNextPage ?? false,
      hasPreviousPage: p?.hasPreviousPage ?? false,
    },
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

/** The summary shape this screen actually selects — see `PAYOUT_RECOVERY_SUMMARY`. */
export type PayoutRecoverySummaryVM = PayoutRecoverySummaryQuery['payoutRecoverySummary'];

export interface UseRecoverySummaryResult {
  summary: PayoutRecoverySummaryVM | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function usePayoutRecoverySummary(): UseRecoverySummaryResult {
  const { data, loading, error, refetch } = useQuery<PayoutRecoverySummaryQuery>(
    PAYOUT_RECOVERY_SUMMARY,
    {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    summary: data?.payoutRecoverySummary ?? null,
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

