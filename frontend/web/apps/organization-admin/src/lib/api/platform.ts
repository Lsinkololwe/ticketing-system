'use client';

/**
 * Platform rules and reference lists the organizer reads (identity `platformRules`, catalog
 * `referenceData`). The platform owns every value: nothing here has a built-in default, so a
 * region that cannot read them shows its designed unavailable state instead of a guess.
 */
import type { ReferenceType } from '@pml.tickets/shared/types/graphql';
import type { OrganizerPlatformRulesQuery, OrganizerPlatformRulesQueryVariables } from '@pml.tickets/shared/types/graphql';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { toRulesView } from '@/lib/settings/platformRules';

export const PLATFORM_RULES = gql`
  query OrganizerPlatformRules {
    platformRules {
      version
      updatedAt
      updatedBy
      currency
      commissionDefault
      commissionRate
      minimumPayout
      reservationHoldMinutes
      reservationGraceMinutes
      escrowHoldDays
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
      approval {
        slaHours
        warnHours
        autoEscalation
        escalationDelayHours
        requireCommentsOnRejection
        requireCommentsOnChangesRequested
        allowSelfApproval
      }
    }
  }
`;

export type PlatformRulesData = OrganizerPlatformRulesQuery['platformRules'];

export function usePlatformRules() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<OrganizerPlatformRulesQuery, OrganizerPlatformRulesQueryVariables>(PLATFORM_RULES, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { rules: data?.platformRules ?? null, loading, error, refetch };
}

/**
 * One reference list (banks, cancellation reasons, age restrictions ...), sorted by the platform's display
 * order. A thin wrapper over the shared `useReferenceOptions`, which owns the query and the cache.
 */
export function useReferenceList(type: ReferenceType, options?: { skip?: boolean }) {
  const list = useReferenceOptions(type, options);
  return { items: list.items, options: list.options, loading: list.loading, error: list.error, empty: list.empty };
}

/** The rules plus every reference list the rules page and the editors share. */
export function usePlatformRulesView() {
  const rules = usePlatformRules();
  const categories = useReferenceList('EVENT_CATEGORY');
  const provinces = useReferenceList('PROVINCE');
  const cities = useReferenceList('CITY');
  const banks = useReferenceList('BANK');
  const documentTypes = useReferenceList('KYB_DOCUMENT_TYPE');
  const reasons = useReferenceList('CANCELLATION_REASON');
  const names = (l: { items: Array<{ name: string }> }) => l.items.map((i) => i.name);
  const view = rules.rules
    ? toRulesView(rules.rules, {
        categories: names(categories),
        provinces: names(provinces),
        cities: names(cities),
        banks: names(banks),
        documentTypes: names(documentTypes),
        cancellationReasons: names(reasons),
      })
    : null;
  return { view, loading: rules.loading, error: rules.error, refetch: rules.refetch };
}
