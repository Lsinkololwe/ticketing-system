'use client';

import { gql } from '@apollo/client';
import { useLazyQuery, useMutation, useQuery } from '@apollo/client/react';
import { resolveServerError } from './errors';
import { EVENT_DETAIL_FIELDS } from '../events/events.consumer.queryDefinitions';
import type { EventCardRow } from './discover';
import type { EventPageQuery, EventPageQueryVariables, BuyerUnlockTierMutation, BuyerUnlockTierMutationVariables } from '../../../types/graphql';

export interface EventTierRow {
  id: string;
  name: string;
  code: string;
  description: string | null;
  price: string | number;
  originalPrice: string | number | null;
  earlyBirdPrice: string | number | null;
  earlyBirdEndsAt: string | null;
  salesStartAt: string | null;
  salesEndAt: string | null;
  currency: string;
  quantity: number;
  soldQuantity: number;
  availableQuantity: number;
  minPerOrder: number | null;
  maxPerOrder: number | null;
  benefits: string[] | null;
  isActive: boolean;
  isHidden: boolean;
  sortOrder: number;
}

export interface EventPageRow extends EventCardRow {
  locationAddress: string | null;
  refundPolicy: string | null;
  cancellationPolicy: string | null;
  termsAndConditions: string | null;
  isVirtual: boolean;
  isFreeEvent: boolean;
  accessibility: {
    wheelchairAccessible: boolean;
    wheelchairSeatsAvailable: number | null;
    signLanguageInterpreter: boolean;
    hearingLoopAvailable: boolean;
    accessibleParking: boolean;
    accessibleRestrooms: boolean;
    assistanceDogsAllowed: boolean;
    additionalNotes: string | null;
  } | null;
  ticketTiers: EventTierRow[] | null;
  /** What the organizer published about the day. Each is null or empty when they did not. */
  ageRestriction: string | null;
  doorsOpenAt: string | null;
  faqs: Array<{ question: string; answer: string }> | null;
  runningOrder: Array<{ time: string; title: string }> | null;
  gettingThere: string | null;
  parkingInfo: string | null;
  bagPolicy: string | null;
  /** The organizer's public standing: the verified badge (identity) and live events (catalog). */
  organization: { id: string; verified: boolean; publishedEventCount: number } | null;
}

const EVENT_PAGE = gql`
  query EventPage($id: ID!) {
    event(id: $id) {
      ...EventDetailFields
      soldOut
      locationAddress
      refundPolicy
      cancellationPolicy
      termsAndConditions
      isVirtual
      isFreeEvent
      ageRestriction
      doorsOpenAt
      gettingThere
      parkingInfo
      bagPolicy
      faqs {
        question
        answer
      }
      runningOrder {
        time
        title
      }
      organization {
        id
        verified
        publishedEventCount
      }
      accessibility {
        wheelchairAccessible
        wheelchairSeatsAvailable
        signLanguageInterpreter
        hearingLoopAvailable
        accessibleParking
        accessibleRestrooms
        assistanceDogsAllowed
        additionalNotes
      }
    }
  }
  ${EVENT_DETAIL_FIELDS}
`;

const UNLOCK_TIER = gql`
  mutation BuyerUnlockTier($eventId: ID!, $accessCode: String!) {
    unlockTierWithAccessCode(eventId: $eventId, accessCode: $accessCode) {
      id
      name
      code
      description
      price
      originalPrice
      earlyBirdPrice
      earlyBirdEndsAt
      salesStartAt
      salesEndAt
      currency
      quantity
      soldQuantity
      availableQuantity
      minPerOrder
      maxPerOrder
      benefits
      isActive
      isHidden
      sortOrder
    }
  }
`;

const VALIDATE_PROMO = gql`
  query ValidatePromoCode($code: String!, $eventId: ID!, $amount: BigDecimal) {
    validatePromoCode(code: $code, eventId: $eventId, amount: $amount) {
      valid
      discountAmount
      errorMessage
    }
  }
`;

export function useEventPage(id: string) {
  const { data, loading, error, refetch } = useQuery<EventPageQuery, EventPageQueryVariables>(EVENT_PAGE, { variables: { id } });
  return { event: data?.event ?? null, loading, error, refetch };
}

export type UnlockOutcome = { ok: true; tier: EventTierRow } | { ok: false; code: 'INVALID' | 'LOCKED_OUT' | 'ERROR' };

/**
 * Opens the hidden tier an access code belongs to. The backend never says which part was wrong, and stops
 * further tries after five wrong codes in fifteen minutes (surfaced here as LOCKED_OUT).
 */
export function useUnlockTier() {
  const [run, { loading }] = useMutation<BuyerUnlockTierMutation, BuyerUnlockTierMutationVariables>(UNLOCK_TIER, { errorPolicy: 'all' });
  return {
    loading,
    unlock: async (eventId: string, accessCode: string): Promise<UnlockOutcome> => {
      let failure: unknown = null;
      try {
        const res = await run({ variables: { eventId, accessCode } });
        const tier = res.data?.unlockTierWithAccessCode;
        if (tier) return { ok: true, tier: tier as unknown as EventTierRow };
        failure = (res as { error?: unknown }).error ?? null;
      } catch (e) {
        failure = e;
      }
      const code = resolveServerError(failure).code;
      if (code === 'RATE_LIMIT_EXCEEDED') return { ok: false, code: 'LOCKED_OUT' };
      if (code === 'TIER_UNKNOWN') return { ok: false, code: 'INVALID' };
      return { ok: false, code: 'ERROR' };
    },
  };
}

export interface PromoResult {
  valid: boolean;
  discountAmount: number;
  errorMessage: string | null;
}

export function usePromoValidation() {
  const [run, { loading }] = useLazyQuery<{ validatePromoCode: { valid: boolean; discountAmount: string | number | null; errorMessage: string | null } }>(
    VALIDATE_PROMO,
    { fetchPolicy: 'network-only' }
  );
  return {
    loading,
    validate: async (code: string, eventId: string, amount: number): Promise<PromoResult> => {
      const res = await run({ variables: { code, eventId, amount } });
      const v = res.data?.validatePromoCode;
      if (!v) return { valid: false, discountAmount: 0, errorMessage: "That promo code isn't valid for this event." };
      return { valid: v.valid, discountAmount: Number(v.discountAmount ?? 0), errorMessage: v.errorMessage };
    },
  };
}

export * from './event.rules';
