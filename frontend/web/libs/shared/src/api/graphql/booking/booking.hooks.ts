/**
 * Booking — Consumer React hooks.
 *
 * Components consume these hooks, never Apollo directly (repo rule).
 * All result shapes are typed from the codegen'd schema types.
 */

'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import type {
  Ticket,
  TicketEdge,
  TicketStatus,
  TicketReservation,
  ReserveTicketsInput,
  CompleteReservationInput,
  CursorPaginationInput,
} from '../../../types/graphql';
import {
  RESERVE_TICKETS,
  COMPLETE_RESERVATION,
  GET_RESERVATION,
  GET_MY_TICKETS,
} from './booking.consumer.definitions';

/** Hold seats for an event (opens the payment window). */
export function useReserveTickets() {
  const [mutate, { data, loading, error, reset }] = useMutation<
    { reserveTickets: TicketReservation },
    { input: ReserveTicketsInput }
  >(RESERVE_TICKETS);

  const reserveTickets = (input: ReserveTicketsInput) => mutate({ variables: { input } });
  return { reserveTickets, reservation: data?.reserveTickets ?? null, loading, error, reset };
}

/** Complete a reservation — triggers the mobile-money push and mints tickets. */
export function useCompleteReservation() {
  const [mutate, { data, loading, error, reset }] = useMutation<
    { completeReservation: Ticket[] },
    { input: CompleteReservationInput }
  >(COMPLETE_RESERVATION);

  const completeReservation = (input: CompleteReservationInput) => mutate({ variables: { input } });
  return { completeReservation, tickets: data?.completeReservation ?? null, loading, error, reset };
}

/**
 * Poll a reservation while payment is pending.
 * Pass `pollMs` to enable polling; polling stops automatically when skipped.
 */
export function useReservation(id: string | null | undefined, pollMs = 0) {
  const { data, loading, error, startPolling, stopPolling } = useQuery<
    { reservation: TicketReservation | null },
    { id: string }
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

/** The signed-in buyer's tickets (My Tickets page). */
export function useMyTickets({ buyerId, status, size = 50, skip }: MyTicketsOptions) {
  const { data, loading, error, refetch } = useQuery<
    {
      ticketsByBuyerCursorPagination: {
        edges: TicketEdge[];
        totalCount: number | null;
      };
    },
    { buyerId: string; status?: TicketStatus; pagination: CursorPaginationInput }
  >(GET_MY_TICKETS, {
    variables: {
      buyerId: buyerId ?? '',
      status,
      pagination: { first: size, after: null, last: null, before: null },
    },
    skip: skip || !buyerId,
    fetchPolicy: 'cache-and-network',
  });

  const connection = data?.ticketsByBuyerCursorPagination;
  const tickets = (connection?.edges ?? []).map((edge) => edge.node) as Ticket[];
  return {
    tickets,
    totalCount: connection?.totalCount ?? tickets.length,
    loading,
    error,
    refetch,
  };
}
