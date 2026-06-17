/**
 * JTI Blacklist Service
 *
 * SERVER-ONLY: Uses Redis for storage.
 *
 * Class-based implementation of IJtiBlacklistService.
 * Manages blacklisted JWT IDs to prevent session creation
 * with revoked tokens.
 *
 * ## Native Redis Features Used
 *
 * - SETEX: Set with expiry (atomic)
 * - EXISTS: Check key existence
 * - GET/DEL: Standard operations
 * - KEYS: Pattern matching (for stats)
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 *
 * @example
 * ```typescript
 * const blacklist = new JtiBlacklistService(redis, 'pml-admin:');
 *
 * await blacklist.add({
 *   jti: 'abc123',
 *   userId: 'user-456',
 *   reason: 'backchannel_logout',
 * });
 *
 * if (await blacklist.isBlacklisted('abc123')) {
 *   throw new Error('Token revoked');
 * }
 * ```
 *
 * @module libs/shared/src/auth/better-auth/services/JtiBlacklistService
 */

import 'server-only';

import type { Redis } from 'ioredis';
import type {
  IJtiBlacklistService,
  BlacklistEntry,
  BlacklistInput,
  BlacklistStats,
} from '../interfaces/IJtiBlacklistService';

// =============================================================================
// CONSTANTS
// =============================================================================

/** Default TTL for blacklist entries (24 hours) */
const DEFAULT_TTL_SECONDS = 86400;

/** Clock skew buffer for token expiry (5 minutes) */
const CLOCK_SKEW_BUFFER_SECONDS = 300;

// =============================================================================
// JTI BLACKLIST SERVICE
// =============================================================================

/**
 * JTI Blacklist Service
 *
 * Stores blacklisted JWT IDs in Redis with TTL matching token expiry.
 */
export class JtiBlacklistService implements IJtiBlacklistService {
  private readonly redis: Redis;
  private readonly keyPrefix: string;
  private readonly defaultTtlSeconds: number;

  /**
   * Create JTI blacklist service
   *
   * @param redis - Redis client instance
   * @param keyPrefix - Prefix for all Redis keys (e.g., 'pml-admin:')
   * @param defaultTtlSeconds - Default TTL when token expiry not available
   */
  constructor(
    redis: Redis,
    keyPrefix: string,
    defaultTtlSeconds: number = DEFAULT_TTL_SECONDS
  ) {
    this.redis = redis;
    this.keyPrefix = keyPrefix;
    this.defaultTtlSeconds = defaultTtlSeconds;
  }

  /**
   * Add a JTI to the blacklist
   */
  async add(entry: BlacklistInput): Promise<boolean> {
    const { jti, userId, sessionId, reason, tokenExpiry } = entry;

    const fullEntry: BlacklistEntry = {
      jti,
      userId,
      sessionId,
      reason,
      tokenExpiry,
      blacklistedAt: new Date().toISOString(),
    };

    // Calculate TTL based on token expiry or default
    const ttlSeconds = this.calculateTtl(tokenExpiry);

    try {
      const key = this.getJtiKey(jti);
      // Native Redis SETEX: atomic set with expiry
      await this.redis.setex(key, ttlSeconds, JSON.stringify(fullEntry));

      console.log(
        `[JtiBlacklistService] Added JTI ${jti} for user ${userId}, TTL: ${ttlSeconds}s, reason: ${reason}`
      );
      return true;
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to add JTI:', error);
      return false;
    }
  }

  /**
   * Check if a JTI is blacklisted
   */
  async isBlacklisted(jti: string): Promise<boolean> {
    try {
      const key = this.getJtiKey(jti);
      // Native Redis EXISTS: O(1) operation
      const exists = await this.redis.exists(key);
      return exists === 1;
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to check JTI:', error);
      // Fail open to prevent lockout on Redis errors
      // In high-security environments, consider failing closed
      return false;
    }
  }

  /**
   * Get blacklist entry details
   */
  async get(jti: string): Promise<BlacklistEntry | null> {
    try {
      const key = this.getJtiKey(jti);
      const value = await this.redis.get(key);

      if (!value) return null;

      return JSON.parse(value) as BlacklistEntry;
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to get JTI:', error);
      return null;
    }
  }

  /**
   * Remove a JTI from the blacklist
   */
  async remove(jti: string): Promise<boolean> {
    try {
      const key = this.getJtiKey(jti);
      const deleted = await this.redis.del(key);

      if (deleted === 1) {
        console.log(`[JtiBlacklistService] Removed JTI ${jti} from blacklist`);
      }

      return deleted === 1;
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to remove JTI:', error);
      return false;
    }
  }

  /**
   * Blacklist all tokens for a user issued before a timestamp
   */
  async blacklistUserTokensBefore(
    userId: string,
    beforeTimestamp: number,
    ttlSeconds: number = this.defaultTtlSeconds
  ): Promise<boolean> {
    try {
      const key = this.getUserBlacklistKey(userId);
      const entry = {
        userId,
        blacklistTokensBefore: beforeTimestamp,
        createdAt: new Date().toISOString(),
      };

      await this.redis.setex(key, ttlSeconds, JSON.stringify(entry));

      console.log(
        `[JtiBlacklistService] Blacklisted all tokens for user ${userId} before ${new Date(beforeTimestamp * 1000).toISOString()}`
      );
      return true;
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to blacklist user tokens:', error);
      return false;
    }
  }

  /**
   * Check if a user's token is blacklisted by timestamp
   */
  async isUserTokenBlacklisted(
    userId: string,
    tokenIssuedAt: number
  ): Promise<boolean> {
    try {
      const key = this.getUserBlacklistKey(userId);
      const value = await this.redis.get(key);

      if (!value) return false;

      const entry = JSON.parse(value) as { blacklistTokensBefore: number };
      return tokenIssuedAt < entry.blacklistTokensBefore;
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to check user blacklist:', error);
      return false;
    }
  }

  /**
   * Get blacklist statistics
   */
  async getStats(): Promise<BlacklistStats> {
    try {
      // Native Redis KEYS pattern matching
      // Note: Use SCAN in production for large datasets
      const jtiKeys = await this.redis.keys(`${this.keyPrefix}jti-blacklist:*`);
      const userKeys = await this.redis.keys(`${this.keyPrefix}user-blacklist:*`);

      return {
        jtiCount: jtiKeys.length,
        userCount: userKeys.length,
      };
    } catch (error) {
      console.error('[JtiBlacklistService] Failed to get stats:', error);
      return { jtiCount: 0, userCount: 0 };
    }
  }

  // ===========================================================================
  // PRIVATE HELPERS
  // ===========================================================================

  /**
   * Get Redis key for a JTI
   */
  private getJtiKey(jti: string): string {
    return `${this.keyPrefix}jti-blacklist:${jti}`;
  }

  /**
   * Get Redis key for user blacklist
   */
  private getUserBlacklistKey(userId: string): string {
    return `${this.keyPrefix}user-blacklist:${userId}`;
  }

  /**
   * Calculate TTL based on token expiry
   *
   * Uses token expiry if available and not yet expired,
   * otherwise falls back to default TTL.
   */
  private calculateTtl(tokenExpiry?: number): number {
    if (!tokenExpiry) {
      return this.defaultTtlSeconds;
    }

    const now = Math.floor(Date.now() / 1000);
    const remainingSeconds = tokenExpiry - now;

    // Token already expired or about to expire
    if (remainingSeconds <= 0) {
      return this.defaultTtlSeconds;
    }

    // Add buffer for clock skew between servers
    return remainingSeconds + CLOCK_SKEW_BUFFER_SECONDS;
  }
}
