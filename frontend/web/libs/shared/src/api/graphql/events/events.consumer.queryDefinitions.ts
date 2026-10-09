/**
 * Events — Consumer GraphQL query definitions (customer ticketing app).
 *
 * Hand-authored `gql` documents against the federated supergraph. Operation
 * result/variable typing is provided by the codegen'd schema types in
 * `libs/shared/src/types/graphql` — do NOT redefine those types here.
 *
 * IMPORTANT: customer-facing browsing MUST use PUBLIC queries only. Events come from
 * `discoverEvents` (cursor-paged) and categories from the bounded `categories` list;
 * both are open to anonymous visitors.
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

/** Published events for public browsing: the unfiltered discovery feed, cursor-paged. */
export const GET_PUBLISHED_EVENTS = gql`
  query GetPublishedEvents($pagination: CursorPaginationInput) {
    discoverEvents(filter: {}, pagination: $pagination) {
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

/** Active event categories for filtering: a bounded public list, returned whole. */
export const GET_ACTIVE_EVENT_CATEGORIES = gql`
  query GetActiveEventCategories {
    categories {
      id
      name
      code
      eventCount
      imageUrl
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
