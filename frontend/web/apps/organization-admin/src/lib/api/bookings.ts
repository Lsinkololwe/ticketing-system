'use client';

/**
 * Bookings, refunds and gate operations (booking-service).
 *
 * The backend has no "booking order" entity: the unit it exposes is the Ticket,
 * with `paymentReference` shared by tickets bought in one payment. The console
 * groups tickets by that reference (see lib/bookings/group.ts). Operation names
 * and shapes follow backend/booking-service/.../schema.graphqls.
 */
import type { OrganizerResendTicketMutation, OrganizerResendTicketMutationVariables, BookingFilterInput, OrganizerBookingQuery, OrganizerBookingQueryVariables, OrganizerBookingsQuery, OrganizerBookingsQueryVariables, OrganizerRefundInboxQuery, OrganizerRefundInboxQueryVariables, RefundRequestFilterInput, OrganizerCalculateRefundQuery, OrganizerCalculateRefundQueryVariables, TicketFilterInput, TicketStatus, EventRefundRequestsQuery, EventRefundRequestsQueryVariables, OrganizerCheckInConflictsQuery, OrganizerCheckInConflictsQueryVariables, OrganizerRecentCheckInsQuery, OrganizerRecentCheckInsQueryVariables, OrganizerTicketsQuery, OrganizerTicketsQueryVariables } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useLazyQuery, useMutation, useQuery } from '@apollo/client/react';

/* ----------------------------------------------------------------- types */

export interface TicketRow {
  id: string;
  ticketNumber: string;
  eventId: string;
  eventTitle: string;
  buyerName: string | null;
  buyerEmail: string | null;
  buyerPhone: string | null;
  ticketCategoryName: string | null;
  price: string;
  currency: string;
  status: string;
  purchaseDate: string | null;
  validatedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  refundedAt: string | null;
  refundReason: string | null;
  paymentReference: string | null;
  netAmount: string | null;
  commissionAmount: string | null;
  paymentInfo: {
    paymentMethod: string | null;
    status: string | null;
    providerReference: string | null;
    paymentDate: string | null;
  } | null;
  refundInfo: { refundAmount: string | null; reason: string | null; status: string | null; refundDate: string | null } | null;
}

export interface TicketPageInfo {
  totalElements: number | null;
  totalPages: number;
  hasNext: boolean | null;
}

export interface RefundRequestRow {
  id: string;
  requestId: string;
  ticketNumber: string;
  eventId: string;
  refundAmount: string;
  currency: string;
  status: string;
  requestType: string;
  reason: string;
  requestedAt: string | null;
  policyApplied: string | null;
}

export interface RefundCalculationRow {
  ticketId: string;
  ticketNumber: string;
  eventDate: string;
  originalAmount: string;
  daysBeforeEvent: number;
  refundPercentage: number;
  refundAmount: string;
  commissionRefund: string;
  platformRetains: string;
  policyApplied: string;
  isEligible: boolean;
  ineligibleReason: string | null;
}

export interface CheckInRow {
  id: string;
  ticketNumber: string | null;
  method: string;
  reason: string | null;
  scannedAt: string | null;
  recordedAt: string;
}

export interface ConflictRow {
  id: string;
  presentedCode: string | null;
  type: string;
  status: string;
  detectedAt: string;
  reviewNote: string | null;
}

/* --------------------------------------------------------------- queries */


export const ORGANIZER_TICKETS = gql`
  query OrganizerTickets($organizerId: String!, $filter: TicketFilterInput, $pagination: OffsetPaginationInput) {
    ticketsByOrganizer(organizerId: $organizerId, filter: $filter, pagination: $pagination) {
      data {
  id ticketNumber eventId eventTitle buyerName buyerEmail buyerPhone
  ticketCategoryName price currency status purchaseDate validatedAt
  cancelledAt cancellationReason refundedAt refundReason paymentReference
  netAmount commissionAmount
  paymentInfo { paymentMethod status providerReference paymentDate }
  refundInfo { refundAmount reason status refundDate }
      }
      pagination { totalElements totalPages hasNext }
    }
  }
`;

export const EVENT_REFUND_REQUESTS = gql`
  query EventRefundRequests($eventId: String!, $pagination: OffsetPaginationInput) {
    refundRequestsByEvent(eventId: $eventId, pagination: $pagination) {
      data {
        id requestId ticketNumber eventId refundAmount currency status requestType
        reason requestedAt policyApplied
      }
      pagination { totalElements totalPages hasNext }
    }
  }
`;

export const CALCULATE_REFUND = gql`
  query OrganizerCalculateRefund($ticketId: String!) {
    calculateRefundAmount(ticketId: $ticketId) {
      ticketId ticketNumber eventDate originalAmount daysBeforeEvent refundPercentage
      refundAmount commissionRefund platformRetains policyApplied isEligible ineligibleReason
    }
  }
`;

