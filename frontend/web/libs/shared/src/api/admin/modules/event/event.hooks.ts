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
  EventStatus,
  AdminEventsQuery,
  AdminEventsQueryVariables,
  EventStatsQuery,
  AdminEventCategoriesQuery,
  AdminEventCategoriesQueryVariables,
  AdminLocationsQuery,
  AdminLocationsQueryVariables,
  ApproveEventMutation,
  ApproveEventMutationVariables,
  RejectEventMutation,
  RejectEventMutationVariables,
  RequestEventChangesMutation,
  RequestEventChangesMutationVariables,
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
import type { OffsetPageInfo } from '../../../../types/pageInfo';

/**
 * Page metadata for this surface, with the field set taken from the generated
 * schema type rather than re-declared — see `types/pageInfo`.
 */
export type EventPageInfo = OffsetPageInfo;

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

/** The row shape this screen actually selects — see `ADMIN_EVENTS`. */
export type AdminEventRow = AdminEventsQuery['events']['content'][number];

export interface UseAdminEventsResult {
  events: AdminEventRow[];
  pageInfo: EventPageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminEvents(options: UseAdminEventsOptions = {}): UseAdminEventsResult {
  const size = options.size ?? DEFAULT_SIZE;
  const { data, loading, error, refetch } = useQuery<AdminEventsQuery, AdminEventsQueryVariables>(
    ADMIN_EVENTS,
    {
    variables: {
      // The server declares this filter non-null, so an unfiltered view sends
      // an object of nulls for every field this screen does not expose rather
      // than omitting the argument.
      filter: {
        status: options.status ?? null,
        searchQuery: options.searchQuery || null,
        approvedNotPublished: null,
        categoryId: null,
        cityId: null,
        country: null,
        createdAfter: null,
        createdBefore: null,
        daysSinceApprovalMax: null,
        daysSinceApprovalMin: null,
        eventDateAfter: null,
        eventDateBefore: null,
        organizerId: null,
        overdue: null,
        published: null,
        statuses: null,
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

  // `errorPolicy: 'all'` makes Apollo type `data` as deeply partial, since a
  // partial GraphQL response is possible alongside errors. Absent an error,
  // the response matches the query exactly, so the read site trusts that.
  const page = data?.events as AdminEventsQuery['events'] | undefined;

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

/** The stats shape this screen actually selects — see `EVENT_STATS`. */
export type AdminEventStats = EventStatsQuery['eventStats'];

export interface UseEventStatsResult {
  stats: AdminEventStats | null;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useEventStats(): UseEventStatsResult {
  const { data, loading, error, refetch } = useQuery<EventStatsQuery>(EVENT_STATS, {
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

/**
 * One category row. `id` is the reference-data row's id (what the writers take); events refer to a category by its
 * `code`. `eventCount` is known for active categories only, so an inactive one reads `null`, not 0.
 */
export interface AdminEventCategoryRow {
  id: string;
  code: string;
  name: string;
  description: string | null;
  isActive: boolean;
  eventCount: number | null;
}

export interface UseEventCategoriesResult {
  categories: AdminEventCategoryRow[];
  pageInfo: EventPageInfo;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

export function useAdminEventCategories(
  options: { page?: number; size?: number } = {}
): UseEventCategoriesResult {
  const size = options.size ?? 50;
  const { data, loading, error, refetch } = useQuery<
    AdminEventCategoriesQuery,
    AdminEventCategoriesQueryVariables
  >(ADMIN_EVENT_CATEGORIES, {
    variables: {
      pagination: {
        page: options.page ?? 0,
        size,
        sortBy: 'displayOrder',
        sortDirection: 'ASC',
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.referenceDataAll;
  const counts = new Map((data?.categories ?? []).map((c) => [c.code, c.eventCount ?? null] as const));
  const categories: AdminEventCategoryRow[] = (page?.content ?? []).map((row) => ({
    id: row.id,
    code: row.code,
    name: row.name,
    description: row.description ?? null,
    isActive: row.isActive,
    eventCount: counts.get(row.code) ?? null,
  }));

  return {
    categories,
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

/** The row shape this screen actually selects — see `ADMIN_LOCATIONS`. */
export type AdminLocationRow = AdminLocationsQuery['locations']['edges'][number]['node'];

export interface UseAdminLocationsResult {
  locations: AdminLocationRow[];
  totalCount: number;
  loading: boolean;
  error?: Error;
  refetch: () => void;
}

/** Cursor-paged; catalog exposes no offset variant for locations. */
export function useAdminLocations(options: { first?: number } = {}): UseAdminLocationsResult {
  const { data, loading, error, refetch } = useQuery<
    AdminLocationsQuery,
    AdminLocationsQueryVariables
  >(ADMIN_LOCATIONS, {
    variables: { pagination: { first: options.first ?? 50, after: null, before: null, last: null } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const conn = data?.locations as AdminLocationsQuery['locations'] | undefined;

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

/**
 * None of the three decision mutations return a success envelope — each just
 * returns the event's new `{ id, status }` (see `ApproveEventMutation` et al.
 * in the generated types). Apollo rejects the promise on a GraphQL error
 * (default `errorPolicy: 'none'`), so "resolved with the row back" is the
 * actual success signal, and a caught error is the actual failure signal.
 */
function decided(id: string | undefined, fallback: string): EventDecisionResult {
  if (!id) return { success: false, message: fallback, errors: [fallback] };
  return { success: true, message: null, errors: [] };
}

function refused(error: unknown, fallback: string): EventDecisionResult {
  const message = error instanceof Error ? error.message : fallback;
  return { success: false, message, errors: [message] };
}

export interface UseEventDecisionsResult {
  approve: (eventId: string, comments?: string) => Promise<EventDecisionResult>;
  reject: (eventId: string, comments: string) => Promise<EventDecisionResult>;
  requestChanges: (eventId: string, comments: string) => Promise<EventDecisionResult>;
  submitting: boolean;
}

export function useEventDecisions(): UseEventDecisionsResult {
  const refetchStats = [{ query: EVENT_STATS }];

  const [approveMutation, approveState] = useMutation<
    ApproveEventMutation,
    ApproveEventMutationVariables
  >(APPROVE_EVENT, {
    refetchQueries: refetchStats,
    awaitRefetchQueries: true,
  });
  const [rejectMutation, rejectState] = useMutation<
    RejectEventMutation,
    RejectEventMutationVariables
  >(REJECT_EVENT, {
    refetchQueries: refetchStats,
    awaitRefetchQueries: true,
  });
  const [changesMutation, changesState] = useMutation<
    RequestEventChangesMutation,
    RequestEventChangesMutationVariables
  >(REQUEST_EVENT_CHANGES, {
    refetchQueries: refetchStats,
    awaitRefetchQueries: true,
  });

  const approve = useCallback(
    async (eventId: string, comments?: string) => {
      try {
        const { data } = await approveMutation({
          variables: { eventId, comments: comments ?? null },
        });
        return decided(data?.approveEvent.id, 'The server did not confirm the approval.');
      } catch (error) {
        return refused(error, 'The server did not confirm the approval.');
      }
    },
    [approveMutation]
  );

  const reject = useCallback(
    async (eventId: string, comments: string) => {
      try {
        const { data } = await rejectMutation({ variables: { eventId, comments } });
        return decided(data?.rejectEvent.id, 'The server did not confirm the rejection.');
      } catch (error) {
        return refused(error, 'The server did not confirm the rejection.');
      }
    },
    [rejectMutation]
  );

  const requestChanges = useCallback(
    async (eventId: string, comments: string) => {
      try {
        const { data } = await changesMutation({ variables: { eventId, comments } });
        return decided(data?.requestEventChanges.id, 'The server did not confirm the request.');
      } catch (error) {
        return refused(error, 'The server did not confirm the request.');
      }
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
