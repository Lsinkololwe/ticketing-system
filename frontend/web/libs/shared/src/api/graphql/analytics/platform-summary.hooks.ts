'use client';

/**
 * Platform summary hook — real numbers for the admin dashboard.
 *
 * <h2>Why this exists</h2>
 * The admin dashboard rendered nine hardcoded values — 156 events, 12,847
 * tickets, K 2.4M revenue, invented organizer names — while `platformSummary`
 * sat unused in the schema. A fabricated dashboard is worse than an empty one:
 * an empty one tells you the platform is new, a fabricated one tells you it is
 * thriving.
 *
 * <p>Returns nulls rather than zeros when the query has not resolved, so the
 * caller can render a skeleton instead of briefly claiming the platform has
 * zero revenue.
 */

import { useQuery } from '@apollo/client/react';
import type { PlatformSummary } from '../../../types/graphql';
import { PLATFORM_SUMMARY } from './platform-summary.queries';

export interface UsePlatformSummaryResult {
  summary: PlatformSummary | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function usePlatformSummary(): UsePlatformSummaryResult {
  const { data, loading, error, refetch } = useQuery<{
    platformSummary: PlatformSummary;
  }>(PLATFORM_SUMMARY, {
    fetchPolicy: 'cache-and-network',
    // A dashboard tile that silently shows nothing because one federated
    // subgraph is down is worse than one that shows what resolved.
    errorPolicy: 'all',
  });

  return {
    summary: data?.platformSummary ?? null,
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}
