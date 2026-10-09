/**
 * Transactions documents: payment attempts (booking-service), ticket search and
 * admin edits, and reservations.
 */
import { gql } from '@apollo/client';
import type { PaymentAttemptStatus } from '../../../../types/graphql';

export const PAYMENT_ATTEMPT_STATUSES: readonly PaymentAttemptStatus[] = [
  'CREATED',
  'PENDING_APPROVAL',
  'PROCESSING',
  'CONFIRMED',
  'COMPLETED',
  'FAILED',
  'REJECTED',
  'EXPIRED',
  'CANCELLED',
];

export const PAYMENT_ATTEMPT_FIELDS = gql`
  fragment TxPaymentAttemptFields on PaymentAttempt {
    id
    depositId
    attemptNumber
    ticketId
    eventId
    buyerId
    amount
    currency
    provider
    payerPhone
    status
    providerStatus
    providerTransactionId
    failureCode
    failureMessage
    webhookProcessed
    retryCount
    lastError
    fulfilled
    reviewStatus
    reviewedBy
    reviewedAt
    reviewNotes
    notes
    riskScore
    riskLevel
    riskFlags
    createdAt
    updatedAt
    expiresAt
  }
`;

/**
 * booking-service has no "all payment attempts" list: `paymentAttempts` needs an
 * intent id. The status index is the only enumerable path, so one document asks
 * for every status under an alias and the hook merges them.
 */
export const PAYMENT_ATTEMPTS_BY_STATUS = gql`
  ${PAYMENT_ATTEMPT_FIELDS}
  query TxPaymentAttempts {
    s0: paymentAttemptsByStatus(status: CREATED) { ...TxPaymentAttemptFields }
    s1: paymentAttemptsByStatus(status: PENDING_APPROVAL) { ...TxPaymentAttemptFields }
    s2: paymentAttemptsByStatus(status: PROCESSING) { ...TxPaymentAttemptFields }
    s3: paymentAttemptsByStatus(status: CONFIRMED) { ...TxPaymentAttemptFields }
    s4: paymentAttemptsByStatus(status: COMPLETED) { ...TxPaymentAttemptFields }
    s5: paymentAttemptsByStatus(status: FAILED) { ...TxPaymentAttemptFields }
    s6: paymentAttemptsByStatus(status: REJECTED) { ...TxPaymentAttemptFields }
    s7: paymentAttemptsByStatus(status: EXPIRED) { ...TxPaymentAttemptFields }
    s8: paymentAttemptsByStatus(status: CANCELLED) { ...TxPaymentAttemptFields }
  }
`;

export const ADD_PAYMENT_ATTEMPT_NOTE = gql`
  ${PAYMENT_ATTEMPT_FIELDS}
  mutation TxAddPaymentAttemptNote($depositId: String!, $note: String!) {
    addPaymentAttemptNote(depositId: $depositId, note: $note) {
      ...TxPaymentAttemptFields
    }
  }
`;

export const SET_PAYMENT_ATTEMPT_REVIEW_STATUS = gql`
  ${PAYMENT_ATTEMPT_FIELDS}
  mutation TxSetPaymentAttemptReviewStatus($depositId: String!, $reviewStatus: String!, $notes: String) {
    setPaymentAttemptReviewStatus(depositId: $depositId, reviewStatus: $reviewStatus, notes: $notes) {
      ...TxPaymentAttemptFields
    }
  }
`;

export const TICKET_FIELDS = gql`
  fragment TxTicketFields on Ticket {
    id
    ticketNumber
    eventId
    eventTitle
    buyerName
    buyerEmail
    buyerPhone
    ticketCategoryCode
    ticketCategoryName
    price
    currency
    status
    qrCode
    purchaseDate
    cancelledAt
    cancellationReason
  }
`;

export const SEARCH_TICKETS = gql`
  ${TICKET_FIELDS}
  query TxSearchTickets($filter: TicketFilterInput!, $pagination: OffsetPaginationInput) {
    searchTickets(filter: $filter, pagination: $pagination) {
      data {
        ...TxTicketFields
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

export const ADMIN_UPDATE_TICKET = gql`
  ${TICKET_FIELDS}
  mutation TxAdminUpdateTicket($ticketId: ID!, $input: AdminTicketUpdateInput!) {
    adminUpdateTicket(ticketId: $ticketId, input: $input) {
      ...TxTicketFields
    }
  }
`;

export const REGENERATE_TICKET_QR = gql`
  ${TICKET_FIELDS}
  mutation TxRegenerateTicketQr($ticketId: ID!) {
    regenerateTicketQrCode(ticketId: $ticketId) {
      ...TxTicketFields
    }
  }
`;

export const BULK_CANCEL_TICKETS = gql`
  mutation TxBulkCancelTickets($ticketIds: [ID!]!, $reason: String!) {
    bulkCancelTickets(ticketIds: $ticketIds, reason: $reason) {
      processedCount
      failedCount
    }
  }
`;

export const RESERVATIONS_BY_EVENT = gql`
  query TxReservationsByEvent($eventId: ID!, $pagination: OffsetPaginationInput) {
    reservationsByEvent(eventId: $eventId, pagination: $pagination) {
      data {
        id
        eventId
        userId
        status
        totalAmount
        currency
        expiresAt
        createdAt
        confirmedAt
        releasedAt
        failedAt
        failureReason
        items {
        tierName
        quantity
        }
      }
      pagination {
        totalCount
        pageSize
        currentPage
        totalPages
        hasNextPage
        hasPreviousPage
      }
    }
  }
`;

export const FORCE_EXPIRE_RESERVATION = gql`
  mutation TxForceExpireReservation($reservationId: ID!) {
    forceExpireReservation(reservationId: $reservationId)
  }
`;

