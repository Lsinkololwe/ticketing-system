/**
 * Session Cleanup Service
 *
 * Handles maintenance tasks for dual session storage:
 * - Clean up expired sessions from MongoDB (Redis uses TTL)
 * - Sync MongoDB sessions to Redis (cache warming)
 * - Verify consistency between stores
 *
 * Usage:
 * - Call cleanupExpiredSessions() periodically (e.g., cron job)
 * - Call syncMongoToRedis() after Redis restart
 * - Call verifyConsistency() for auditing
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 */

import type { Db } from 'mongodb';
import type { Redis } from 'ioredis';

// =============================================================================
// TYPES
// =============================================================================

export interface CleanupResult {
  deletedFromMongo: number;
  cleanedFromRedisTracking: number;
  timestamp: string;
}

export interface SyncResult {
  synced: number;
  skipped: number;
  timestamp: string;
}

export interface ConsistencyReport {
  mongoOnly: string[];
  redisOnly: string[];
  consistent: number;
  timestamp: string;
}

// =============================================================================
// SESSION CLEANUP SERVICE
// =============================================================================

export class SessionCleanupService {
  constructor(
    private mongodb: Db,
    private redis: Redis,
    private keyPrefix: string = 'pml-organizer:'
  ) {}

  /**
   * Clean up expired sessions from MongoDB
   *
   * Redis handles its own expiration via TTL, but MongoDB needs manual cleanup.
   * Call this periodically (e.g., every hour via cron).
   */
  async cleanupExpiredSessions(): Promise<CleanupResult> {
    const now = new Date();
    const result: CleanupResult = {
      deletedFromMongo: 0,
      cleanedFromRedisTracking: 0,
      timestamp: now.toISOString(),
    };

    // 1. Find expired sessions before deleting (to clean up Redis tracking)
    const expiredSessions = await this.mongodb
      .collection('session')
      .find({ expiresAt: { $lt: now } })
      .project({ token: 1, userId: 1 })
      .toArray();

    // 2. Delete expired sessions from MongoDB
    const deleteResult = await this.mongodb.collection('session').deleteMany({
      expiresAt: { $lt: now },
    });
    result.deletedFromMongo = deleteResult.deletedCount;

    // 3. Clean up Redis tracking for expired sessions
    if (expiredSessions.length > 0) {
      const pipeline = this.redis.pipeline();

      for (const session of expiredSessions) {
        if (session.userId && session.token) {
          pipeline.srem(
            `${this.keyPrefix}active_sessions:${session.userId}`,
            session.token
          );
          result.cleanedFromRedisTracking++;
        }
      }

      await pipeline.exec();
    }

    console.log('[SessionCleanup] Expired sessions cleaned:', result);
    return result;
  }

  /**
   * Sync MongoDB sessions to Redis (cache warming)
   *
   * Use after Redis restart or for initial cache population.
   * Only syncs non-expired sessions.
   */
  async syncMongoToRedis(): Promise<SyncResult> {
    const now = new Date();
    const result: SyncResult = {
      synced: 0,
      skipped: 0,
      timestamp: now.toISOString(),
    };

    // Find all active sessions
    const sessions = await this.mongodb
      .collection('session')
      .find({ expiresAt: { $gt: now } })
      .toArray();

    const pipeline = this.redis.pipeline();

    for (const session of sessions) {
      const ttl = Math.floor(
        (new Date(session.expiresAt).getTime() - now.getTime()) / 1000
      );

      if (ttl > 0) {
        // Store session in Redis
        const key = `${this.keyPrefix}${session.token}`;
        pipeline.set(key, JSON.stringify(session), 'EX', ttl);

        // Add to active sessions tracking
        pipeline.sadd(
          `${this.keyPrefix}active_sessions:${session.userId}`,
          session.token
        );

        result.synced++;
      } else {
        result.skipped++;
      }
    }

    await pipeline.exec();

    console.log('[SessionCleanup] MongoDB to Redis sync complete:', result);
    return result;
  }

