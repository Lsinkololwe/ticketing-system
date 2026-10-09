'use client';

/**
 * Pending Counts Hook (Admin App)
 *
 * Provides the dynamic "pending queue" badge counts for the admin sidebar,
 * keyed by the exact nav-item ids declared in `apps/admin/src/config/navigation.ts`.
 *
 * Consumers (e.g. the Sidebar badge renderer) get a stable, all-zero default
 * while loading or on error so the UI never crashes on a missing field.
 */

import { useMemo } from 'react';
import { useQuery } from '@apollo/client/react';
import type { PendingCountsQuery, PendingCountsQueryVariables } from '../../../types/graphql';
import { PENDING_COUNTS } from './pending-counts.queries';

// ============================================================================
// Nav-item id keys (must mirror apps/admin/src/config/navigation.ts)
// ============================================================================

export type PendingCountKey =
  | 'pending-approvals'
  | 'organizer-applications'
  | 'event-reviews'
  | 'document-verification'
  | 'payout-requests'
  | 'refund-requests';

export type PendingCounts = Record<PendingCountKey, number>;

const ZERO_COUNTS: PendingCounts = {
  'pending-approvals': 0,
  'organizer-applications': 0,
  'event-reviews': 0,
  'document-verification': 0,
  'payout-requests': 0,
  'refund-requests': 0,
};

// Poll interval consistent with other admin dashboard stat hooks.
const POLL_INTERVAL_MS = 60_000;

export interface UsePendingCountsResult {
  counts: PendingCounts;
  loading: boolean;
  error?: unknown;
}

/**
 * Fetch the pending-queue counts for the admin action center.
 *
 * Runs a single federated query (fanned out to all three subgraphs by the
 * Apollo Router), polled every 60s. Returns counts keyed by nav-item id. The
 * combined "All Approvals" total (`pending-approvals`) is derived from the
 * already-aggregated per-queue counts.
 */
export function usePendingCounts(): UsePendingCountsResult {
  const { data, loading, error } = useQuery<PendingCountsQuery, PendingCountsQueryVariables>(
    PENDING_COUNTS,
    {
      pollInterval: POLL_INTERVAL_MS,
      fetchPolicy: 'cache-and-network',
      errorPolicy: 'all',
    }
  );

  const counts = useMemo<PendingCounts>(() => {
    if (!data) return ZERO_COUNTS;

    const organizerApplications = data.identityPendingCounts?.organizerApplications ?? 0;
    const documentVerifications = data.identityPendingCounts?.documentVerifications ?? 0;
    const eventReviews = data.catalogPendingCounts?.eventReviews ?? 0;
    const payoutRequests = data.bookingPendingCounts?.payoutRequests ?? 0;
    const refundRequests = data.bookingPendingCounts?.refundRequests ?? 0;

    return {
      // "All Approvals" = sum of the approval-type queues (organizer
      // applications + event reviews + document verifications).
      'pending-approvals':
        organizerApplications + eventReviews + documentVerifications,
      'organizer-applications': organizerApplications,
      'event-reviews': eventReviews,
      'document-verification': documentVerifications,
      'payout-requests': payoutRequests,
      'refund-requests': refundRequests,
    };
  }, [data]);

  return { counts, loading, error: error ?? undefined };
}
