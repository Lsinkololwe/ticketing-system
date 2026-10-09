/**
 * Check-in GraphQL Operations (Organization Admin App)
 *
 * The ticket-holder roster for one event, the gate validation mutation, and
 * the gate's own attendance figures. All resolved by booking-service.
 *
 * @see backend/booking-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

/**
 * Ticket holders for an event.
 *
 * `buyerName` / `buyerEmail` are `@tag(name: "organizer")` — an organizer may
 * see the holders of their own event's tickets, which is exactly what a gate
 * list is for. The query resolves to nothing for a caller who does not own the
 * event.
 *
 * Paginated because a festival roster is thousands of rows; the gate screen
 * pulls a page at a time rather than the whole list.
 */
export const EVENT_TICKET_HOLDERS = gql`
  query EventTicketHolders($eventId: String!, $pagination: OffsetPaginationInput) {
    ticketsByEvent(eventId: $eventId, pagination: $pagination) {
      data {
        id
        ticketNumber
        buyerName
        buyerEmail
        ticketCategoryName
        status
        purchaseDate
        validatedAt
      }
      pagination {
        totalElements
        totalPages
        hasNext
      }
    }
  }
`;

/**
 * Validate a ticket at the gate.
 *
 * Takes `eventId` as well as the code, because a gate admits to ONE event. A
 * ticket for tomorrow's show is a perfectly valid ticket and must still be
 * refused tonight; the previous signature had no way to know which gate was
 * asking, so it admitted them.
 *
 * `code` is the ticket NUMBER, not the id — that is what a QR carries and what
 * a steward can type.
 *
 * Returns an outcome, not a boolean. "Fake ticket" and "used twenty minutes
 * ago" need different words at a gate, and a caller that only sees a flag shows
 * the same red box for both.
 */
export const VALIDATE_TICKET = gql`
  mutation ValidateTicket($input: ValidateTicketInput!) {
    validateTicket(input: $input) {
      outcome
      admitted
      message
      checkIn {
        id
        ticketNumber
        method
        recordedAt
      }
      conflict {
        id
        type
        originalCheckInAt
      }
      ticket {
        id
        ticketNumber
        buyerName
        buyerEmail
        ticketCategoryName
        status
        purchaseDate
        validatedAt
      }
    }
  }
`;

/**
 * Attendance for one event's gate.
 *
 * `admitted` counts accepted check-ins, which is not the number of people who
 * physically walked in: a duplicate admitted offline by a second device gets no
 * check-in row, so the two figures differ by exactly the duplicate-scan
 * conflicts. The screen shows both rather than folding them together.
 */
export const CHECK_IN_SUMMARY = gql`
  query CheckInSummary($eventId: ID!) {
    checkInSummary(eventId: $eventId) {
      eventId
      issued
      admitted
      conflicts
      openConflicts
      manualAdmissions
      lastCheckInAt
    }
  }
`;

