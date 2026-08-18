/**
 * Booking — Consumer GraphQL operation definitions (customer ticketing app).
 *
 * The customer checkout is a reservation-based flow (matches the backend's
 * mobile-money model):
 *   1. `reserveTickets`     — hold seats, start a 10-min countdown
 *   2. `payReservation`     — trigger the mobile-money prompt (tickets come later)
 *   3. poll `reservation`   — watch status / remainingSeconds while paying
 *
 * These operations are `@tag(name: "mobile")` and (for purchase) `@auth`'d to
 * CUSTOMER on the backend. Result/variable typing comes from the codegen'd
 * schema types — do NOT redefine those types here.
 *
 * @see backend booking-service schema.graphqls
 */

import { gql } from '@apollo/client';

/** Public ticket fields shown to the ticket holder (My Tickets + confirmation). */
export const TICKET_FIELDS = gql`
  fragment TicketFields on Ticket {
    id
    ticketNumber
    eventId
    eventTitle
    eventDate
    eventLocationName
    ticketCategoryName
    price
    currency
    status
    qrCode
    barcode
    purchaseDate
    validUntil
  }
`;

/** Reservation fields for the checkout countdown + order summary. */
export const RESERVATION_FIELDS = gql`
  fragment ReservationFields on TicketReservation {
    id
    eventId
    totalAmount
    currency
    status
    expiresAt
    remainingSeconds
    discountAmount
    promoCodeApplied
    paymentIntentId
    confirmedAt
    releasedAt
    failedAt
    failureReason
    items {
      ticketTierId
      tierName
      quantity
      unitPrice
      subtotal
    }
  }
`;

/** Hold seats for an event and open the payment window. */
export const RESERVE_TICKETS = gql`
  mutation ReserveTickets($input: ReserveTicketsInput!) {
    reserveTickets(input: $input) {
      ...ReservationFields
    }
  }
  ${RESERVATION_FIELDS}
`;

/**
 * Ask for the mobile-money prompt on a held reservation.
 *
 * This does NOT return tickets, and the absence is the point. Confirmation
 * follows the provider's callback, never a client asserting that it paid
 * (ET-TKT-001 §4), and mobile-money confirmation takes between eight seconds
 * and four minutes. Poll `GET_RESERVATION` until its status reads CONFIRMED.
 */
export const PAY_RESERVATION = gql`
  mutation PayReservation($input: PayReservationInput!) {
    payReservation(input: $input) {
      success
      message
      paymentIntentId
      transactionRef
      paymentStatus
      reservationId
      errors
    }
  }
`;

/** Poll a reservation's status / remaining time while payment is pending. */
export const GET_RESERVATION = gql`
  query GetReservation($id: ID!) {
    reservation(id: $id) {
      ...ReservationFields
    }
  }
  ${RESERVATION_FIELDS}
`;

/** The signed-in buyer's tickets (My Tickets page). */
export const GET_MY_TICKETS = gql`
  query GetMyTickets($buyerId: String!, $status: TicketStatus, $pagination: CursorPaginationInput) {
    ticketsByBuyerCursorPagination(buyerId: $buyerId, status: $status, pagination: $pagination) {
      edges {
        node {
          ...TicketFields
        }
      }
      pageInfo {
        totalElements
        hasNext
        endCursor
      }
      totalCount
    }
  }
  ${TICKET_FIELDS}
`;
