/**
 * Organizer Events GraphQL (Organization Admin App)
 *
 * The organizer's own events list plus publish/unpublish. organizerId is taken
 * from the JWT server-side, so no id argument is needed on the list query.
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

/**
 * The organizer's own events (offset paginated). Revenue/soldTickets are
 * federated from booking-service.
 */
export const MY_EVENTS = gql`
  query MyEvents($pagination: OffsetPaginationInput) {
    myEventsOffsetPagination(pagination: $pagination) {
      content {
        id
        title
        status
        eventDateTime
        endDateTime
        locationName
        cityName
        bannerImageUrl
        totalCapacity
        soldTickets
        revenue
        currency
      }
      totalElements
      totalPages
      hasNext
    }
  }
`;

/** Publish an event (organizer). Approved events only — enforced server-side. */
export const PUBLISH_EVENT = gql`
  mutation PublishEvent($id: ID!) {
    publishEvent(id: $id) {
      success
      message
      errors
      data {
        id
        status
      }
    }
  }
`;

/** Unpublish an event (organizer). */
export const UNPUBLISH_EVENT = gql`
  mutation UnpublishEvent($id: ID!) {
    unpublishEvent(id: $id) {
      success
      message
      errors
      data {
        id
        status
      }
    }
  }
`;

/**
 * A single event, for the organizer's event-detail screen.
 *
 * Selects the organizer-tagged inventory fields (`quantity`, `soldQuantity`)
 * alongside the public ones, so the tier table can show sold-against-allocated
 * rather than just what is still on sale. Revenue is federated in from
 * booking-service.
 *
 * Authorisation is enforced server-side: `event(id:)` is public, but the
 * organizer-tagged fields resolve to null for a caller who does not own it.
 */
export const MY_EVENT_DETAIL = gql`
  query MyEventDetail($id: ID!) {
    event(id: $id) {
      id
      title
      description
      status
      eventDateTime
      endDateTime
      locationName
      locationAddress
      cityName
      bannerImageUrl
      totalCapacity
      soldTickets
      availableTickets
      revenue
      currency
      rejectionReason
      ticketTiers {
        id
        name
        price
        currency
        quantity
        soldQuantity
        isActive
      }
    }
  }
`;

/**
 * Create an event.
 *
 * The event is created as a DRAFT — publishing is a separate, explicitly
 * authorised step (`publishEvent`), and on this platform it requires the event
 * to have been approved first. A create that silently published would bypass
 * that review.
 */
export const CREATE_EVENT = gql`
  mutation CreateEvent($input: CreateEventInput!) {
    createEvent(input: $input) {
      success
      message
      errors
      data {
        id
        title
        status
      }
    }
  }
`;
