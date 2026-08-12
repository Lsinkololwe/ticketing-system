/**
 * The frontend counterpart of the backend's `CachedRevocationCheck`: a Redis cache in front of
 * an independent durable store, with the same three-state semantics.
 *
 * ## Resolution order
 *
 * Redis and identity-service fail for unrelated reasons, so keeping both on the read path means
 * a Redis outage costs latency rather than enforcement and `UNKNOWN` is reached only when both
 * are unreachable. This matches the backend's `CachedRevocationCheck`, so a route handler and
 * `/graphql` agree on whether a session is alive.
 *
 * ## Trusting a miss
 *
 * A cache hit is conclusive. A miss is only accepted while the completeness sentinel written by
 * identity-service is present; otherwise the check falls through to the durable store, so a
 * revocation the cache never received cannot be masked by its own absence.
 *
 * @module libs/shared/src/auth/revocation/RevocationService
 */

import type { Redis } from 'ioredis';

import {
  REVOCATION_CACHE_COMPLETE_KEY,
  identifiersFromClaims,
  maskIdentifier,
  revocationCacheKey,
} from './keys';
import type { IdentityRevocationClient } from './IdentityRevocationClient';
import type { IRevocationService } from './IRevocationService';
import type {
  RevocationDecision,
  RevocationWriteResult,
  RevokeSignOutInput,
  TokenClaims,
} from './types';

// =============================================================================
// OPTIONS
// =============================================================================

export interface RevocationServiceOptions {
  /**
   * Local Redis, used as a read cache only.
   *
   * Optional: without it, checks resolve through the durable store alone — correct, but slower.
   */
  redis?: Redis | null;

  /** The durable system of record. Required — there is no cache-only mode. */
  client: IdentityRevocationClient;

  /**
   * Budget for the cache lookup before falling through, in ms.
   *
   * Kept low so a slow cache hands over to the durable store rather than adding latency to
   * every request.
   */
  cacheTimeoutMs?: number;

  /** Seconds a primed cache entry lives. Should track the realm's `accessTokenLifespan`. */
  cacheTtlSeconds?: number;

  logger?: Pick<Console, 'warn' | 'error' | 'info'>;
}

// =============================================================================
// SERVICE
// =============================================================================

export class RevocationService implements IRevocationService {
  private readonly redis: Redis | null;
  private readonly client: IdentityRevocationClient;
  private readonly cacheTimeoutMs: number;
  private readonly cacheTtlSeconds: number;
  private readonly logger: Pick<Console, 'warn' | 'error' | 'info'>;

  /**
   * Set when a revocation failed to reach the cache and the completeness marker could not be
   * withdrawn — typically because the same outage prevented both writes.
   *
   * While it is set this process treats the cache as incomplete, and retries the withdrawal on
   * the next check so other readers stop trusting misses too. It clears once the withdrawal
   * lands; identity-service's warmer then restores a correct cache and re-marks it complete.
   */
  private cacheWithdrawalPending = false;

  constructor(options: RevocationServiceOptions) {
    this.redis = options.redis ?? null;
    this.client = options.client;
    this.cacheTimeoutMs = options.cacheTimeoutMs ?? 250;
    // Default matches the realm's accessTokenLifespan (3600s) plus a minute of clock skew.
    this.cacheTtlSeconds = options.cacheTtlSeconds ?? 3660;
    this.logger = options.logger ?? console;
  }

  // ===========================================================================
  // READ
  // ===========================================================================

  async check(claims: TokenClaims): Promise<RevocationDecision> {
    const identifiers = identifiersFromClaims(claims);

    if (identifiers.length === 0) {
      // Nothing to match against a revocation record, so the state is unknown rather than active.
      this.logger.warn('[Revocation] Token carries no jti/sid/sub — cannot be checked');
      return 'UNKNOWN';
    }

    const cached = await this.checkCache(identifiers);
    if (cached !== null) {
      return cached;
    }

    try {
      return await this.client.check(identifiers);
    } catch (error) {
      this.logger.error(
        '[Revocation] Neither the cache nor identity-service could resolve the token ' +
          '(sub=%s). Fail-closed operations will be refused until one recovers: %s',
        maskIdentifier(claims.sub),
        error instanceof Error ? error.message : String(error)
      );
      return 'UNKNOWN';
    }
  }

