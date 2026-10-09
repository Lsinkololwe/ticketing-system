'use server';

import { BffAuthError } from '@pml.tickets/shared/auth/bff';
import { bff, FRESH_AUTH_SEC, ORGANIZER_ROLES } from '@/lib/bff';

export type FreshAuthResult = { ok: true } | { ok: false; reason: 'STEP_UP_REQUIRED' | 'UNAUTHENTICATED' | 'FORBIDDEN'; stepUpUrl?: string };

/**
 * Server Action: is the session's last interactive login recent enough for money movement and
 * bank-account changes? When it is not, `stepUpUrl` sends the user through Keycloak (`max_age`)
 * and back to `next`. The backend must enforce `auth_time` for the same operations (D6).
 */
export async function requireFreshAuth(next: string): Promise<FreshAuthResult> {
  try {
    await bff.requireSession({ roles: ORGANIZER_ROLES, freshAuthSec: FRESH_AUTH_SEC, kind: 'action', returnTo: next });
    return { ok: true };
  } catch (e) {
    if (e instanceof BffAuthError) return { ok: false, reason: e.code, stepUpUrl: e.stepUpUrl };
    throw e;
  }
}
