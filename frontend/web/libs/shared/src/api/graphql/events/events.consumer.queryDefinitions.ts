/**
 * Events — Consumer GraphQL query definitions (customer ticketing app).
 *
 * Hand-authored `gql` documents against the federated supergraph. Operation
 * result/variable typing is provided by the codegen'd schema types in
 * `libs/shared/src/types/graphql` — do NOT redefine those types here.
 *
 * IMPORTANT: customer-facing browsing MUST use the PUBLIC *cursor* queries.
 * The offset-pagination variants (`publishedEventsOffsetPagination`, etc.) are
 * gated `@auth(requires: ORGANIZER/ADMIN)` on the backend and return FORBIDDEN
 * for anonymous/CUSTOMER visitors — so the public cursor queries are used here.
 *
 * @see backend catalog-service schema.graphqls
 * @see libs/shared/src/types/graphql/index.ts (run `npm run codegen` to refresh)
 */

import { gql } from '@apollo/client';

// ==========================================
// Fragments
// ==========================================

/** Fields needed to render an event card in listings. */
export const EVENT_CARD_FIELDS = gql`
  fragment EventCardFields on Event {
    id
    title
    description
    status
    featured
    eventDateTime
    endDateTime
    cityName
    locationName
    bannerImageUrl
    galleryImages
    organizerName
    soldTickets
    totalCapacity
    availableTickets
    minTicketPrice
    maxTicketPrice
    currency
    category {
      id
      name
    }
  }
`;

/** Full detail fields for a single event, including its ticket tiers. */
export const EVENT_DETAIL_FIELDS = gql`
  fragment EventDetailFields on Event {
    ...EventCardFields
    ticketTiers {
      id
      name
      code
      description
      price
      originalPrice
      earlyBirdPrice
      earlyBirdEndsAt
      salesStartAt
      salesEndAt
      currency
      quantity
      soldQuantity
      availableQuantity
      minPerOrder
      maxPerOrder
      benefits
      isActive
      isHidden
      sortOrder
    }
  }
  ${EVENT_CARD_FIELDS}
`;

// ==========================================
// Queries (public, cursor pagination)
// ==========================================

/** Published events for public browsing (public cursor pagination). */
export const GET_PUBLISHED_EVENTS = gql`
  query GetPublishedEvents($pagination: CursorPaginationInput) {
    publishedEventsCursorPagination(pagination: $pagination) {
      edges {
        node {
          ...EventCardFields
        }
      }
      pageInfo {
        totalElements
        totalPages
        currentPage
        pageSize
        hasNext
        hasPrevious
        endCursor
      }
    }
  }
  ${EVENT_CARD_FIELDS}
`;

/** Upcoming published events (public cursor pagination). */
export const GET_UPCOMING_EVENTS = gql`
  query GetUpcomingEvents($pagination: CursorPaginationInput) {
    upcomingEventsCursorPagination(pagination: $pagination) {
      edges {
        node {
          ...EventCardFields
        }
      }
      pageInfo {
        totalElements
        totalPages
        currentPage
        pageSize
        hasNext
        hasPrevious
        endCursor
      }
    }
  }
  ${EVENT_CARD_FIELDS}
`;

/** A single event by id, with ticket tiers (detail + booking pages). */
export const GET_EVENT_BY_ID = gql`
  query GetEventById($id: ID!) {
    event(id: $id) {
      ...EventDetailFields
    }
  }
  ${EVENT_DETAIL_FIELDS}
`;

/** Active event categories for filtering (public cursor pagination). */
export const GET_ACTIVE_EVENT_CATEGORIES = gql`
  query GetActiveEventCategories($pagination: CursorPaginationInput) {
    activeEventCategoriesCursorPagination(pagination: $pagination) {
      edges {
        node {
          id
          name
          code
          eventCount
        }
      }
      pageInfo {
        totalElements
      }
    }
  }
`;

/** Cities that currently have events (for the city filter — public). */
export const GET_CITIES_WITH_EVENTS = gql`
  query GetCitiesWithEvents {
    citiesWithEvents {
      id
      name
      province
    }
  }
`;
