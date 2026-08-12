/**
 * Presents a legacy {@link IJtiBlacklistService} through the {@link IRevocationService}
 * interface, for apps not yet wired to identity-service.
 *
 * ## Scope
 *
 * `organization-admin` uses {@link RevocationService} and reaches the durable system of record.
 * `admin` and `ticketing` still use the Redis-only blacklist, and this adapter lets them keep
 * their existing behaviour while `BackchannelLogoutHandler` depends on a single interface.
 *
 * ## Limitations
 *
 * The backing store is a single Redis under a per-app key prefix, so:
 *
 * - revocations are not durable, and a write during a Redis outage is lost;
 * - the keys are not the canonical `pml:*` layout the backend reads, so a revocation recorded
 *   here is not visible to the backend services;
 * - `check` can only report `UNKNOWN` when Redis is unreachable, never a durable answer.
 *
 * Migrating an app means giving it a {@link RevocationService} in its `lib/auth` and dropping
 * this adapter.
 *
 * @module libs/shared/src/auth/revocation/LegacyJtiBlacklistAdapter
 */

import { identifiersFromClaims } from './keys';
import type { IRevocationService } from './IRevocationService';
import type {
  RevocationDecision,
  RevocationWriteResult,
  RevokeSignOutInput,
  TokenClaims,
} from './types';

/** The subset of the legacy blacklist this adapter needs. */
export interface LegacyJtiBlacklist {
  add(entry: {
    jti: string;
    userId: string;
    sessionId?: string;
    reason: string;
    tokenExpiry?: number;
  }): Promise<boolean>;
  isBlacklisted(jti: string): Promise<boolean>;
  blacklistUserTokensBefore(userId: string, beforeTimestamp: number): Promise<boolean>;
}

export class LegacyJtiBlacklistAdapter implements IRevocationService {
  constructor(
    private readonly blacklist: LegacyJtiBlacklist,
    private readonly logger: Pick<Console, 'warn' | 'error'> = console
  ) {}

  async check(claims: TokenClaims): Promise<RevocationDecision> {
    if (!claims.jti) {
      return 'UNKNOWN';
    }
    try {
      return (await this.blacklist.isBlacklisted(claims.jti)) ? 'REVOKED' : 'ACTIVE';
    } catch (error) {
      this.logger.warn(
        '[Revocation] Legacy blacklist could not be read: %s',
        error instanceof Error ? error.message : String(error)
      );
      return 'UNKNOWN';
    }
  }

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

    try {
      let revoked = 0;

      if (input.jti) {
        if (await this.blacklist.add({
          jti: input.jti,
          userId: input.sub ?? 'unknown',
          sessionId: input.sid,
          reason: input.reason,
        })) {
          revoked += 1;
        }
      }

      if (input.sub) {
        if (await this.blacklist.blacklistUserTokensBefore(
          input.sub,
          Math.floor(Date.now() / 1000)
        )) {
          revoked += 1;
        }
      }

      // `cachePrimed` is the same write as the durable one here — there is only one store.
      return { persisted: revoked > 0, identifiersRevoked: revoked, cachePrimed: revoked > 0 };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.logger.error('[Revocation] Legacy blacklist write failed: %s', message);
      return { persisted: false, identifiersRevoked: 0, cachePrimed: false, error: message };
    }
  }

  async isEnforceable(): Promise<boolean> {
    try {
      await this.blacklist.isBlacklisted('enforceability-probe');
      return true;
    } catch {
      return false;
    }
  }
}
