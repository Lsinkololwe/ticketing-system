/**
 * Canonical Redis key layout for revocation.
 *
 * Revocation keys are global and are never namespaced by application: a revoked credential is
 * revoked everywhere, so every app and every backend service reads and writes the same keys.
 * Better Auth's `redisKeyPrefix` is a separate, genuinely per-app namespace covering that app's
 * session storage only.
 *
 * Must stay in sync with `com.pml.shared.security.TokenBlacklistConstants`.
 *
 * @module libs/shared/src/auth/revocation/keys
 */

import type { RevocationType } from './types';

/**
 * Global key prefixes, identical to the Java constants of the same name.
 *
 * Not to be prefixed with an app id — see the module doc.
 */
export const REVOCATION_KEY_PREFIX: Record<RevocationType, string> = {
  TOKEN: 'pml:blacklist:',
  SESSION: 'pml:session:',
  USER: 'pml:revoked:',
} as const;

/**
 * Present only while the cache is known to hold every live revocation.
 *
 * Maintained by identity-service, which owns the data and runs the warmer. Readers consult it to
 * decide whether a cache miss is an answer: a key can be absent because the token was never
 * revoked, or because the cache was restarted, flushed, evicted or unreachable when the
 * revocation was written. Only the first of those means "not revoked".
 *
 * Must match `RevocationCacheTrust.SENTINEL_KEY` on the backend.
 */
export const REVOCATION_CACHE_COMPLETE_KEY = 'pml:revocation:cache-complete';

/** Builds the Redis key a revocation is cached under. */
export function revocationCacheKey(type: RevocationType, value: string): string {
  return `${REVOCATION_KEY_PREFIX[type]}${value}`;
}

/** Turns a token's claims into the identifiers worth checking, skipping absent ones. */
export function identifiersFromClaims(claims: {
  jti?: string;
  sid?: string;
  sub?: string;
}): { type: RevocationType; value: string }[] {
  const identifiers: { type: RevocationType; value: string }[] = [];
  if (claims.jti) identifiers.push({ type: 'TOKEN', value: claims.jti });
  if (claims.sid) identifiers.push({ type: 'SESSION', value: claims.sid });
  if (claims.sub) identifiers.push({ type: 'USER', value: claims.sub });
  return identifiers;
}

/** Masks an identifier for logs. Never log a raw jti, sid or sub. */
export function maskIdentifier(value: string | undefined): string {
  if (!value || value.length <= 8) return '****';
  return `${value.slice(0, 4)}...${value.slice(-4)}`;
}
