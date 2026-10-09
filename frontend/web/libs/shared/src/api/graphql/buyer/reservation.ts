'use client';

import { gql } from '@apollo/client';
import { useMutation } from '@apollo/client/react';
import type { CancelReservationMutation, CancelReservationMutationVariables } from '../../../types/graphql';

export interface ReservationItemRow {
  ticketTierId: string;
  tierName: string;
  quantity: number;
  unitPrice: string | number;
  subtotal: string | number;
}
export type ReservationStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'RELEASED' | 'FAILED';

/** The reservation fields the checkout needs (matches the shared ReservationFields fragment). */
export interface ReservationRow {
  id: string;
  eventId: string;
  totalAmount: string | number;
  currency: string;
  status: ReservationStatus;
  expiresAt: string;
  remainingSeconds: number | null;
  discountAmount: string | number | null;
  promoCodeApplied: string | null;
  paymentIntentId: string | null;
  confirmedAt: string | null;
  releasedAt: string | null;
  failedAt: string | null;
  failureReason: string | null;
  items: ReservationItemRow[];
}

const CANCEL = gql`
  mutation CancelReservation($reservationId: ID!) {
    cancelReservation(reservationId: $reservationId)
  }
`;

export function useCancelReservation() {
  const [mutate, { loading }] = useMutation<CancelReservationMutation, CancelReservationMutationVariables>(CANCEL);
  return { loading, cancel: (reservationId: string) => mutate({ variables: { reservationId } }) };
}

export const ticketCount = (r: Pick<ReservationRow, 'items'>) => r.items.reduce((a, i) => a + i.quantity, 0);
export const subtotalOf = (r: Pick<ReservationRow, 'items'>) => r.items.reduce((a, i) => a + Number(i.subtotal), 0);
