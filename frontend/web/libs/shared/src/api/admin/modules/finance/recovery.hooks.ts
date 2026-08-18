'use client';

/**
 * Payout recovery hooks. See `recovery.queries.ts` for why the scope is payouts
 * rather than the design's eight sources.
 */

import { useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import type {
  PayoutIssueType,
  PayoutRecoverySummary,
  PayoutRequest,
} from '../../../../types/graphql';
import type { FinancePageInfo, UseFinancePageOptions, DecisionResult } from './finance.hooks';
import {
  MARK_PAYOUT_FOR_REVIEW,
  PAYOUTS_FOR_REVIEW,
  PAYOUT_RECOVERY_SUMMARY,
  RETRYABLE_PAYOUT_REQUESTS,
  RETRY_PAYOUT_REQUEST,
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
  stuck: 'stuckPayoutRequestsOffsetPagination',
  retryable: 'retryablePayoutRequestsOffsetPagination',
  review: 'payoutRequestsForReviewOffsetPagination',
};

export interface UseRecoveryQueueResult {
  items: PayoutRequest[];
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
  const { data, loading, error, refetch } = useQuery<Record<string, {
    data: PayoutRequest[];
    pagination: Partial<FinancePageInfo> | null;
  }>>(QUERY_FOR[bucket], {
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

export interface UseRecoverySummaryResult {
  summary: PayoutRecoverySummary | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function usePayoutRecoverySummary(): UseRecoverySummaryResult {
  const { data, loading, error, refetch } = useQuery<{
    payoutRecoverySummary: PayoutRecoverySummary;
  }>(PAYOUT_RECOVERY_SUMMARY, {
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

interface Envelope {
  success: boolean;
  message: string | null;
  errors: string[];
}

function envelopeOf(payload: Envelope | undefined, fallback: string): DecisionResult {
  if (!payload) return { success: false, message: fallback, errors: [fallback] };
  return {
    success: payload.success,
    message: payload.message ?? null,
    errors: payload.errors ?? [],
  };
}

export interface UseRecoveryActionsResult {
  retry: (id: string) => Promise<DecisionResult>;
  markForReview: (
    id: string,
    issueType: PayoutIssueType,
    notes?: string
  ) => Promise<DecisionResult>;
  submitting: boolean;
}

export function useRecoveryActions(): UseRecoveryActionsResult {
  const [retryMutation, retryState] = useMutation(RETRY_PAYOUT_REQUEST, {
    refetchQueries: [{ query: PAYOUT_RECOVERY_SUMMARY }],
    awaitRefetchQueries: true,
  });
  const [markMutation, markState] = useMutation(MARK_PAYOUT_FOR_REVIEW, {
    refetchQueries: [{ query: PAYOUT_RECOVERY_SUMMARY }],
    awaitRefetchQueries: true,
  });

  const retry = useCallback(
    async (id: string) => {
      const { data } = await retryMutation({ variables: { payoutRequestId: id } });
      return envelopeOf(
        (data as { retryPayoutRequest?: Envelope } | undefined)?.retryPayoutRequest,
        'The server did not confirm the retry.'
      );
    },
    [retryMutation]
  );

  const markForReview = useCallback(
    async (id: string, issueType: PayoutIssueType, notes?: string) => {
      const { data } = await markMutation({
        variables: { payoutRequestId: id, issueType, notes: notes ?? null },
      });
      return envelopeOf(
        (data as { markPayoutForReview?: Envelope } | undefined)?.markPayoutForReview,
        'The server did not confirm the flag.'
      );
    },
    [markMutation]
  );

  return { retry, markForReview, submitting: retryState.loading || markState.loading };
}