export const REFUND_TICKET = gql`
  mutation OrganizerRefundTicket($ticketNumber: String!, $reason: String!, $amount: BigDecimal) {
    refundTicket(ticketNumber: $ticketNumber, reason: $reason, amount: $amount) { id ticketNumber status refundedAt }
  }
`;

export const RESEND_TICKET = gql`
  mutation OrganizerResendTicket($ticketId: ID!) {
    resendTicket(ticketId: $ticketId) { ticketId ticketNumber status channel destination }
  }
`;

/** Every booking made for the organization's events (identity-scoped on the server). */
export const ORGANIZER_BOOKINGS = gql`
  query OrganizerBookings($filter: BookingFilterInput, $pagination: OffsetPaginationInput) {
    bookingsByOrganizer(filter: $filter, pagination: $pagination) {
      data {
        id bookingNumber eventId eventTitle contactName contactEmail contactPhone status ticketCount
        subtotal discountAmount totalAmount currency promoCode refundedAmount refundableAmount createdAt confirmedAt
        payment { provider status reference amount currency payerPhone paidAt }
        tickets {
          id ticketNumber eventId eventTitle buyerName buyerEmail buyerPhone ticketCategoryName price currency status
          purchaseDate validatedAt cancelledAt cancellationReason refundedAt refundReason paymentReference netAmount commissionAmount
          paymentInfo { paymentMethod status providerReference paymentDate }
          refundInfo { refundAmount reason status refundDate }
        }
      }
      pagination { totalElements totalPages hasNext }
    }
  }
`;

export const ORGANIZER_BOOKING = gql`
  query OrganizerBooking($id: ID!) {
    booking(id: $id) {
      id bookingNumber eventId eventTitle contactName contactEmail contactPhone status ticketCount
      subtotal discountAmount totalAmount currency promoCode refundedAmount refundableAmount createdAt confirmedAt
      items { ticketTierId tierName quantity unitPrice subtotal }
      payment { provider status reference amount currency payerPhone paidAt }
      refundRequests { id requestId ticketNumber refundAmount currency status reason requestedAt }
      tickets {
        id ticketNumber eventId eventTitle buyerName buyerEmail buyerPhone ticketCategoryName price currency status
        purchaseDate validatedAt cancelledAt cancellationReason refundedAt refundReason paymentReference netAmount commissionAmount
        paymentInfo { paymentMethod status providerReference paymentDate }
        refundInfo { refundAmount reason status refundDate }
      }
    }
  }
`;

/** Refund requests across every event of the organization (no event picker). */
export const REFUND_INBOX = gql`
  query OrganizerRefundInbox($filter: RefundRequestFilterInput, $pagination: OffsetPaginationInput) {
    refundRequestsByOrganizer(filter: $filter, pagination: $pagination) {
      data { id requestId ticketNumber eventId refundAmount currency status requestType reason requestedAt policyApplied }
      pagination { totalElements totalPages hasNext }
    }
  }
`;

export const CANCEL_TICKET = gql`
  mutation OrganizerCancelTicket($ticketNumber: String!, $reason: String!) {
    cancelTicket(ticketNumber: $ticketNumber, reason: $reason) { id ticketNumber status cancelledAt }
  }
`;

export const RECENT_CHECK_INS = gql`
  query OrganizerRecentCheckIns($eventId: ID!, $limit: Int) {
    recentCheckIns(eventId: $eventId, limit: $limit) { id ticketNumber method reason scannedAt recordedAt }
  }
`;

export const CHECK_IN_CONFLICTS = gql`
  query OrganizerCheckInConflicts($eventId: ID!, $pagination: OffsetPaginationInput) {
    checkInConflicts(eventId: $eventId, pagination: $pagination) {
      content { id presentedCode type status detectedAt reviewNote }
      totalElements
    }
  }
`;

export const REVIEW_CONFLICT = gql`
  mutation OrganizerReviewConflict($id: ID!, $note: String!) {
    reviewConflict(id: $id, note: $note) { id status reviewNote }
  }
`;

/* ----------------------------------------------------------------- hooks */

export interface TicketFilter {
  eventId?: string;
  status?: string;
  searchQuery?: string;
}

