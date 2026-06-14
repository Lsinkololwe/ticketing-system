/**
 * JTI (JWT ID) Blacklist Service
 *
 * SERVER-ONLY: This module uses Redis which requires Node.js runtime.
 *
 * Manages a blacklist of revoked JWT IDs to prevent session creation
 * with tokens that have been invalidated via backchannel logout.
 *
 * ## Architecture Overview
 *
 * ```
 * Keycloak Backchannel Logout
 *     │
 *     ▼
 * Add JTI to Redis Blacklist (with TTL)
 *     │
 *     ▼
 * Session Creation Hook checks blacklist
 *     │
 *     ├── JTI blacklisted → REJECT session
 *     │
 *     └── JTI not blacklisted → ALLOW session
 * ```
 *
 * ## Why JTI Blacklisting?
 *
 * When Keycloak sends a backchannel logout:
 * 1. The user's Keycloak session is terminated
 * 2. But the id_token/access_token may still be valid (not expired)
 * 3. Without blacklisting, a user could replay the token to create a new session
 * 4. JTI blacklisting prevents this by tracking revoked token IDs
 *
 * ## Storage
 *
 * - **Redis** with TTL matching token expiry
 * - Key format: `{prefix}jti-blacklist:{jti}`
 * - Value: JSON with revocation metadata
 *
 * ## Used By
 *
 * - `backchannel-logout.ts` - Adds JTIs on logout
 * - `config.ts` - Checks JTIs before session creation
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 * @module libs/shared/src/auth/better-auth/jti-blacklist
 */

import 'server-only';

import { Redis } from 'ioredis';

// =============================================================================
// TYPES
// =============================================================================

/**
 * Blacklist entry metadata
 */
export interface BlacklistEntry {
  /** JWT ID that was blacklisted */
  jti: string;
  /** User ID (sub claim) */
  userId: string;
  /** Keycloak session ID (sid claim), if available */
  sessionId?: string;
  /** When the JTI was blacklisted */
  blacklistedAt: string;
  /** Why it was blacklisted */
  reason: 'backchannel_logout' | 'manual_revoke' | 'session_revoke';
  /** Original token expiry time */
  tokenExpiry?: number;
}

/**
 * Configuration for the JTI blacklist service
 */
export interface JtiBlacklistConfig {
  /** Redis client instance */
  redis: Redis;
  /** Key prefix for blacklist entries */
  keyPrefix: string;
  /** Default TTL in seconds (used if token expiry not available) */
  defaultTtlSeconds?: number;
}

// =============================================================================
// JTI BLACKLIST SERVICE
// =============================================================================

/**
 * JTI Blacklist Service
 *
 * Provides methods to add, check, and remove JTIs from the blacklist.
 *
 * @example
 * ```typescript
 * const blacklist = createJtiBlacklist({
 *   redis,
 *   keyPrefix: 'pml-admin:',
 * });
 *
 * // Add a JTI to blacklist
 * await blacklist.add({
 *   jti: 'abc123',
 *   userId: 'user-456',
 *   reason: 'backchannel_logout',
 *   tokenExpiry: Math.floor(Date.now() / 1000) + 3600,
 * });
 *
 * // Check if JTI is blacklisted
 * const isBlacklisted = await blacklist.isBlacklisted('abc123');
 * ```
 */
