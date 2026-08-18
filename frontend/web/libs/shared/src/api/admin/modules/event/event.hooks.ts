'use client';

/**
 * Admin event hooks — the three views of `Admin - Events.dc.html`.
 *
 * Catalog's flat offset page is normalised into the same `pageInfo` shape the
 * finance hooks return, so a table component does not need to know which
 * subgraph fed it.
 */

import { useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import type {
  Event,
  EventCategory,
  EventStats,
  EventStatus,
  Location,
} from '../../../../types/graphql';
import {
  ADMIN_EVENTS,
  ADMIN_EVENT_CATEGORIES,
  ADMIN_LOCATIONS,
  APPROVE_EVENT,
  EVENT_STATS,
  REJECT_EVENT,
  REQUEST_EVENT_CHANGES,
} from './event.queries';

export interface EventPageInfo {
  totalCount: number;
  pageSize: number;
  currentPage: number;
  totalPages: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
}

/** Catalog's flat page → the shared shape. */
interface FlatPage<T> {
  content: T[];
  pageNumber: number | null;
  pageSize: number | null;
  totalElements: number | null;
  totalPages: number | null;
  hasNext: boolean | null;
  hasPrevious: boolean | null;
}

function normalise<T>(page: FlatPage<T> | undefined, fallbackSize: number): EventPageInfo {
  return {
    totalCount: page?.totalElements ?? 0,
    pageSize: page?.pageSize ?? fallbackSize,
    currentPage: page?.pageNumber ?? 0,
    totalPages: page?.totalPages ?? 0,
    hasNextPage: page?.hasNext ?? false,
    hasPreviousPage: page?.hasPrevious ?? false,
  };
}

const DEFAULT_SIZE = 20;

// =============================================================================
// EVENTS
// =============================================================================

export interface UseAdminEventsOptions {
  status?: EventStatus | null;
  searchQuery?: string | null;
  page?: number;
  size?: number;
}

export interface UseAdminEventsResult {
  events: Event[];
  pageInfo: EventPageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminEvents(options: UseAdminEventsOptions = {}): UseAdminEventsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<{
    eventsOffsetPagination: FlatPage<Event>;
  }>(ADMIN_EVENTS, {
    variables: {
      filter: {
        status: options.status ?? null,
        searchQuery: options.searchQuery || null,
      },
      pagination: {
        page: options.page ?? 0,
        size,
        sortBy: 'eventDateTime',
        sortDirection: 'DESC',
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.eventsOffsetPagination;

  return {
    events: page?.content ?? [],
    pageInfo: normalise(page, size),
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

export interface UseEventStatsResult {
  stats: EventStats | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useEventStats(): UseEventStatsResult {
  const { data, loading, error, refetch } = useQuery<{ eventStats: EventStats }>(EVENT_STATS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    stats: data?.eventStats ?? null,
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

// =============================================================================
// CATEGORIES
// =============================================================================

export interface UseEventCategoriesResult {
  categories: EventCategory[];
  pageInfo: EventPageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminEventCategories(
  options: { page?: number; size?: number } = {}
): UseEventCategoriesResult {
  const size = options.size ?? 50;
  const { data, loading, error, refetch } = useQuery<{
    eventCategoriesOffsetPagination: FlatPage<EventCategory>;
  }>(ADMIN_EVENT_CATEGORIES, {
    variables: {
      pagination: {
        page: options.page ?? 0,
        size,
        sortBy: 'sortOrder',
        sortDirection: 'ASC',
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.eventCategoriesOffsetPagination;

  return {
    categories: page?.content ?? [],
    pageInfo: normalise(page, size),
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

// =============================================================================
// LOCATIONS
// =============================================================================

export interface UseAdminLocationsResult {
  locations: Location[];
  totalCount: number;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

/** Cursor-paged; catalog exposes no offset variant for locations. */
export function useAdminLocations(options: { first?: number } = {}): UseAdminLocationsResult {
  const { data, loading, error, refetch } = useQuery<{
    locationsCursorPagination: {
      edges: { node: Location }[];
      pageInfo: { hasNextPage: boolean | null; endCursor: string | null; totalCount: number | null };
    };
  }>(ADMIN_LOCATIONS, {
    variables: { pagination: { first: options.first ?? 50 } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const conn = data?.locationsCursorPagination;

  return {
    locations: conn?.edges?.map((e) => e.node) ?? [],
    totalCount: conn?.pageInfo?.totalCount ?? conn?.edges?.length ?? 0,
    loading,
    error: error as Error | undefined,
    refetch: () => {
      void refetch();
    },
  };
}

// =============================================================================
// APPROVAL DECISIONS
// =============================================================================

export interface EventDecisionResult {
  success: boolean;
  message: string | null;
  errors: string[];
}

interface Envelope {
  success: boolean;
  message: string | null;
  errors: string[];
}

function envelopeOf(payload: Envelope | undefined, fallback: string): EventDecisionResult {
  if (!payload) return { success: false, message: fallback, errors: [fallback] };
  return {
    success: payload.success,
    message: payload.message ?? null,
    errors: payload.errors ?? [],
  };
}

export interface UseEventDecisionsResult {
  approve: (eventId: string, comments?: string) => Promise<EventDecisionResult>;
  reject: (eventId: string, comments: string) => Promise<EventDecisionResult>;
  requestChanges: (eventId: string, comments: string) => Promise<EventDecisionResult>;
  submitting: boolean;
}

export function useEventDecisions(): UseEventDecisionsResult {
  const refetchStats = [{ query: EVENT_STATS }];

  const [approveMutation, approveState] = useMutation(APPROVE_EVENT, {
    refetchQueries: refetchStats,
    awaitRefetchQueries: true,
  });
  const [rejectMutation, rejectState] = useMutation(REJECT_EVENT, {
    refetchQueries: refetchStats,
    awaitRefetchQueries: true,
  });
  const [changesMutation, changesState] = useMutation(REQUEST_EVENT_CHANGES, {
    refetchQueries: refetchStats,
    awaitRefetchQueries: true,
  });

  const approve = useCallback(
    async (eventId: string, comments?: string) => {
      const { data } = await approveMutation({
        variables: { eventId, comments: comments ?? null },
      });
      return envelopeOf(
        (data as { approveEvent?: Envelope } | undefined)?.approveEvent,
        'The server did not confirm the approval.'
      );
    },
    [approveMutation]
  );

  const reject = useCallback(
    async (eventId: string, comments: string) => {
      const { data } = await rejectMutation({ variables: { eventId, comments } });
      return envelopeOf(
        (data as { rejectEvent?: Envelope } | undefined)?.rejectEvent,
        'The server did not confirm the rejection.'
      );
    },
    [rejectMutation]
  );

  const requestChanges = useCallback(
    async (eventId: string, comments: string) => {
      const { data } = await changesMutation({ variables: { eventId, comments } });
      return envelopeOf(
        (data as { requestEventChanges?: Envelope } | undefined)?.requestEventChanges,
        'The server did not confirm the request.'
      );
    },
    [changesMutation]
  );

  return {
    approve,
    reject,
    requestChanges,
    submitting: approveState.loading || rejectState.loading || changesState.loading,
  };
}
