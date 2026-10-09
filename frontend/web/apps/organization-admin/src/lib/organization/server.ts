import 'server-only';
import { cache } from 'react';
import { bff } from '@/lib/bff';
import { parseStatus, routeForStatus } from '../onboarding/state';
import { resolveOnboardingState, toAuthStatus } from './onboarding';

export type { OrganizationAuthStatus } from './onboarding';

/** One round-trip per request, shared by layouts and pages. */
export const getOnboardingState = cache(() =>
  resolveOnboardingState({
    getAccessToken: () => bff.getAccessToken(),
    graphqlUrl: process.env.GRAPHQL_URL ?? '',
  })
);

export const getOrganizationStatus = cache(async () => {
  const state = await getOnboardingState();
  if (state.kind === 'unknown') console.error('[organization] status unresolved:', state.reason);
  return toAuthStatus(state);
});

export function getRouteForStatus(status: string | null): string {
  const parsed = parseStatus(status);
  return parsed ? routeForStatus(parsed) : '/welcome';
}
