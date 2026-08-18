'use client';

/**
 * React Hooks for the Organizer Dashboard (Organization Admin App)
 *
 * Thin, typed wrappers over the dashboard queries. Types come from codegen
 * (never hand-defined). Safe defaults are returned so pending/zero-data
 * organizations render clean empty states rather than undefined access.
 */

import { useQuery } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_DASHBOARD_STATS,
  MY_UPCOMING_EVENTS,
  MY_RECENT_ACTIVITY,
  MY_REVENUE_SERIES,
  MY_TICKET_MIX,
  MY_CHECK_IN_RATE,
  MY_PAYOUT_WINDOW,
  MY_PAYOUT_SOURCES,
  PAYOUT_ELIGIBILITY,
} from './dashboard.queries';
import type {
  OrganizerDashboardStats,
  OrganizerUpcomingEvent,
  OrganizerActivityItem,
  OrganizerRevenuePoint,
  OrganizerTicketMix,
  OrganizerCheckInRate,
  OrganizerPayoutWindow,
  PayoutEligibility,
  PayoutBlockedReason,
  OrganizerPayoutSource,
} from '../../../../types/graphql';

interface QueryOptions {
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}

/**
 * Headline dashboard metrics. `stats` is null until loaded.
 */
export function useMyDashboardStats(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myDashboardStats: OrganizerDashboardStats;
  }>(MY_DASHBOARD_STATS, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: options?.skip ?? false,
  });

  return {
    stats: data?.myDashboardStats ?? null,
    loading,
    error,
    refetch,
  };
}

/**
 * Upcoming events for the dashboard list. `events` defaults to an empty array.
 */
export function useMyUpcomingEvents(limit = 5, options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myUpcomingEvents: OrganizerUpcomingEvent[];
  }>(MY_UPCOMING_EVENTS, {
    variables: { limit },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    events: data?.myUpcomingEvents ?? [],
    loading,
    error,
    refetch,
  };
}

/**
 * Recent activity feed for the dashboard. `activity` defaults to an empty array.
 */
export function useMyRecentActivity(limit = 5, options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myRecentActivity: OrganizerActivityItem[];
  }>(MY_RECENT_ACTIVITY, {
    variables: { limit },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    activity: data?.myRecentActivity ?? [],
    loading,
    error,
    refetch,
  };
}

// =============================================================================
// DASHBOARD ANALYTICS
//
// Back the dashboard's data-viz tiles. Each returns raw counts and the
// denominator they were computed against — never a bare percentage — so the
// tiles can print the denominator beside every rate.
//
// Contract: frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md
// =============================================================================

/**
 * Revenue per complete calendar month, oldest first. `points` defaults to an
 * empty array, which the tile renders as an empty state rather than a flat
 * zero line — no data and zero revenue are different findings.
 */
export function useMyRevenueSeries(months = 6, options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myRevenueSeries: OrganizerRevenuePoint[];
  }>(MY_REVENUE_SERIES, {
    variables: { months },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    points: data?.myRevenueSeries ?? [],
    loading,
    error,
    refetch,
  };
}

/**
 * Sold-ticket breakdown by tier. `mix` is null until loaded.
 */
export function useMyTicketMix(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myTicketMix: OrganizerTicketMix;
  }>(MY_TICKET_MIX, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    mix: data?.myTicketMix ?? null,
    loading,
    error,
    refetch,
  };
}

/**
 * Gate attendance for the most recent event that has run.
 *
 * `rate` stays null when no event has run yet. The tile must render an empty
 * state in that case, never 0% — a rate of zero asserts that nobody showed up.
 */
export function useMyCheckInRate(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myCheckInRate: OrganizerCheckInRate | null;
  }>(MY_CHECK_IN_RATE, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    rate: data?.myCheckInRate ?? null,
    loading,
    error,
    refetch,
  };
}

/**
 * Withdrawable balance plus the escrow hold on the next tranche.
 */
export function useMyPayoutWindow(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myPayoutWindow: OrganizerPayoutWindow;
  }>(MY_PAYOUT_WINDOW, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    window: data?.myPayoutWindow ?? null,
    loading,
    error,
    refetch,
  };
}

/**
 * Escrow accounts the organizer can draw a payout from right now.
 *
 * `sources` defaults to an empty array, which the payout dialog must render as
 * "nothing available to withdraw" — never as a disabled-looking form with a
 * plausible default account filled in.
 */
export function useMyPayoutSources(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<{
    myPayoutSources: OrganizerPayoutSource[];
  }>(MY_PAYOUT_SOURCES, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    sources: data?.myPayoutSources ?? [],
    loading,
    error,
    refetch,
  };
}

/** Human wording for each blocked reason. Types come from codegen. */
const PAYOUT_BLOCKED_COPY: Record<PayoutBlockedReason, string> = {
  NO_ESCROW_ACCOUNT: 'No payout account is available for this event.',
  EVENT_NOT_COMPLETED: 'The event has not finished yet.',
  HOLD_NOT_ELAPSED: 'The hold period has not elapsed.',
  OPEN_DISPUTES: 'There is an open dispute against this event.',
  BELOW_MINIMUM: 'The balance is below the payout minimum.',
  PAYOUT_ALREADY_REQUESTED: 'A payout request for this event is already open.',
};

/**
 * Whether a payout can be requested for one event, and if not, why.
 *
 * Returns the reasons as sentences so a screen can say "the hold opens on the
 * 14th" instead of greying out a button with no explanation — which produces a
 * support message rather than a wait.
 *
 * Not authoritative: the server re-evaluates at request time, because minutes
 * pass between a screen rendering and a button being pressed and a chargeback
 * can arrive in that window.
 */
export function usePayoutEligibility(
  eventId: string | null | undefined,
  options?: QueryOptions
) {
  const { data, loading, error, refetch } = useQuery<{
    payoutEligibility: PayoutEligibility;
  }>(PAYOUT_ELIGIBILITY, {
    variables: { eventId },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip || !eventId,
  });

  const eligibility = data?.payoutEligibility ?? null;

  return {
    eligibility,
    // Defaults to FALSE while loading and on error. An unknown answer must not
    // render an enabled withdraw button — the failure mode of guessing wrong in
    // that direction is a request the server refuses, in front of someone
    // trying to get paid.
    canRequest: eligibility?.eligible ?? false,
    blockedReasons: (eligibility?.reasons ?? []).map(
      (reason) => PAYOUT_BLOCKED_COPY[reason] ?? 'This payout is not available yet.'
    ),
    /** Null when the hold has elapsed, so a screen can omit the line entirely. */
    opensAt: eligibility?.opensAt ?? null,
    loading,
    error,
    refetch,
  };
}
