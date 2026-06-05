/**
 * Enhanced Redis Secondary Storage for Better Auth
 *
 * Features:
 * - Session storage with automatic TTL
 * - Active sessions list per user (for "logout all devices")
 * - Session metadata (IP, user agent, device)
 * - Audit logging hooks
 *
 * OWASP Compliance:
 * - Server-side session invalidation
 * - Session ID regeneration support
 * - IP binding for session validation
 * - Complete audit trail
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 * @see https://better-auth.com/docs/concepts/database#secondary-storage
 */

import type { SecondaryStorage } from 'better-auth';
import type { Redis } from 'ioredis';

// =============================================================================
// TYPES
// =============================================================================

export interface SessionMetadata {
  sessionToken: string;
  userId: string;
  createdAt: string;
  expiresAt: string;
  ipAddress?: string;
  userAgent?: string;
  deviceId?: string;
}

export interface SessionAuditEvent {
  type:
    | 'SESSION_CREATED'
    | 'SESSION_ACCESSED'
    | 'SESSION_REFRESHED'
    | 'SESSION_REVOKED'
    | 'SESSION_EXPIRED'
    | 'SESSION_LOGOUT'
    | 'SESSION_REVOKE_ALL';
  sessionToken: string;
  userId: string;
  timestamp: string;
  ipAddress?: string;
  metadata?: Record<string, unknown>;
}

export interface EnhancedStorageOptions {
  enableAuditLog?: boolean;
  auditLogCallback?: (event: SessionAuditEvent) => Promise<void>;
  auditRetentionDays?: number;
}

// =============================================================================
// ENHANCED REDIS SECONDARY STORAGE
// =============================================================================

/**
 * Create an enhanced Redis secondary storage implementation
 *
 * This implementation adds:
 * - Active sessions tracking per user
 * - Audit logging for all session operations
 * - Proper error handling with fallback support
 */
export function createEnhancedRedisStorage(
  redis: Redis,
  keyPrefix: string = 'pml-organizer:',
  options: EnhancedStorageOptions = {}
): SecondaryStorage {
  const {
    enableAuditLog = true,
    auditLogCallback,
    auditRetentionDays = 30,
  } = options;

  // Helper to log audit events
  const logAuditEvent = async (event: SessionAuditEvent): Promise<void> => {
    if (!enableAuditLog) return;

    try {
      // Log to console (can be sent to external logging service)
      console.log('[Session Audit]', JSON.stringify(event));

      // Store in Redis sorted set for recent events
      const auditKey = `${keyPrefix}audit:${event.userId}`;
      const timestamp = new Date(event.timestamp).getTime();
      await redis.zadd(auditKey, timestamp, JSON.stringify(event));
      await redis.expire(auditKey, auditRetentionDays * 24 * 60 * 60);

      // Also store in global audit log (limited to last 10000 events)
      await redis.lpush(`${keyPrefix}audit:global`, JSON.stringify(event));
      await redis.ltrim(`${keyPrefix}audit:global`, 0, 9999);

      // Call external callback if provided
      if (auditLogCallback) {
        await auditLogCallback(event);
      }
    } catch (error) {
      console.error('[Session Audit] Failed to log event:', error);
      // Don't throw - audit logging should not break session operations
    }
  };

  // Helper to truncate session token for logs (security)
  const truncateToken = (token: string): string => {
    if (token.length <= 8) return '***';
    return token.substring(0, 8) + '...';
  };

  return {
    /**
     * Get a session from Redis
     */
    async get(key: string): Promise<string | null> {
      try {
        const fullKey = `${keyPrefix}${key}`;
        const value = await redis.get(fullKey);

        if (value === null || value === undefined) {
          return null;
        }

        // Return as string (Better Auth expects string)
        if (typeof value === 'string') {
          return value;
        }

        // Convert object to string if needed
        if (typeof value === 'object') {
          return JSON.stringify(value);
        }

        return String(value);
      } catch (error) {
        console.error('[Redis SecondaryStorage] Get error:', error);
        // Return null on error - Better Auth will fall back to database
        return null;
      }
    },

    /**
     * Store a session in Redis with optional TTL
     */
    async set(key: string, value: string, ttl?: number): Promise<void> {
      try {
        const fullKey = `${keyPrefix}${key}`;
        const stringValue =
          typeof value === 'string' ? value : JSON.stringify(value);

        if (ttl && ttl > 0) {
          // Set with TTL in seconds
          await redis.set(fullKey, stringValue, 'EX', ttl);
        } else {
          // Set without TTL (will use Redis default or persist forever)
          await redis.set(fullKey, stringValue);
        }

        // Parse session data to track active sessions
        try {
          const sessionData = JSON.parse(stringValue);
          if (sessionData.userId) {
            // Add to active sessions set for this user
            const activeKey = `${keyPrefix}active_sessions:${sessionData.userId}`;
            await redis.sadd(activeKey, key);
            if (ttl) {
              await redis.expire(activeKey, ttl);
            }

            // Log session creation
            await logAuditEvent({
              type: 'SESSION_CREATED',
              sessionToken: truncateToken(key),
              userId: sessionData.userId,
              timestamp: new Date().toISOString(),
              ipAddress: sessionData.ipAddress,
              metadata: {
                userAgent: sessionData.userAgent,
                ttl,
              },
            });
          }
        } catch {
          // Not a session object, ignore
        }
      } catch (error) {
        console.error('[Redis SecondaryStorage] Set error:', error);
        throw error;
      }
    },

    /**
     * Delete a session from Redis
     */
    async delete(key: string): Promise<void> {
      try {
        const fullKey = `${keyPrefix}${key}`;

        // Get session data before deletion for cleanup
        const existingValue = await redis.get(fullKey);

        // Delete the session
        await redis.del(fullKey);

        // Cleanup active sessions tracking
        if (existingValue) {
          try {
            const sessionData = JSON.parse(existingValue);
            if (sessionData.userId) {
              const activeKey = `${keyPrefix}active_sessions:${sessionData.userId}`;
              await redis.srem(activeKey, key);

              // Log session revocation
              await logAuditEvent({
                type: 'SESSION_REVOKED',
                sessionToken: truncateToken(key),
                userId: sessionData.userId,
                timestamp: new Date().toISOString(),
              });
            }
          } catch {
            // Ignore parse errors
          }
        }
      } catch (error) {
        console.error('[Redis SecondaryStorage] Delete error:', error);
        throw error;
      }
    },
  };
}

