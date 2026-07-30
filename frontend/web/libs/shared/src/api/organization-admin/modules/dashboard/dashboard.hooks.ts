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
} from './dashboard.queries';
import type {
  OrganizerDashboardStats,
  OrganizerUpcomingEvent,
  OrganizerActivityItem,
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
