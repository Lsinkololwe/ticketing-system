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
