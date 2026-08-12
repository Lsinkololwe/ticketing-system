/**
 * Token revocation — shared across every frontend app.
 *
 * Reusable: the key layout, the three-state decision, the durable-first write path and the
 * fail-closed policy are identical in `organization-admin`, `admin` and `ticketing`. A per-app
 * variation in any of them is how the original bug happened.
 *
 * App-specific: which operations are fail-closed, and the wiring (Redis client, identity-service
 * URL, service-account token). Those live in each app's `lib/auth`.
 *
 * @module libs/shared/src/auth/revocation
 */

export * from './types';
export * from './keys';
export * from './errors';
export * from './IRevocationService';
export * from './IdentityRevocationClient';
export * from './RevocationService';
export * from './failClosed';
export * from './serviceAccountToken';
export * from './LegacyJtiBlacklistAdapter';
