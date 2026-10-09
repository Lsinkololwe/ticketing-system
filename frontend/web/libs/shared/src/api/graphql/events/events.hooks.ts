/**
 * Events — Consumer React hooks.
 *
 * Components consume these hooks, never Apollo `useQuery` directly (repo rule).
 * All result shapes are typed from the codegen'd schema types.
 *
 * Browsing uses the PUBLIC cursor queries (see queryDefinitions). The cursor
 * `EventConnection` is normalised here back into a flat `{ events, pageInfo }`
 * view-model so page components stay pagination-shape-agnostic.
 */

'use client';

import { useQuery } from '@apollo/client/react';
import type {
  CursorPaginationInput,
  GetPublishedEventsQuery,
  GetPublishedEventsQueryVariables,
  GetEventByIdQuery,
  GetEventByIdQueryVariables,
  GetActiveEventCategoriesQuery,
  GetActiveEventCategoriesQueryVariables,
  GetCitiesWithEventsQuery,
  GetCitiesWithEventsQueryVariables,
} from '../../../types/graphql';
import {
  GET_PUBLISHED_EVENTS,
  GET_EVENT_BY_ID,
  GET_ACTIVE_EVENT_CATEGORIES,
  GET_CITIES_WITH_EVENTS,
} from './events.consumer.queryDefinitions';
import type { FlatPageInfo } from '../../../types/pageInfo';

/**
 * Page metadata for this surface, with the field set taken from the generated
 * schema type rather than re-declared — see `types/pageInfo`.
 */
export type EventPageInfo = FlatPageInfo;

/** One row of the published-events list — the `EventCardFields` selection. */
export type PublishedEventRow =
  GetPublishedEventsQuery['discoverEvents']['edges'][number]['node'];

/** The full detail shape returned by `event(id)` — includes ticket tiers. */
export type EventDetail = NonNullable<GetEventByIdQuery['event']>;

/** One ticket tier of an event's detail — the `ticketTiers` selection. */
export type TicketTierRow = NonNullable<EventDetail['ticketTiers']>[number];

type EventConnectionResult = GetPublishedEventsQuery['discoverEvents'];

export interface EventListOptions {
  /** Page size (maps to cursor `first`). */
  size?: number;
  /** Opaque cursor to fetch after (forward pagination). */
  after?: string | null;
  skip?: boolean;
}

function toCursorPagination(opts?: EventListOptions): CursorPaginationInput {
  return {
    first: opts?.size ?? 20,
    after: opts?.after ?? null,
    last: null,
    before: null,
  };
}

const EMPTY_PAGE_INFO: EventPageInfo = {
  totalElements: 0,
  totalPages: 0,
  pageNumber: 0,
  pageSize: 0,
  hasNext: false,
  hasPrevious: false,
  endCursor: null,
};

function normalisePageInfo(pageInfo?: EventConnectionResult['pageInfo']): EventPageInfo {
  if (!pageInfo) return EMPTY_PAGE_INFO;
  return {
    totalElements: pageInfo.totalElements ?? 0,
    totalPages: pageInfo.totalPages ?? 0,
    pageNumber: pageInfo.currentPage ?? 0,
    pageSize: pageInfo.pageSize ?? 0,
    hasNext: pageInfo.hasNext ?? false,
    hasPrevious: pageInfo.hasPrevious ?? false,
    endCursor: pageInfo.endCursor ?? null,
  };
}

function connectionToList(connection?: EventConnectionResult) {
  return {
    events: (connection?.edges ?? []).map((edge) => edge.node),
    pageInfo: normalisePageInfo(connection?.pageInfo),
  };
}

/** Published events for public browsing. */
export function usePublishedEvents(opts?: EventListOptions) {
  const { data, loading, error, refetch } = useQuery<
    GetPublishedEventsQuery,
    GetPublishedEventsQueryVariables
  >(GET_PUBLISHED_EVENTS, {
    variables: { pagination: toCursorPagination(opts) },
    skip: opts?.skip,
    fetchPolicy: 'cache-and-network',
  });

  return { ...connectionToList(data?.discoverEvents), loading, error, refetch };
}

/** A single event by id (detail + booking pages). */
export function useEvent(id: string | undefined) {
  const { data, loading, error, refetch } = useQuery<GetEventByIdQuery, GetEventByIdQueryVariables>(
    GET_EVENT_BY_ID,
    {
      variables: { id: id ?? '' },
      skip: !id,
    }
  );
  return { event: data?.event ?? null, loading, error, refetch };
}

/** One row of the active-categories filter list. */
export type EventCategoryOption = GetActiveEventCategoriesQuery['categories'][number];

/** Active event categories for the filter panel (a bounded public list). */
export function useActiveEventCategories() {
  const { data, loading, error } = useQuery<
    GetActiveEventCategoriesQuery,
    GetActiveEventCategoriesQueryVariables
  >(GET_ACTIVE_EVENT_CATEGORIES);
  const categories: EventCategoryOption[] = data?.categories ?? [];
  return { categories, loading, error };
}

/** One row of the cities-with-events filter list. */
export type CityOption = GetCitiesWithEventsQuery['citiesWithEvents'][number];

/** Cities that currently have events (city filter — public). */
export function useCitiesWithEvents() {
  const { data, loading, error } = useQuery<
    GetCitiesWithEventsQuery,
    GetCitiesWithEventsQueryVariables
  >(GET_CITIES_WITH_EVENTS);
  return { cities: data?.citiesWithEvents ?? [], loading, error };
}
