'use client';

import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { EVENT_CARD_FIELDS } from '../events/events.consumer.queryDefinitions';
import type { EventDiscoverySort, BuyerTrendingEventsQuery, BuyerTrendingEventsQueryVariables, BuyerRecommendedEventsQuery, BuyerRecommendedEventsQueryVariables } from '../../../types/graphql';

/** One event as shown on a card: the shared EventCardFields selection plus the sold-out flag. */
export interface EventCardRow {
  id: string;
  title: string;
  description: string;
  status: string;
  featured: boolean;
  eventDateTime: string;
  endDateTime: string;
  cityName: string | null;
  locationName: string | null;
  bannerImageUrl: string | null;
  galleryImages: string[] | null;
  organizerName: string;
  soldTickets: number;
  totalCapacity: number;
  availableTickets: number;
  minTicketPrice: string | number | null;
  maxTicketPrice: string | number | null;
  currency: string | null;
  soldOut: boolean;
  category: { id: string; name: string } | null;
}

export interface DiscoverFilter {
  searchQuery?: string;
  categoryId?: string;
  /** The city's id (from `citiesWithEvents`). The feed has exactly five filters (ET-CAT-003 R4): category, city, start range, price range, text. */
  cityId?: string;
  startDate?: string;
  endDate?: string;
  minPrice?: number;
  maxPrice?: number;
}

/** The orders the public feed offers; the catalog does the ordering, not the browser. */
export type DiscoverSort = EventDiscoverySort;

const DISCOVER = gql`
  query DiscoverEvents($filter: EventDiscoveryFilterInput!, $pagination: CursorPaginationInput, $sort: EventDiscoverySort) {
    discoverEvents(filter: $filter, pagination: $pagination, sort: $sort) {
      edges {
        node {
          ...EventCardFields
          soldOut
        }
      }
      pageInfo {
        totalElements
        hasNext
        endCursor
      }
    }
  }
  ${EVENT_CARD_FIELDS}
`;

interface DiscoverData {
  discoverEvents: {
    edges: Array<{ node: EventCardRow }>;
    pageInfo: { totalElements: number | null; hasNext: boolean | null; endCursor: string | null };
  };
}

const page = (first: number, after: string | null) => ({ first, after, last: null, before: null });

/** Cursor-paged public discovery. `loadMore` appends the next page. */
export function useDiscoverEvents(filter: DiscoverFilter, pageSize = 6, skip = false, sort: DiscoverSort = 'SOONEST') {
  const { data, loading, error, fetchMore, refetch } = useQuery<DiscoverData>(DISCOVER, {
    variables: { filter, pagination: page(pageSize, null), sort },
    skip,
    notifyOnNetworkStatusChange: true,
  });
  const conn = data?.discoverEvents;
  const events = (conn?.edges ?? []).map((e) => e.node);
  return {
    events,
    total: conn?.pageInfo.totalElements ?? events.length,
    hasNext: Boolean(conn?.pageInfo.hasNext),
    loading,
    error,
    refetch,
    loadMore: () =>
      fetchMore({
        variables: { filter, pagination: page(pageSize, conn?.pageInfo.endCursor ?? null), sort },
        updateQuery: (prev, { fetchMoreResult }) =>
          fetchMoreResult
            ? {
                discoverEvents: {
                  ...fetchMoreResult.discoverEvents,
                  edges: [...prev.discoverEvents.edges, ...fetchMoreResult.discoverEvents.edges],
                },
              }
            : prev,
      }),
  };
}

const TRENDING = gql`
  query BuyerTrendingEvents($first: Int) {
    trendingEvents(first: $first) {
      ...EventCardFields
      soldOut
    }
  }
  ${EVENT_CARD_FIELDS}
`;

const RECOMMENDED = gql`
  query BuyerRecommendedEvents($basedOnEventIds: [ID!], $first: Int) {
    recommendedEvents(basedOnEventIds: $basedOnEventIds, first: $first) {
      reason
      basedOnEventId
      event {
        ...EventCardFields
        soldOut
      }
    }
  }
  ${EVENT_CARD_FIELDS}
`;

/** Upcoming published events ranked by tickets sold (public). */
export function useTrendingEvents(first = 9, skip = false) {
  const { data, loading, error, refetch } = useQuery<BuyerTrendingEventsQuery, BuyerTrendingEventsQueryVariables>(TRENDING, { variables: { first }, skip });
  return { events: (data?.trendingEvents ?? []) as EventCardRow[], loading, error, refetch };
}

export type RecommendedEvent = { event: EventCardRow; reason: 'BECAUSE_YOU_BOOKED' | 'TRENDING'; basedOnEventId: string | null };

/**
 * Events in the categories of events the buyer holds tickets for. The caller names those events (their
 * tickets live in booking); the catalog falls back to trending when there is nothing to go on.
 */
export function useRecommendedEvents(basedOnEventIds: string[], first = 4, skip = false) {
  const { data, loading, error, refetch } = useQuery<BuyerRecommendedEventsQuery, BuyerRecommendedEventsQueryVariables>(RECOMMENDED, {
    variables: { basedOnEventIds, first },
    skip,
  });
  return { items: (data?.recommendedEvents ?? []) as unknown as RecommendedEvent[], loading, error, refetch };
}
