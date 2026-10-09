'use client';

import type { OffsetPageInfo } from '../../../../types/pageInfo';
import type { TransactionReviewStatus, TxBulkCancelTicketsMutation, TxBulkCancelTicketsMutationVariables, TxReservationsByEventQuery, TxReservationsByEventQueryVariables, TxSearchTicketsQuery, TxSearchTicketsQueryVariables } from '../../../../types/graphql';
import { useCallback, useMemo } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  ADD_PAYMENT_ATTEMPT_NOTE,
  ADMIN_UPDATE_TICKET,
  BULK_CANCEL_TICKETS,
  FORCE_EXPIRE_RESERVATION,
  PAYMENT_ATTEMPTS_BY_STATUS,
  REGENERATE_TICKET_QR,
  RESERVATIONS_BY_EVENT,
  SEARCH_TICKETS,
  SET_PAYMENT_ATTEMPT_REVIEW_STATUS,
} from './transactions.queries';

export type PaymentAttemptStatus =
  | 'CREATED'
  | 'PENDING_APPROVAL'
  | 'PROCESSING'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELLED';

export type TxPageInfo = OffsetPageInfo;

function pageInfo(p: { totalCount?: number | null; pageSize?: number | null; currentPage?: number | null; totalPages?: number | null; hasNextPage?: boolean | null; hasPreviousPage?: boolean | null } | null | undefined, size: number): TxPageInfo {
  return {
    totalCount: p?.totalCount ?? 0,
    pageSize: p?.pageSize ?? size,
    currentPage: p?.currentPage ?? 0,
    totalPages: p?.totalPages ?? 0,
    hasNextPage: p?.hasNextPage ?? false,
    hasPreviousPage: p?.hasPreviousPage ?? false,
  };
}

/* ---------------------------------------------------------------- payments */

export interface TxPaymentAttempt {
  id: string;
  depositId: string;
  attemptNumber: string;
  ticketId: string;
  eventId: string | null;
  buyerId: string;
  amount: number | string;
  currency: string;
  provider: string;
  payerPhone: string;
  status: PaymentAttemptStatus;
  providerStatus: string | null;
  providerTransactionId: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  webhookProcessed: boolean;
  retryCount: number;
  lastError: string | null;
  fulfilled: boolean;
  reviewStatus: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNotes: string | null;
  notes: string | null;
  riskScore?: number | null;
  riskLevel?: string | null;
  riskFlags?: string[] | null;
  createdAt: string | null;
  updatedAt: string | null;
  expiresAt: string | null;
}

/** A payment is "stuck" when it has been in flight for more than 30 minutes. */
export const STUCK_AFTER_MINUTES = 30;
const IN_FLIGHT: PaymentAttemptStatus[] = ['CREATED', 'PENDING_APPROVAL', 'PROCESSING'];

export function attemptAgeMinutes(a: TxPaymentAttempt, now: number = Date.now()): number {
  const t = a.createdAt ? new Date(a.createdAt).getTime() : NaN;
  return Number.isNaN(t) ? 0 : Math.max(0, Math.round((now - t) / 60000));
}

export function isStuckAttempt(a: TxPaymentAttempt, now: number = Date.now()): boolean {
  return IN_FLIGHT.includes(a.status) && attemptAgeMinutes(a, now) > STUCK_AFTER_MINUTES;
}

export const IN_REVIEW_STATUSES: readonly TransactionReviewStatus[] = ['PENDING_REVIEW', 'UNDER_REVIEW', 'ESCALATED'];

