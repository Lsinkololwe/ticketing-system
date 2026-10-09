'use client';

/** Promo codes (discounts) per event (booking-service). */
import type { OrgEventPromosQuery, OrgEventPromosQueryVariables } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export interface PromoRow {
  id: string;
  code: string;
  eventId: string | null;
  discountType: DiscountType;
  discountValue: string;
  maxUses: number | null;
  currentUses: number;
  validFrom: string | null;
  validUntil: string | null;
  minPurchaseAmount: string | null;
  maxDiscountAmount: string | null;
  applicableTiers: string[] | null;
  isActive: boolean;
}

export interface PromoInput {
  code?: string;
  discountType: DiscountType;
  discountValue: string;
  maxUses?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  minPurchaseAmount?: string | null;
  maxDiscountAmount?: string | null;
  applicableTiers?: string[];
}

export const EVENT_PROMOS = gql`query OrgEventPromos($eventId: ID!) { eventPromoCodes(eventId: $eventId) { id code eventId discountType discountValue maxUses currentUses validFrom validUntil minPurchaseAmount maxDiscountAmount applicableTiers isActive } }`;
const CREATE = gql`mutation OrgCreatePromo($input: CreatePromoCodeInput!) { createPromoCode(input: $input) { id } }`;
const UPDATE = gql`mutation OrgUpdatePromo($id: ID!, $input: UpdatePromoCodeInput!) { updatePromoCode(id: $id, input: $input) { id } }`;
const ACTIVATE = gql`mutation OrgActivatePromo($id: ID!) { activatePromoCode(id: $id) { id isActive } }`;
const DEACTIVATE = gql`mutation OrgDeactivatePromo($id: ID!) { deactivatePromoCode(id: $id) { id isActive } }`;
const DELETE = gql`mutation OrgDeletePromo($id: ID!) { deletePromoCode(id: $id) }`;

export function useEventPromos(eventId: string) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrgEventPromosQuery, OrgEventPromosQueryVariables>(EVENT_PROMOS, {
    variables: { eventId },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { promos: data?.eventPromoCodes ?? [], loading, error, refetch };
}

export function usePromoActions(eventId: string) {
  const opts = { refetchQueries: [EVENT_PROMOS] };
  const [create] = useMutation(CREATE, opts);
  const [update] = useMutation(UPDATE, opts);
  const [activate] = useMutation(ACTIVATE, opts);
  const [deactivate] = useMutation(DEACTIVATE, opts);
  const [del] = useMutation(DELETE, opts);
  return {
    create: (input: PromoInput) => create({ variables: { input: { ...input, eventId } } }),
    update: (id: string, input: Omit<PromoInput, 'code'>) => update({ variables: { id, input } }),
    setActive: (id: string, active: boolean) => (active ? activate : deactivate)({ variables: { id } }),
    remove: (id: string) => del({ variables: { id } }),
  };
}
