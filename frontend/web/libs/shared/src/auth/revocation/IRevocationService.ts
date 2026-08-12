/**
 * The contract every app depends on for revocation.
 *
 * Shared by `organization-admin`, `admin` and `ticketing`. The semantics — three-state reads and
 * durable-first writes — are identical in each; only the wiring differs, so nothing app-specific
 * belongs in this interface.
 *
 * @module libs/shared/src/auth/revocation/IRevocationService
 */

import type {
  RevocationDecision,
  RevocationWriteResult,
  RevokeSignOutInput,
  TokenClaims,
} from './types';

export interface IRevocationService {
  /**
   * Resolves whether a token has been revoked.
   *
   * Never throws and never reports `ACTIVE` for a failure — an unreachable store is `UNKNOWN`,
   * so the caller can apply its own policy.
   */
  check(claims: TokenClaims): Promise<RevocationDecision>;

  /**
   * Records a sign-out durably, then primes the local cache.
   *
   * Resolves with `persisted: false` rather than throwing, so a logout flow can still clear the
   * local session and redirect — but the caller must surface the failure, because the token is
   * still valid everywhere else.
   */
  revokeSignOut(input: RevokeSignOutInput): Promise<RevocationWriteResult>;

  /** True when at least one store can answer. For health endpoints. */
  isEnforceable(): Promise<boolean>;
}
