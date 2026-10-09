/**
 * Admin event queries — `Admin - Events.dc.html`.
 *
 * <h2>Pagination shapes differ between catalog and booking</h2>
 * Three conventions live in this schema and none of them are interchangeable:
 * catalog's offset pages are FLAT (`content`, `pageNumber`, `totalElements`,
 * `hasNext`), booking's are nested (`data` + `pagination { totalCount }`), and
 * identity's are nested under `pageInfo`. Selecting the wrong shape is a 400
 * with an empty body, which renders as a blank panel rather than an error. The
 * hooks normalise so callers do not have to care which service owns a table.
 *
 * <h2>Locations are cursor-only</h2>
 * There is no `locationsOffsetPagination` — only `locations`.
 * So the locations view pages by cursor while the other two page by offset.
 *
 * @see backend/catalog-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

export const EVENT_LIST_FIELDS = gql`
  fragment EventListFields on Event {
    id
    title
    status
    published
    eventDateTime
    endDateTime
    organizerName
    organizerId
    locationName
    cityName
    totalCapacity
    soldTickets
    availableTickets
    minTicketPrice
    currency
    submittedForApprovalAt
    approvalDeadline
    approvedAt
    approvedBy
    rejectedAt
    rejectionReason
    isOverdue
    category {
      id
      name
    }
  }
`;

/** The admin events table. `filter.status` narrows it; null means every status. */
export const ADMIN_EVENTS = gql`
  ${EVENT_LIST_FIELDS}
  query AdminEvents($filter: EventFilterInput, $pagination: OffsetPaginationInput) {
    events(filter: $filter, pagination: $pagination) {
      content {
        ...EventListFields
      }
      pageNumber
      pageSize
      totalElements
      totalPages
      hasNext
      hasPrevious
    }
  }
`;

export const EVENT_STATS = gql`
  query EventStats {
    eventStats {
      totalEvents
      publishedEvents
      draftEvents
      pendingApprovalEvents
      approvedNotPublishedEvents
      cancelledEvents
      completedEvents
      rejectedEvents
      totalCapacity
      totalSoldTickets
    }
  }
`;

/**
 * Every category, active or not, for the management screens. The rows are `EVENT_CATEGORY` reference data, the
 * store the public `categories` list reads. That list also carries the event count, but only for active categories.
 */
export const ADMIN_EVENT_CATEGORIES = gql`
  query AdminEventCategories($pagination: OffsetPaginationInput) {
    referenceDataAll(type: EVENT_CATEGORY, pagination: $pagination) {
      content {
        id
        code
        name
        description
        isActive
      }
      pageNumber
      pageSize
      totalElements
      totalPages
      hasNext
      hasPrevious
    }
    categories {
      code
      eventCount
    }
  }
`;

/** Cursor-paged: catalog exposes no offset variant for locations. */
export const ADMIN_LOCATIONS = gql`
  query AdminLocations($pagination: CursorPaginationInput) {
    locations(pagination: $pagination) {
      edges {
        node {
          id
          name
          address
          city
          province
          country
          postalCode
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        totalCount
      }
    }
  }
`;

// =============================================================================
// APPROVAL DECISIONS
// =============================================================================

/**
 * The three decisions the design's pending rows offer.
 *
 * `rejectEvent` and `requestEventChanges` both declare `comments: String!` —
 * the organizer is shown that text, so the UI must not send a placeholder to
 * satisfy the non-null.
 */
export const APPROVE_EVENT = gql`
  mutation ApproveEvent($eventId: ID!, $comments: String) {
    approveEvent(eventId: $eventId, comments: $comments) {
      id
      status
    }
  }
`;

export const REJECT_EVENT = gql`
  mutation RejectEvent($eventId: ID!, $comments: String!) {
    rejectEvent(eventId: $eventId, comments: $comments) {
      id
      status
    }
  }
`;

export const REQUEST_EVENT_CHANGES = gql`
  mutation RequestEventChanges($eventId: ID!, $comments: String!) {
    requestEventChanges(eventId: $eventId, comments: $comments) {
      id
      status
    }
  }
`;