export function createJtiBlacklist(config: JtiBlacklistConfig) {
  const { redis, keyPrefix, defaultTtlSeconds = 86400 } = config; // Default 24 hours

  /**
   * Get the Redis key for a JTI
   */
  function getKey(jti: string): string {
    return `${keyPrefix}jti-blacklist:${jti}`;
  }

  return {
    /**
     * Add a JTI to the blacklist
     *
     * @param entry - Blacklist entry details
     * @returns True if added successfully
     *
     * @used-by
     * - `backchannel-logout.ts` - When Keycloak sends logout
     * - Session revocation handlers
     */
    async add(
      entry: Omit<BlacklistEntry, 'blacklistedAt'>
    ): Promise<boolean> {
      const { jti, userId, sessionId, reason, tokenExpiry } = entry;

      const fullEntry: BlacklistEntry = {
        jti,
        userId,
        sessionId,
        reason,
        tokenExpiry,
        blacklistedAt: new Date().toISOString(),
      };

      // Calculate TTL: use token expiry if available, otherwise default
      let ttlSeconds = defaultTtlSeconds;
      if (tokenExpiry) {
        const now = Math.floor(Date.now() / 1000);
        const remainingSeconds = tokenExpiry - now;
        // Only use if token hasn't expired yet (add buffer)
        if (remainingSeconds > 0) {
          // Add 5-minute buffer to account for clock skew
          ttlSeconds = remainingSeconds + 300;
        }
      }

      try {
        const key = getKey(jti);
        await redis.setex(key, ttlSeconds, JSON.stringify(fullEntry));

        console.log(`[JTI Blacklist] Added JTI ${jti} for user ${userId}, TTL: ${ttlSeconds}s`);
        return true;
      } catch (error) {
        console.error('[JTI Blacklist] Failed to add JTI:', error);
        return false;
      }
    },

    /**
     * Check if a JTI is blacklisted
     *
     * @param jti - JWT ID to check
     * @returns True if blacklisted, false otherwise
     *
     * @used-by
     * - `config.ts` databaseHooks.session.create.before
     * - Token validation middleware
     */
    async isBlacklisted(jti: string): Promise<boolean> {
      try {
        const key = getKey(jti);
        const exists = await redis.exists(key);
        return exists === 1;
      } catch (error) {
        console.error('[JTI Blacklist] Failed to check JTI:', error);
        // Fail open (allow) on Redis errors to prevent lockout
        // In high-security environments, you might want to fail closed
        return false;
      }
    },

    /**
     * Get blacklist entry details
     *
     * @param jti - JWT ID to get details for
     * @returns Blacklist entry or null if not found
     *
     * @used-by
     * - Admin debugging tools
     * - Audit logging
     */
    async get(jti: string): Promise<BlacklistEntry | null> {
      try {
        const key = getKey(jti);
        const value = await redis.get(key);
        if (!value) return null;
        return JSON.parse(value) as BlacklistEntry;
      } catch (error) {
        console.error('[JTI Blacklist] Failed to get JTI:', error);
        return null;
      }
    },

    /**
     * Remove a JTI from the blacklist (manual override)
     *
     * Use with caution - this allows previously revoked tokens to work again.
     *
     * @param jti - JWT ID to remove
     * @returns True if removed, false if not found
     *
     * @used-by
     * - Admin override tools (rare use case)
     */
    async remove(jti: string): Promise<boolean> {
      try {
        const key = getKey(jti);
        const deleted = await redis.del(key);
        return deleted === 1;
      } catch (error) {
        console.error('[JTI Blacklist] Failed to remove JTI:', error);
        return false;
      }
    },

    /**
     * Blacklist all JTIs for a user (mass revocation)
     *
     * This doesn't actually blacklist specific JTIs (we don't know them),
     * but instead stores a "user blacklist" that rejects all tokens
     * issued before a certain time.
     *
     * @param userId - User ID to blacklist
     * @param beforeTimestamp - Reject tokens issued before this time (epoch seconds)
     * @param ttlSeconds - How long to enforce this blacklist
     * @returns True if added successfully
     *
     * @used-by
     * - User account deactivation
     * - Security incident response
     */
    async blacklistUserTokensBefore(
      userId: string,
      beforeTimestamp: number,
      ttlSeconds: number = defaultTtlSeconds
    ): Promise<boolean> {
      try {
        const key = `${keyPrefix}user-blacklist:${userId}`;
        const entry = {
          userId,
          blacklistTokensBefore: beforeTimestamp,
          createdAt: new Date().toISOString(),
        };
        await redis.setex(key, ttlSeconds, JSON.stringify(entry));
        console.log(`[JTI Blacklist] Blacklisted all tokens for user ${userId} before ${new Date(beforeTimestamp * 1000).toISOString()}`);
        return true;
      } catch (error) {
        console.error('[JTI Blacklist] Failed to blacklist user tokens:', error);
        return false;
      }
    },

    /**
     * Check if a user's token is blacklisted by timestamp
     *
     * @param userId - User ID
     * @param tokenIssuedAt - When the token was issued (iat claim, epoch seconds)
     * @returns True if token was issued before the blacklist cutoff
     *
     * @used-by
     * - Token validation that checks both JTI and user-level blacklist
     */
    async isUserTokenBlacklisted(
      userId: string,
      tokenIssuedAt: number
    ): Promise<boolean> {
      try {
        const key = `${keyPrefix}user-blacklist:${userId}`;
        const value = await redis.get(key);
        if (!value) return false;

        const entry = JSON.parse(value) as { blacklistTokensBefore: number };
        return tokenIssuedAt < entry.blacklistTokensBefore;
      } catch (error) {
        console.error('[JTI Blacklist] Failed to check user blacklist:', error);
        return false;
      }
    },

    /**
     * Get statistics about the blacklist
     *
     * @returns Count of blacklisted JTIs and users
     *
     * @used-by
     * - Admin monitoring dashboards
     * - Health checks
     */
    async getStats(): Promise<{ jtiCount: number; userCount: number }> {
      try {
        const jtiKeys = await redis.keys(`${keyPrefix}jti-blacklist:*`);
        const userKeys = await redis.keys(`${keyPrefix}user-blacklist:*`);
        return {
          jtiCount: jtiKeys.length,
          userCount: userKeys.length,
        };
      } catch (error) {
        console.error('[JTI Blacklist] Failed to get stats:', error);
        return { jtiCount: 0, userCount: 0 };
      }
    },
  };
}

/**
 * Type of the JTI blacklist service
 */
export type JtiBlacklistService = ReturnType<typeof createJtiBlacklist>;