export function useOrganizerTickets(
  organizerId: string | null | undefined,
  filter: TicketFilter,
  page: number,
  size: number
) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerTicketsQuery, OrganizerTicketsQueryVariables>(ORGANIZER_TICKETS, {
    variables: {
      organizerId: organizerId ?? '',
      filter: {
        eventId: filter.eventId || null,
        status: (filter.status || null) as TicketStatus | null,
        searchQuery: filter.searchQuery || null,
      } as TicketFilterInput,
      pagination: { page, size, sortBy: 'purchaseDate', sortDirection: 'DESC' },
    },
    skip: !organizerId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const result = data?.ticketsByOrganizer;
  return {
    tickets: result?.data ?? [],
    total: result?.pagination.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

export function useEventRefundRequests(eventId: string | null | undefined, page = 0, size = 20) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<EventRefundRequestsQuery, EventRefundRequestsQueryVariables>(EVENT_REFUND_REQUESTS, {
    variables: { eventId: eventId ?? '', pagination: { page, size, sortBy: 'requestedAt', sortDirection: 'DESC' } },
    skip: !eventId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return {
    requests: data?.refundRequestsByEvent.data ?? [],
    total: data?.refundRequestsByEvent.pagination.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

export function useRefundQuote() {
  const [run, { data, loading, error }] = useLazyQuery<OrganizerCalculateRefundQuery, OrganizerCalculateRefundQueryVariables>(CALCULATE_REFUND, {
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });
  return {
    quote: data?.calculateRefundAmount ?? null,
    loading,
    error,
    load: (ticketId: string) => run({ variables: { ticketId } }),
  };
}

export function useRefundTicket() {
  const [mutate, state] = useMutation(REFUND_TICKET, { refetchQueries: [ORGANIZER_TICKETS, ORGANIZER_BOOKINGS, ORGANIZER_BOOKING] });
  return {
    /** `amount` (kwacha) refunds part of the ticket; omit it for the policy amount. */
    refund: (ticketNumber: string, reason: string, amount?: string | null) => mutate({ variables: { ticketNumber, reason, amount: amount ?? null } }),
    ...state,
  };
}

export function useResendTicket() {
  const [mutate, state] = useMutation<OrganizerResendTicketMutation, OrganizerResendTicketMutationVariables>(RESEND_TICKET);
  return { resend: (ticketId: string) => mutate({ variables: { ticketId } }), ...state };
}

export type BookingVM = OrganizerBookingsQuery['bookingsByOrganizer']['data'][number];
export type BookingDetailVM = NonNullable<OrganizerBookingQuery['booking']>;
export type RefundInboxRow = OrganizerRefundInboxQuery['refundRequestsByOrganizer']['data'][number];

export function useOrganizerBookings(filter: BookingFilterInput, page: number, size: number) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerBookingsQuery, OrganizerBookingsQueryVariables>(ORGANIZER_BOOKINGS, {
    variables: { filter, pagination: { page, size, sortBy: 'createdAt', sortDirection: 'DESC' } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const r = data?.bookingsByOrganizer;
  return { bookings: r?.data ?? [], total: r?.pagination.totalElements ?? 0, loading, error, refetch };
}

export function useOrganizerBooking(id: string) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerBookingQuery, OrganizerBookingQueryVariables>(ORGANIZER_BOOKING, {
    variables: { id },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { booking: data?.booking ?? null, loading, error, refetch };
}

export function useRefundInbox(filter: RefundRequestFilterInput, page = 0, size = 20) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerRefundInboxQuery, OrganizerRefundInboxQueryVariables>(REFUND_INBOX, {
    variables: { filter, pagination: { page, size, sortBy: 'requestedAt', sortDirection: 'DESC' } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const r = data?.refundRequestsByOrganizer;
  return { requests: r?.data ?? [], total: r?.pagination.totalElements ?? 0, loading, error, refetch };
}

export function useCancelTicket() {
  const [mutate, state] = useMutation(CANCEL_TICKET, { refetchQueries: [ORGANIZER_TICKETS] });
  return { cancel: (ticketNumber: string, reason: string) => mutate({ variables: { ticketNumber, reason } }), ...state };
}

export function useRecentCheckIns(eventId: string | null | undefined, limit = 25) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerRecentCheckInsQuery, OrganizerRecentCheckInsQueryVariables>(RECENT_CHECK_INS, {
    variables: { eventId: eventId ?? '', limit },
    skip: !eventId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    pollInterval: 15_000,
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { checkIns: data?.recentCheckIns ?? [], loading, error, refetch };
}

export function useCheckInConflicts(eventId: string | null | undefined) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerCheckInConflictsQuery, OrganizerCheckInConflictsQueryVariables>(CHECK_IN_CONFLICTS, {
    variables: { eventId: eventId ?? '', pagination: { page: 0, size: 50, sortBy: 'detectedAt', sortDirection: 'DESC' } },
    skip: !eventId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { conflicts: data?.checkInConflicts.content ?? [], loading, error, refetch };
}

export function useReviewConflict() {
  const [mutate, state] = useMutation(REVIEW_CONFLICT, { refetchQueries: [CHECK_IN_CONFLICTS] });
  return { review: (id: string, note: string) => mutate({ variables: { id, note } }), ...state };
}