export function usePaymentAttempts() {
  const { data, loading, error, refetch } = useQuery<Record<string, TxPaymentAttempt[]>>(PAYMENT_ATTEMPTS_BY_STATUS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const items = useMemo(() => {
    const all = Object.values(data ?? {}).flat().filter(Boolean) as TxPaymentAttempt[];
    return all.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  }, [data]);
  return { items, loading, error: error as Error | undefined, refetch: () => void refetch() };
}

export function usePaymentAttemptActions() {
  const opts = { refetchQueries: ['TxPaymentAttempts'], awaitRefetchQueries: true };
  const [note, n] = useMutation(ADD_PAYMENT_ATTEMPT_NOTE, opts);
  const [review, r] = useMutation(SET_PAYMENT_ATTEMPT_REVIEW_STATUS, opts);
  return {
    addNote: useCallback(async (depositId: string, text: string) => void (await note({ variables: { depositId, note: text } })), [note]),
    setReviewStatus: useCallback(
      async (depositId: string, reviewStatus: string, notes?: string) =>
        void (await review({ variables: { depositId, reviewStatus, notes: notes ?? null } })),
      [review]
    ),
    busy: n.loading || r.loading,
  };
}

/* ----------------------------------------------------------------- tickets */

export type TicketStatus = 'ISSUED' | 'VALIDATED' | 'TRANSFERRED' | 'REFUND_PENDING' | 'REFUNDED' | 'CANCELLED' | 'EXPIRED';

export interface TxTicket {
  id: string;
  ticketNumber: string;
  eventId: string;
  eventTitle: string;
  buyerName: string | null;
  buyerEmail: string | null;
  buyerPhone: string | null;
  ticketCategoryCode: string | null;
  ticketCategoryName: string | null;
  price: number | string;
  currency: string;
  status: TicketStatus;
  qrCode: string | null;
  purchaseDate: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
}

export interface TicketSearchOptions {
  searchQuery?: string;
  status?: TicketStatus | null;
  eventId?: string | null;
  page?: number;
  size?: number;
}

export function useTicketSearch(opts: TicketSearchOptions = {}) {
  const size = opts.size ?? 20;
  const { data, loading, error, refetch } = useQuery<TxSearchTicketsQuery, TxSearchTicketsQueryVariables>(SEARCH_TICKETS, {
    variables: {
      filter: { searchQuery: opts.searchQuery || null, status: opts.status ?? null, eventId: opts.eventId ?? null },
      pagination: { page: opts.page ?? 0, size, sortBy: 'createdAt', sortDirection: 'DESC' },
    } as TxSearchTicketsQueryVariables,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.searchTickets.data ?? [],
    pageInfo: pageInfo(data?.searchTickets.pagination, size),
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export interface AdminTicketUpdate {
  buyerName?: string;
  buyerEmail?: string;
  buyerPhone?: string;
  ticketCategoryCode?: string;
  notes?: string;
}

export function useTicketActions() {
  const opts = { refetchQueries: ['TxSearchTickets'], awaitRefetchQueries: true };
  const [update, u] = useMutation(ADMIN_UPDATE_TICKET, opts);
  const [qr, q] = useMutation(REGENERATE_TICKET_QR, opts);
  const [bulk, b] = useMutation<TxBulkCancelTicketsMutation, TxBulkCancelTicketsMutationVariables>(BULK_CANCEL_TICKETS, opts);
  return {
    updateTicket: useCallback(
      async (ticketId: string, input: AdminTicketUpdate) => void (await update({ variables: { ticketId, input } })),
      [update]
    ),
    regenerateQr: useCallback(async (ticketId: string) => void (await qr({ variables: { ticketId } })), [qr]),
    bulkCancel: useCallback(
      async (ticketIds: string[], reason: string) =>
        (await bulk({ variables: { ticketIds, reason } })).data?.bulkCancelTickets ?? { processedCount: 0, failedCount: 0 },
      [bulk]
    ),
    busy: u.loading || q.loading || b.loading,
  };
}

/* ------------------------------------------------------------ reservations */

export type ReservationStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'RELEASED' | 'FAILED';

export interface TxReservation {
  id: string;
  eventId: string;
  userId: string;
  status: ReservationStatus;
  totalAmount: number | string;
  currency: string;
  expiresAt: string;
  createdAt: string;
  confirmedAt: string | null;
  releasedAt: string | null;
  failedAt: string | null;
  failureReason: string | null;
  items: Array<{ tierName: string; quantity: number }>;
}

/** Reservations are only enumerable per event (`reservationsByEvent`). Skipped until an event is chosen. */
export function useReservationsByEvent(eventId: string | null, page = 0, size = 20) {
  const { data, loading, error, refetch } = useQuery<TxReservationsByEventQuery, TxReservationsByEventQueryVariables>(RESERVATIONS_BY_EVENT, {
    variables: { eventId, pagination: { page, size, sortBy: 'createdAt', sortDirection: 'DESC' } } as TxReservationsByEventQueryVariables,
    skip: !eventId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    items: data?.reservationsByEvent.data ?? [],
    pageInfo: pageInfo(data?.reservationsByEvent.pagination, size),
    loading: !!eventId && loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

export function useForceExpireReservation() {
  const [run, state] = useMutation(FORCE_EXPIRE_RESERVATION, { refetchQueries: ['TxReservationsByEvent'], awaitRefetchQueries: true });
  return {
    forceExpire: useCallback(async (reservationId: string) => void (await run({ variables: { reservationId } })), [run]),
    busy: state.loading,
  };
}

