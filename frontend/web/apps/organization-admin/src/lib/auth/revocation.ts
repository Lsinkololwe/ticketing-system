/**
 * Organizer app wiring for the shared revocation module.
 *
 * ## What is app-specific and what is not
 *
 * The semantics — key layout, three-state reads, durable-first writes and the fail-closed policy
 * — come from `@pml.tickets/shared/auth/revocation` and are identical in every app. This file
 * supplies only what differs for the organizer app: the Redis client used as a cache, the
 * identity-service location, and the service account authorising the internal calls.
 *
 * The revocation key prefix is not among them: those keys are global.
 *
 * @module lib/auth/revocation
 */

import 'server-only';

import {
  IdentityRevocationClient,
  RevocationService,
  createServiceAccountTokenProvider,
  type IRevocationService,
} from '@pml.tickets/shared/auth/revocation';
import type { Redis } from 'ioredis';

import { redis } from './index';

// =============================================================================
// CONFIGURATION
// =============================================================================

const IDENTITY_SERVICE_URL =
  process.env.IDENTITY_SERVICE_URL ?? 'http://localhost:8083';

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? 'http://localhost:8084';
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? 'myticketzm';

const SERVICE_CLIENT_ID = process.env.INTERNAL_CLIENT_ID ?? 'internal-service';
const SERVICE_CLIENT_SECRET = process.env.INTERNAL_CLIENT_SECRET ?? '';

/**
 * Matches the realm's `accessTokenLifespan` (3600s) plus a minute of skew, so a cached entry
 * outlives every token it shadows.
 */
const CACHE_TTL_SECONDS = 3660;

// =============================================================================
// SINGLETON
// =============================================================================

declare global {
  // eslint-disable-next-line no-var
  var __pmlRevocationService: IRevocationService | undefined;
}

function build(): IRevocationService {
  if (!SERVICE_CLIENT_SECRET) {
    // Without a service account the app cannot record revocations, so sign-outs will report
    // failure rather than appear to succeed.
    console.error(
      '[Revocation] INTERNAL_CLIENT_SECRET is not set. Sign-outs will fail to revoke ' +
        'tokens and will report the failure rather than pretending to succeed.'
    );
  }

  const client = new IdentityRevocationClient({
    baseUrl: IDENTITY_SERVICE_URL,
    getAccessToken: createServiceAccountTokenProvider({
      tokenUrl: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
      clientId: SERVICE_CLIENT_ID,
      clientSecret: SERVICE_CLIENT_SECRET,
    }),
  });

  return new RevocationService({
    // Cache only. Enforcement survives its absence.
    redis: (redis as Redis | null) ?? null,
    client,
    cacheTtlSeconds: CACHE_TTL_SECONDS,
  });
}

/**
 * The organizer app's revocation service.
 *
 * Held on `globalThis` so Next.js hot reload does not spawn a new token cache per rebuild.
 */
export const revocationService: IRevocationService =
  globalThis.__pmlRevocationService ?? (globalThis.__pmlRevocationService = build());