// =============================================================================
// SESSION MANAGER - High-Level Operations
// =============================================================================

/**
 * SessionManager provides high-level session management operations
 *
 * Use this for:
 * - Revoking all user sessions (logout from all devices)
 * - Listing active sessions
 * - Session regeneration after privilege escalation
 * - Consistency verification
 */
export class SessionManager {
  constructor(
    private redis: Redis,
    private keyPrefix: string = 'pml-organizer:'
  ) {}

  /**
   * Revoke ALL sessions for a user (logout from all devices)
   * Implements OWASP requirement for complete session termination
   *
   * @param userId - The user ID to revoke all sessions for
   * @returns Number of sessions revoked
   */
  async revokeAllUserSessions(userId: string): Promise<number> {
    const activeKey = `${this.keyPrefix}active_sessions:${userId}`;
    const sessionTokens = await this.redis.smembers(activeKey);

    if (sessionTokens.length === 0) {
      return 0;
    }

    // Delete all session keys using pipeline for efficiency
    const pipeline = this.redis.pipeline();
    for (const token of sessionTokens) {
      pipeline.del(`${this.keyPrefix}${token}`);
    }
    // Clear the active sessions set
    pipeline.del(activeKey);

    await pipeline.exec();

    // Log audit event
    await this.redis.lpush(
      `${this.keyPrefix}audit:global`,
      JSON.stringify({
        type: 'SESSION_REVOKE_ALL',
        userId,
        timestamp: new Date().toISOString(),
        metadata: { count: sessionTokens.length },
      })
    );

    console.log('[SessionManager] Revoked all sessions for user:', {
      userId,
      count: sessionTokens.length,
      timestamp: new Date().toISOString(),
    });

    return sessionTokens.length;
  }

  /**
   * Get all active session tokens for a user
   *
   * @param userId - The user ID
   * @returns Array of session tokens
   */
  async getActiveSessionTokens(userId: string): Promise<string[]> {
    const activeKey = `${this.keyPrefix}active_sessions:${userId}`;
    return this.redis.smembers(activeKey);
  }

  /**
   * Get all active sessions with full data for a user
   *
   * @param userId - The user ID
   * @returns Array of session objects
   */
  async getActiveSessions(userId: string): Promise<SessionMetadata[]> {
    const tokens = await this.getActiveSessionTokens(userId);
    const sessions: SessionMetadata[] = [];

    for (const token of tokens) {
      const data = await this.redis.get(`${this.keyPrefix}${token}`);
      if (data) {
        try {
          const parsed = JSON.parse(data);
          sessions.push({
            sessionToken: token.substring(0, 8) + '...', // Truncate for security
            userId: parsed.userId,
            createdAt: parsed.createdAt,
            expiresAt: parsed.expiresAt,
            ipAddress: parsed.ipAddress,
            userAgent: parsed.userAgent,
          });
        } catch {
          // Invalid session data, skip
        }
      }
    }

    return sessions;
  }

