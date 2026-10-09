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
} from './dashboard.queries';
import type {
  MyDashboardStatsQuery,
  MyDashboardStatsQueryVariables,
  MyUpcomingEventsQuery,
  MyUpcomingEventsQueryVariables,
  MyRecentActivityQuery,
  MyRecentActivityQueryVariables,
  MyRevenueSeriesQuery,
  MyRevenueSeriesQueryVariables,
  MyTicketMixQuery,
  MyTicketMixQueryVariables,
  MyCheckInRateQuery,
  MyCheckInRateQueryVariables,
  MyPayoutWindowQuery,
  MyPayoutWindowQueryVariables,
  MyPayoutSourcesQuery,
  MyPayoutSourcesQueryVariables,
} from '../../../../types/graphql';

interface QueryOptions {
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}

/**
 * Headline dashboard metrics. `stats` is null until loaded.
 */
export function useMyDashboardStats(options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<MyDashboardStatsQuery, MyDashboardStatsQueryVariables>(MY_DASHBOARD_STATS, {
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
  const { data, loading, error, refetch } = useQuery<MyUpcomingEventsQuery, MyUpcomingEventsQueryVariables>(MY_UPCOMING_EVENTS, {
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
  const { data, loading, error, refetch } = useQuery<MyRecentActivityQuery, MyRecentActivityQueryVariables>(MY_RECENT_ACTIVITY, {
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
// =============================================================================

/**
 * Revenue per complete calendar month, oldest first. `points` defaults to an
 * empty array, which the tile renders as an empty state rather than a flat
 * zero line — no data and zero revenue are different findings.
 */
export function useMyRevenueSeries(months = 6, options?: QueryOptions) {
  const { data, loading, error, refetch } = useQuery<MyRevenueSeriesQuery, MyRevenueSeriesQueryVariables>(MY_REVENUE_SERIES, {
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
  const { data, loading, error, refetch } = useQuery<MyTicketMixQuery, MyTicketMixQueryVariables>(MY_TICKET_MIX, {
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
  const { data, loading, error, refetch } = useQuery<MyCheckInRateQuery, MyCheckInRateQueryVariables>(MY_CHECK_IN_RATE, {
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
  const { data, loading, error, refetch } = useQuery<MyPayoutWindowQuery, MyPayoutWindowQueryVariables>(MY_PAYOUT_WINDOW, {
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
  const { data, loading, error, refetch } = useQuery<MyPayoutSourcesQuery, MyPayoutSourcesQueryVariables>(MY_PAYOUT_SOURCES, {
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

