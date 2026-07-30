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
  Event,
  EventEdge,
  EventCategory,
  EventCategoryEdge,
  City,
  PageInfo,
  CursorPaginationInput,
} from '../../../types/graphql';
import {
  GET_PUBLISHED_EVENTS,
  GET_UPCOMING_EVENTS,
  GET_EVENT_BY_ID,
  GET_ACTIVE_EVENT_CATEGORIES,
  GET_CITIES_WITH_EVENTS,
} from './events.consumer.queryDefinitions';

export interface EventPageInfo {
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
  endCursor: string | null;
}

interface EventConnectionResult {
  edges: EventEdge[];
  pageInfo: PageInfo;
}

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

function normalisePageInfo(pageInfo?: PageInfo): EventPageInfo {
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
    { publishedEventsCursorPagination: EventConnectionResult },
    { pagination: CursorPaginationInput }
  >(GET_PUBLISHED_EVENTS, {
    variables: { pagination: toCursorPagination(opts) },
    skip: opts?.skip,
    fetchPolicy: 'cache-and-network',
  });

  return { ...connectionToList(data?.publishedEventsCursorPagination), loading, error, refetch };
}

/** Upcoming published events. */
export function useUpcomingEvents(opts?: EventListOptions) {
  const { data, loading, error, refetch } = useQuery<
    { upcomingEventsCursorPagination: EventConnectionResult },
    { pagination: CursorPaginationInput }
  >(GET_UPCOMING_EVENTS, {
    variables: { pagination: toCursorPagination(opts) },
    skip: opts?.skip,
    fetchPolicy: 'cache-and-network',
  });

  return { ...connectionToList(data?.upcomingEventsCursorPagination), loading, error, refetch };
}

/** A single event by id (detail + booking pages). */
export function useEvent(id: string | undefined) {
  const { data, loading, error, refetch } = useQuery<{ event: Event | null }, { id: string }>(
    GET_EVENT_BY_ID,
    {
      variables: { id: id ?? '' },
      skip: !id,
    }
  );
  return { event: data?.event ?? null, loading, error, refetch };
}

type EventCategoryOption = Pick<EventCategory, 'id' | 'name' | 'code' | 'eventCount'>;

/** Active event categories for the filter panel (public cursor query). */
export function useActiveEventCategories() {
  const { data, loading, error } = useQuery<{
    activeEventCategoriesCursorPagination: { edges: EventCategoryEdge[] };
  }>(GET_ACTIVE_EVENT_CATEGORIES, {
    variables: { pagination: { first: 100, after: null, last: null, before: null } },
  });
  const categories: EventCategoryOption[] = (
    data?.activeEventCategoriesCursorPagination.edges ?? []
  ).map((edge) => edge.node);
  return { categories, loading, error };
}

type CityOption = Pick<City, 'id' | 'name' | 'province'>;

/** Cities that currently have events (city filter — public). */
export function useCitiesWithEvents() {
  const { data, loading, error } = useQuery<{ citiesWithEvents: CityOption[] }>(
    GET_CITIES_WITH_EVENTS
  );
  return { cities: data?.citiesWithEvents ?? [], loading, error };
}