  /**
   * @returns the decision, or `null` when the cache could not answer and the caller should fall
   *          through to the durable store
   */
  private async checkCache(
    identifiers: { type: 'TOKEN' | 'SESSION' | 'USER'; value: string }[]
  ): Promise<RevocationDecision | null> {
    if (!this.redis) return null;

    try {
      await this.retryPendingWithdrawal();

      const keys = identifiers.map((id) => revocationCacheKey(id.type, id.value));

      // Identifier lookups and the sentinel in one round trip.
      const [found, complete] = await this.withTimeout(
        Promise.all([
          this.redis.exists(...keys),
          this.redis.exists(REVOCATION_CACHE_COMPLETE_KEY),
        ]),
        this.cacheTimeoutMs
      );

      if (found > 0) return 'REVOKED';
      if (complete > 0 && !this.cacheWithdrawalPending) return 'ACTIVE';

      // Miss against an incomplete cache proves nothing about this token.
      return null;
    } catch (error) {
      this.logger.warn(
        '[Revocation] Cache unavailable, falling back to identity-service: %s',
        error instanceof Error ? error.message : String(error)
      );
      return null;
    }
  }

  // ===========================================================================
  // WRITE
  // ===========================================================================

  async revokeSignOut(input: RevokeSignOutInput): Promise<RevocationWriteResult> {
    const identifiers = identifiersFromClaims(input);

    if (identifiers.length === 0) {
      return {
        persisted: false,
        identifiersRevoked: 0,
        cachePrimed: false,
        error: 'no jti, sid or sub supplied — nothing to revoke',
      };
    }

    let identifiersRevoked: number;
    try {
      // Durable first: if this fails, nothing was revoked and the caller is told so.
      identifiersRevoked = await this.client.revokeSignOut(input);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.logger.error(
        '[Revocation] Sign-out for %s was NOT persisted: %s. The access token remains ' +
          'valid until it expires on its own.',
        maskIdentifier(input.sub),
        message
      );
      return { persisted: false, identifiersRevoked: 0, cachePrimed: false, error: message };
    }

    const cachePrimed = await this.primeCache(identifiers);
    return { persisted: true, identifiersRevoked, cachePrimed };
  }

  /** Best-effort: the record is already durable, so a failure here costs a lookup. */
  private async primeCache(
    identifiers: { type: 'TOKEN' | 'SESSION' | 'USER'; value: string }[]
  ): Promise<boolean> {
    if (!this.redis) return false;

    try {
      const pipeline = this.redis.pipeline();
      for (const id of identifiers) {
        pipeline.setex(revocationCacheKey(id.type, id.value), this.cacheTtlSeconds, 'revoked');
      }

      const results = await this.withTimeout(pipeline.exec(), this.cacheTimeoutMs);

      // `exec` reports per-command failures in its result tuples rather than rejecting, so the
      // absence of a thrown error is not evidence the keys were written.
      const failure = results?.find(([commandError]) => commandError)?.[0];
      if (!results || failure) {
        throw failure ?? new Error('pipeline returned no result');
      }

      return true;
    } catch (error) {
      this.logger.warn(
        '[Revocation] Revocation is durable but was not cached: %s',
        error instanceof Error ? error.message : String(error)
      );
      // The cache is now missing a live revocation, so withdraw the completeness guarantee for
      // every reader until identity-service's warmer reloads it.
      this.cacheWithdrawalPending = true;
      await this.retryPendingWithdrawal();
      return false;
    }
  }

  /**
   * Attempts an outstanding withdrawal of the completeness marker.
   *
   * A no-op unless a previous cache write failed. Kept off the happy path so a healthy service
   * pays nothing for it.
   */
  private async retryPendingWithdrawal(): Promise<void> {
    if (!this.cacheWithdrawalPending || !this.redis) return;

    try {
      await this.withTimeout(
        this.redis.del(REVOCATION_CACHE_COMPLETE_KEY),
        this.cacheTimeoutMs
      );
      this.cacheWithdrawalPending = false;
    } catch {
      // Still unreachable. The flag keeps this process distrusting misses, and the next check
      // tries again.
    }
  }

  // ===========================================================================
  // HEALTH
  // ===========================================================================

  async isEnforceable(): Promise<boolean> {
    const durable = await this.client
      .ping()
      .then(() => true)
      .catch(() => false);
    if (durable) return true;

    if (!this.redis) return false;
    return this.withTimeout(this.redis.ping(), this.cacheTimeoutMs)
      .then(() => true)
      .catch(() => false);
  }

  // ===========================================================================
  // PRIVATE
  // ===========================================================================

  private withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
    return Promise.race([
      promise,
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error(`timed out after ${ms}ms`)), ms)
      ),
    ]);
  }
}