  /**
   * Verify consistency between MongoDB and Redis
   *
   * Returns sessions that exist in one store but not the other.
   * Useful for auditing and debugging sync issues.
   */
  async verifyConsistency(): Promise<ConsistencyReport> {
    const now = new Date();
    const report: ConsistencyReport = {
      mongoOnly: [],
      redisOnly: [],
      consistent: 0,
      timestamp: now.toISOString(),
    };

    // Get all active MongoDB sessions
    const mongoSessions = await this.mongodb
      .collection('session')
      .find({ expiresAt: { $gt: now } })
      .project({ token: 1 })
      .toArray();

    const mongoTokens = new Set(mongoSessions.map((s) => s.token));

    // Get all Redis session keys (excluding tracking and audit keys)
    const redisKeys = await this.redis.keys(`${this.keyPrefix}*`);
    const redisTokens = new Set(
      redisKeys
        .filter(
          (k) =>
            !k.includes('active_sessions') &&
            !k.includes('audit') &&
            !k.includes('tokens') &&
            !k.includes('blacklist')
        )
        .map((k) => k.replace(this.keyPrefix, ''))
    );

    // Check MongoDB sessions
    Array.from(mongoTokens).forEach((token) => {
      if (redisTokens.has(token)) {
        report.consistent++;
      } else {
        report.mongoOnly.push(token.slice(0, 8) + '...');
      }
    });

    // Check Redis sessions
    Array.from(redisTokens).forEach((token) => {
      if (!mongoTokens.has(token)) {
        report.redisOnly.push(token.slice(0, 8) + '...');
      }
    });

    console.log('[SessionCleanup] Consistency report:', {
      consistent: report.consistent,
      mongoOnly: report.mongoOnly.length,
      redisOnly: report.redisOnly.length,
    });

    return report;
  }

  /**
   * Repair inconsistencies between stores
   *
   * - Sessions in MongoDB but not Redis → Sync to Redis
   * - Sessions in Redis but not MongoDB → Delete from Redis
   */
  async repairInconsistencies(): Promise<{
    syncedToRedis: number;
    deletedFromRedis: number;
  }> {
    const consistency = await this.verifyConsistency();
    const now = new Date();
    let syncedToRedis = 0;
    let deletedFromRedis = 0;

    // Sync MongoDB-only sessions to Redis
    if (consistency.mongoOnly.length > 0) {
      // Re-fetch full session data for MongoDB-only tokens
      const mongoSessions = await this.mongodb
        .collection('session')
        .find({ expiresAt: { $gt: now } })
        .toArray();

      const pipeline = this.redis.pipeline();

      for (const session of mongoSessions) {
        const ttl = Math.floor(
          (new Date(session.expiresAt).getTime() - now.getTime()) / 1000
        );

        if (ttl > 0) {
          const key = `${this.keyPrefix}${session.token}`;
          const exists = await this.redis.exists(key);

          if (!exists) {
            pipeline.set(key, JSON.stringify(session), 'EX', ttl);
            pipeline.sadd(
              `${this.keyPrefix}active_sessions:${session.userId}`,
              session.token
            );
            syncedToRedis++;
          }
        }
      }

      await pipeline.exec();
    }

    // Delete Redis-only sessions (orphaned)
    if (consistency.redisOnly.length > 0) {
      const pipeline = this.redis.pipeline();

      for (const tokenPrefix of consistency.redisOnly) {
        // Find the full key
        const keys = await this.redis.keys(
          `${this.keyPrefix}${tokenPrefix.replace('...', '')}*`
        );

        for (const key of keys) {
          if (
            !key.includes('active_sessions') &&
            !key.includes('audit') &&
            !key.includes('tokens')
          ) {
            pipeline.del(key);
            deletedFromRedis++;
          }
        }
      }

      await pipeline.exec();
    }

    console.log('[SessionCleanup] Repair complete:', {
      syncedToRedis,
      deletedFromRedis,
    });

    return { syncedToRedis, deletedFromRedis };
  }

  /**
   * Get session statistics
   */
  async getStats(): Promise<{
    mongoActiveSessions: number;
    redisKeys: number;
    activeUsers: number;
  }> {
    const now = new Date();

    // Count active MongoDB sessions
    const mongoActiveSessions = await this.mongodb
      .collection('session')
      .countDocuments({ expiresAt: { $gt: now } });

    // Count Redis session keys
    const redisKeys = await this.redis.keys(`${this.keyPrefix}*`);
    const sessionKeys = redisKeys.filter(
      (k) =>
        !k.includes('active_sessions') &&
        !k.includes('audit') &&
        !k.includes('tokens') &&
        !k.includes('blacklist')
    );

    // Count active users (users with at least one session)
    const activeUsersKeys = redisKeys.filter((k) =>
      k.includes('active_sessions')
    );

    return {
      mongoActiveSessions,
      redisKeys: sessionKeys.length,
      activeUsers: activeUsersKeys.length,
    };
  }
}

// =============================================================================
// FACTORY FUNCTION
// =============================================================================

/**
 * Create a SessionCleanupService instance
 */
export function createSessionCleanupService(
  mongodb: Db,
  redis: Redis,
  keyPrefix: string = 'pml-organizer:'
): SessionCleanupService {
  return new SessionCleanupService(mongodb, redis, keyPrefix);
}
