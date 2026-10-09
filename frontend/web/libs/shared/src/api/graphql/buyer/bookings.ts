'use client';

import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';
import { TICKET_FIELDS } from '../booking/booking.consumer.definitions';
import type {
  BuyerMyBookingsQuery,
  BuyerMyBookingsQueryVariables,
  BuyerResendTicketMutation,
  BuyerResendTicketMutationVariables,
  BuyerCancelRefundRequestMutation,
  BuyerCancelRefundRequestMutationVariables,
} from '../../../types/graphql';

export type BuyerBookingRow = BuyerMyBookingsQuery['myBookings']['data'][number];
export type BuyerBookingTicket = BuyerBookingRow['tickets'][number];

const MY_BOOKINGS = gql`
  query BuyerMyBookings($filter: BookingFilterInput, $pagination: OffsetPaginationInput) {
    myBookings(filter: $filter, pagination: $pagination) {
      data {
        id
        bookingNumber
        reservationId
        eventId
        eventTitle
        eventDate
        status
        ticketCount
        totalAmount
        currency
        refundedAmount
        lateRefundStatus
        contactName
        contactEmail
        contactPhone
        createdAt
        confirmedAt
        items {
          ticketTierId
          tierName
          quantity
        }
        tickets {
          ...TicketFields
          bookingNumber
          transferPending
          transferCount
        }
      }
      pagination {
        totalElements
        hasNext
      }
    }
  }
  ${TICKET_FIELDS}
`;

const RESEND = gql`
  mutation BuyerResendTicket($ticketId: ID!) {
    resendTicket(ticketId: $ticketId) {
      ticketId
      ticketNumber
      status
      channel
      destination
    }
  }
`;

const CANCEL_REFUND = gql`
  mutation BuyerCancelRefundRequest($refundRequestId: ID!, $reason: String!) {
    cancelRefundRequest(refundRequestId: $refundRequestId, reason: $reason) {
      id
      status
    }
  }
`;

/** The signed-in buyer's bookings, newest first, each with its tickets. */
export function useMyBookings(size = 50, skip = false) {
  const { data, loading, error, refetch } = useQuery<BuyerMyBookingsQuery, BuyerMyBookingsQueryVariables>(MY_BOOKINGS, {
    variables: { filter: null, pagination: { page: 0, size, sortBy: null, sortDirection: null } },
    skip,
    fetchPolicy: 'cache-and-network',
  });
  return { bookings: data?.myBookings.data ?? [], total: data?.myBookings.pagination.totalElements ?? 0, loading, error, refetch };
}

/** What a re-send answered: QUEUED, DUPLICATE (just sent) or NO_VERIFIED_CONTACT. */
export type ResendOutcome = 'QUEUED' | 'DUPLICATE' | 'NO_VERIFIED_CONTACT';

export function useResendTicket() {
  const [run, { loading }] = useMutation<BuyerResendTicketMutation, BuyerResendTicketMutationVariables>(RESEND);
  return {
    loading,
    resend: async (ticketId: string) => {
      const res = await run({ variables: { ticketId } });
      const r = res.data?.resendTicket;
      return { status: (r?.status ?? 'QUEUED') as ResendOutcome, channel: r?.channel ?? null, destination: r?.destination ?? null };
    },
  };
}

/** Withdraws the buyer's own refund request while it is still waiting for review. */
export function useCancelRefundRequest() {
  const [run, { loading }] = useMutation<BuyerCancelRefundRequestMutation, BuyerCancelRefundRequestMutationVariables>(CANCEL_REFUND, {
    refetchQueries: ['MyRefundRequests', 'BuyerMyBookings'],
  });
  return { loading, cancel: (refundRequestId: string, reason: string) => run({ variables: { refundRequestId, reason } }) };
}

/**
 * The booking a reservation became, once it exists: CONFIRMED with its tickets, or
 * PAID_AFTER_EXPIRY_AUTO_REFUNDED when the payment arrived after the hold lapsed.
 */
export function useBookingForReservation(reservationId: string | null, enabled: boolean) {
  const { data, loading, error, refetch } = useQuery<BuyerMyBookingsQuery, BuyerMyBookingsQueryVariables>(MY_BOOKINGS, {
    variables: { filter: null, pagination: { page: 0, size: 10, sortBy: null, sortDirection: null } },
    skip: !enabled || !reservationId,
    fetchPolicy: 'network-only',
  });
  const booking = data?.myBookings.data.find((b) => b.reservationId === reservationId) ?? null;
  return { booking, loading, error, refetch };
}
