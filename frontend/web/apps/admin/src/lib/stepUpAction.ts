'use server';

import { BffAuthError } from '@pml.tickets/shared/auth/bff';
import { bff, STAFF_ACCESS_ROLES, STEP_UP_SEC } from '@/lib/bff';

export type FreshAuthResult = { fresh: true } | { fresh: false; url: string };

/**
 * Server-side recency check before a sensitive action (payout decisions, refunds, platform rules, user
 * suspension). Stale sign-in: the caller is sent to /api/auth/stepup, which re-authenticates at Keycloak
 * and returns to `returnTo`. The backend remains the enforcement point for the same operations.
 */
export async function checkFreshAuth(returnTo: string): Promise<FreshAuthResult> {
  try {
    await bff.requireSession({ roles: STAFF_ACCESS_ROLES, freshAuthSec: STEP_UP_SEC, kind: 'action', returnTo });
    return { fresh: true };
  } catch (err) {
    if (err instanceof BffAuthError && err.code === 'STEP_UP_REQUIRED' && err.stepUpUrl) return { fresh: false, url: err.stepUpUrl };
    if (err instanceof BffAuthError && err.code === 'UNAUTHENTICATED') return { fresh: false, url: '/login' };
    throw err;
  }
}