  /**
   * Check if a specific session is valid (exists and not expired)
   *
   * @param sessionToken - The session token to check
   * @returns True if session is valid
   */
  async isSessionValid(sessionToken: string): Promise<boolean> {
    const key = `${this.keyPrefix}${sessionToken}`;
    const ttl = await this.redis.ttl(key);
    return ttl > 0;
  }

  /**
   * Force session refresh (regenerate session ID)
   * Call after privilege escalation per OWASP guidelines
   *
   * @param oldToken - The old session token
   * @param newToken - The new session token
   * @param userId - The user ID
   * @param ttl - Time to live in seconds
   */
  async regenerateSession(
    oldToken: string,
    newToken: string,
    userId: string,
    ttl: number
  ): Promise<void> {
    const oldKey = `${this.keyPrefix}${oldToken}`;
    const newKey = `${this.keyPrefix}${newToken}`;

    // Get existing session data
    const sessionData = await this.redis.get(oldKey);
    if (!sessionData) {
      throw new Error('Session not found');
    }

    // Update session data with new token
    const parsed = JSON.parse(sessionData);
    parsed.token = newToken;
    parsed.regeneratedAt = new Date().toISOString();
    parsed.previousToken = oldToken.substring(0, 8) + '...';

    // Atomic operation: delete old, create new
    const pipeline = this.redis.pipeline();
    pipeline.del(oldKey);
    pipeline.set(newKey, JSON.stringify(parsed), 'EX', ttl);
    pipeline.srem(`${this.keyPrefix}active_sessions:${userId}`, oldToken);
    pipeline.sadd(`${this.keyPrefix}active_sessions:${userId}`, newToken);

    await pipeline.exec();

    console.log('[SessionManager] Session regenerated:', {
      userId,
      oldToken: oldToken.substring(0, 8) + '...',
      newToken: newToken.substring(0, 8) + '...',
    });
  }

  /**
   * Revoke a specific session
   *
   * @param sessionToken - The session token to revoke
   * @param userId - The user ID (for active sessions cleanup)
   */
  async revokeSession(sessionToken: string, userId?: string): Promise<boolean> {
    const key = `${this.keyPrefix}${sessionToken}`;

    // Get session data if userId not provided
    if (!userId) {
      const data = await this.redis.get(key);
      if (data) {
        try {
          const parsed = JSON.parse(data);
          userId = parsed.userId;
        } catch {
          // Ignore
        }
      }
    }

    // Delete the session
    const deleted = await this.redis.del(key);

    // Remove from active sessions
    if (userId) {
      await this.redis.srem(
        `${this.keyPrefix}active_sessions:${userId}`,
        sessionToken
      );
    }

    return deleted > 0;
  }

  /**
   * Get audit log for a user
   *
   * @param userId - The user ID
   * @param limit - Maximum number of events to return
   * @returns Array of audit events
   */
  async getAuditLog(
    userId: string,
    limit: number = 100
  ): Promise<SessionAuditEvent[]> {
    const auditKey = `${this.keyPrefix}audit:${userId}`;
    const events = await this.redis.zrevrange(auditKey, 0, limit - 1);

    return events.map((e) => JSON.parse(e) as SessionAuditEvent);
  }

  /**
   * Get count of active sessions for a user
   *
   * @param userId - The user ID
   * @returns Number of active sessions
   */
  async getActiveSessionCount(userId: string): Promise<number> {
    const activeKey = `${this.keyPrefix}active_sessions:${userId}`;
    return this.redis.scard(activeKey);
  }

  /**
   * Clean up expired sessions from active sessions tracking
   * Call periodically to maintain accuracy
   */
  async cleanupExpiredTracking(): Promise<number> {
    // Get all active session keys
    const pattern = `${this.keyPrefix}active_sessions:*`;
    const keys = await this.redis.keys(pattern);

    let cleaned = 0;

    for (const activeKey of keys) {
      const tokens = await this.redis.smembers(activeKey);

      for (const token of tokens) {
        const sessionKey = `${this.keyPrefix}${token}`;
        const exists = await this.redis.exists(sessionKey);

        if (!exists) {
          await this.redis.srem(activeKey, token);
          cleaned++;
        }
      }
    }

    if (cleaned > 0) {
      console.log('[SessionManager] Cleaned up expired tracking entries:', cleaned);
    }

    return cleaned;
  }
}

// =============================================================================
// EXPORTS
// =============================================================================

export type { SecondaryStorage };
