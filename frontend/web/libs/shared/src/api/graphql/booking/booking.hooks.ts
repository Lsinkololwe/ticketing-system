/**
 * Booking — Consumer React hooks.
 *
 * Components consume these hooks, never Apollo directly (repo rule).
 * All result shapes are typed from the codegen'd schema types.
 */

'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import type {
  TicketStatus,
  ReserveTicketsInput,
  PayReservationInput,
  ReserveTicketsMutation,
  ReserveTicketsMutationVariables,
  PayReservationMutation,
  PayReservationMutationVariables,
  GetReservationQuery,
  GetReservationQueryVariables,
  GetMyTicketsQuery,
  GetMyTicketsQueryVariables,
} from '../../../types/graphql';
import {
  RESERVE_TICKETS,
  PAY_RESERVATION,
  GET_RESERVATION,
  GET_MY_TICKETS,
} from './booking.consumer.definitions';

/** Hold seats for an event (opens the payment window). */
export function useReserveTickets() {
  const [mutate, { data, loading, error, reset }] = useMutation<
    ReserveTicketsMutation,
    ReserveTicketsMutationVariables
  >(RESERVE_TICKETS);

  const reserveTickets = (input: ReserveTicketsInput) => mutate({ variables: { input } });
  return { reserveTickets, reservation: data?.reserveTickets ?? null, loading, error, reset };
}

/**
 * Ask for the mobile-money prompt on a held reservation.
 *
 * Returns the initiation outcome, never tickets. `success` here means the
 * provider accepted the request and the buyer's handset should be ringing — not
 * that money moved. Watch the reservation with `useReservation` for the real
 * outcome.
 */
export function usePayReservation() {
  const [mutate, { data, loading, error, reset }] = useMutation<
    PayReservationMutation,
    PayReservationMutationVariables
  >(PAY_RESERVATION);

  const payReservation = (input: PayReservationInput) => mutate({ variables: { input } });
  return { payReservation, initiation: data?.payReservation ?? null, loading, error, reset };
}

/**
 * Poll a reservation while payment is pending.
 * Pass `pollMs` to enable polling; polling stops automatically when skipped.
 */
export function useReservation(id: string | null | undefined, pollMs = 0) {
  const { data, loading, error, startPolling, stopPolling } = useQuery<
    GetReservationQuery,
    GetReservationQueryVariables
  >(GET_RESERVATION, {
    variables: { id: id ?? '' },
    skip: !id,
    pollInterval: pollMs || undefined,
    fetchPolicy: 'network-only',
  });

  return { reservation: data?.reservation ?? null, loading, error, startPolling, stopPolling };
}

export interface MyTicketsOptions {
  buyerId: string | undefined;
  status?: TicketStatus;
  size?: number;
  skip?: boolean;
}

/** One row of the buyer's ticket list — the `GetMyTickets` selection. */
export type MyTicketRow =
  GetMyTicketsQuery['ticketsByBuyerCursorPagination']['edges'][number]['node'];

/** The signed-in buyer's tickets (My Tickets page). */
export function useMyTickets({ buyerId, status, size = 50, skip }: MyTicketsOptions) {
  const { data, loading, error, refetch } = useQuery<GetMyTicketsQuery, GetMyTicketsQueryVariables>(
    GET_MY_TICKETS,
    {
      variables: {
        buyerId: buyerId ?? '',
        status: status ?? null,
        pagination: { first: size, after: null, last: null, before: null },
      },
      skip: skip || !buyerId,
      fetchPolicy: 'cache-and-network',
    }
  );

  const connection = data?.ticketsByBuyerCursorPagination;
  const tickets = (connection?.edges ?? []).map((edge) => edge.node);
  return {
    tickets,
    totalCount: connection?.totalCount ?? tickets.length,
    loading,
    error,
    refetch,
  };
}
