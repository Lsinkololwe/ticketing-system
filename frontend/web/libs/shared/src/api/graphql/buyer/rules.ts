'use client';

import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import type { BuyerPlatformRulesQuery } from '../../../types/graphql';

/** The values buyers must obey, read from the platform: hold length, tickets per booking and the refund policies. */
export type BuyerPlatformRules = BuyerPlatformRulesQuery['publicPlatformRules'];
export type BuyerRefundPolicy = BuyerPlatformRules['refundPolicies'][number];

const RULES = gql`
  query BuyerPlatformRules {
    publicPlatformRules {
      version
      updatedAt
      currency
      reservationHoldMinutes
      reservationGraceMinutes
      refundCutoffHours
      maxTicketsPerBooking
      rescheduleLimit
      refundPolicies {
        code
        label
        summary
        rules {
          daysBefore
          percent
        }
      }
    }
  }
`;

/** The buyer-facing platform rules, readable signed out and signed in alike. `rules` stays null while loading or when unavailable. */
export function usePlatformRules(skip = false) {
  const { data, loading, error, refetch } = useQuery<BuyerPlatformRulesQuery>(RULES, { skip, fetchPolicy: 'cache-first' });
  return { rules: data?.publicPlatformRules ?? null, loading, error, refetch };
}

/** The policy an event picked, by the code the catalog stores (FLEXIBLE, MODERATE, STRICT, NO_REFUNDS), or null when the platform does not list it. */
export function policyFor(rules: Pick<BuyerPlatformRules, 'refundPolicies'> | null, code: string | null): BuyerRefundPolicy | null {
  if (!rules || !code) return null;
  return rules.refundPolicies.find((p) => p.code === code) ?? null;
}

/** One rule in the buyer's words: "50% refund if you ask at least 7 days before the event". */
export function describeRule(r: { daysBefore: number; percent: number }): string {
  const d = r.daysBefore;
  const lead = d >= 2 ? `${d} days` : d === 1 ? '1 day' : `${Math.round(d * 24)} hours`;
  return `${r.percent}% refund if you ask at least ${lead} before the event`;
}

/** A policy's rules, longest lead time first. */
export function sortedRules(policy: BuyerRefundPolicy) {
  return [...policy.rules].sort((a, b) => b.daysBefore - a.daysBefore);
}

/** "2 days" for whole days of 48 hours or more, otherwise hours: how long before the event refund requests close. */
export function hoursText(h: number): string {
  return h % 24 === 0 && h >= 48 ? `${h / 24} days` : `${h} hour${h === 1 ? '' : 's'}`;
}
