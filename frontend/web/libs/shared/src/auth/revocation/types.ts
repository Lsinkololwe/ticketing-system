/**
 * Shared revocation vocabulary.
 *
 * Mirrors `com.pml.shared.security.revocation` on the backend. The two sides must agree on
 * the type names because they cross the wire in `POST /api/internal/revocations/*`.
 *
 * @module libs/shared/src/auth/revocation/types
 */

// =============================================================================
// IDENTIFIERS
// =============================================================================

/**
 * The three identifiers a token can be revoked by.
 *
 * - `TOKEN`  — the access token's `jti`. Kills the token in hand.
 * - `SESSION` — Keycloak's `sid`. Kills every token minted for that SSO session.
 * - `USER`   — the `sub`. "Sign me out everywhere", bans, compromise response.
 */
export type RevocationType = 'TOKEN' | 'SESSION' | 'USER';

/** One (type, value) pair. */
export interface RevocationIdentifier {
  type: RevocationType;
  value: string;
}

// =============================================================================
// DECISION
// =============================================================================

/**
 * The outcome of a revocation check.
 *
 * `UNKNOWN` is distinct from `ACTIVE` so callers can tell a verified token from an unverifiable
 * one: a dashboard read may proceed on `UNKNOWN`, a fail-closed operation refuses it.
 */
export type RevocationDecision = 'REVOKED' | 'ACTIVE' | 'UNKNOWN';

/** The claims a check needs. All optional — a token may not carry all three. */
export interface TokenClaims {
  jti?: string;
  sid?: string;
  sub?: string;
}

// =============================================================================
// WRITES
// =============================================================================

/** Why a revocation happened. Recorded for the audit trail. */
export type RevocationReason =
  | 'user_logout'
  | 'backchannel_logout'
  | 'session_revoke'
  | 'admin_revoke'
  | 'account_suspended';

export interface RevokeSignOutInput {
  jti?: string;
  sid?: string;
  sub?: string;
  reason: RevocationReason;
  revokedBy?: string;
}

/**
 * The result of a revocation write.
 *
 * `persisted` reports whether the revocation reached the durable system of record. When it is
 * `false` the token is still valid outside this browser and the caller surfaces that.
 */
export interface RevocationWriteResult {
  persisted: boolean;
  identifiersRevoked: number;
  cachePrimed: boolean;
  error?: string;
}
