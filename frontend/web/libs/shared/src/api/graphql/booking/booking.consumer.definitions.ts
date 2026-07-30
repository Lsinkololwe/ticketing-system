/**
 * Booking — Consumer GraphQL operation definitions (customer ticketing app).
 *
 * The customer checkout is a reservation-based flow (matches the backend's
 * mobile-money model):
 *   1. `reserveTickets`     — hold seats, start a 10-min countdown
 *   2. `completeReservation`— trigger the mobile-money STK push, mint tickets
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

/** Complete a reservation — triggers the mobile-money push and mints tickets. */
export const COMPLETE_RESERVATION = gql`
  mutation CompleteReservation($input: CompleteReservationInput!) {
    completeReservation(input: $input) {
      ...TicketFields
    }
  }
  ${TICKET_FIELDS}
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
