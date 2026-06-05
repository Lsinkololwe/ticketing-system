/**
 * Token Blacklist Service
 *
 * Industry-standard approach for immediate token revocation at scale.
 * Uses Redis with O(1) EXISTS operations for blacklist checks.
 *
 * Key Features:
 * - O(1) blacklist lookup (not O(N) session scan)
 * - Automatic TTL cleanup (no manual deletion needed)
 * - User session indexing for efficient backchannel logout
 * - Minimal memory footprint (~50 bytes per blacklisted token)
 *
 * @see OWASP Session Management Cheat Sheet
 */

import { Redis } from 'ioredis';
import { decodeJwt } from 'jose';

// =============================================================================
// CONFIGURATION
// =============================================================================

const REDIS_HOST = process.env.REDIS_HOST;
const REDIS_PORT = parseInt(process.env.REDIS_PORT as string, 10);
const REDIS_PASSWORD = process.env.REDIS_PASSWORD;

// =============================================================================
// SHARED KEY PREFIXES (Must match backend services)
// =============================================================================
// These prefixes are used by both frontend and backend (Java) services
// to ensure consistent Redis key patterns across the system.

/**
 * Token blacklist prefix - SHARED across all services
 * Used by: organization-admin, api-gateway, identity-service, catalog-service, booking-service
 */
const BLACKLIST_PREFIX = 'pml:blacklist:';

// Internal prefixes (organizer app only)
const USER_SESSIONS_PREFIX = 'pml-organizer:user:';       // User -> Sessions index
const SESSION_PREFIX = 'pml-organizer:session:';          // Session storage
const JTI_SESSION_PREFIX = 'pml-organizer:jti:';          // JTI -> Session mapping

/**
 * Token blacklist TTL: 1 hour
 *
 * Rationale:
 * - Access tokens typically expire in 5-15 minutes
 * - 1-hour TTL provides buffer for:
 *   - Tokens refreshed just before logout
 *   - Clock skew between services
 * - Redis auto-deletes after TTL (no cleanup needed)
 */
const TOKEN_BLACKLIST_TTL_SECONDS = 60 * 60; // 1 hour


// Session TTL (8 hours = match Better Auth session lifetime)
// Used for user session index TTL to ensure automatic cleanup
const SESSION_TTL_SECONDS = 8 * 60 * 60;

// =============================================================================
// REDIS CLIENT (Singleton)
// =============================================================================

let redisClient: Redis | null = null;

function getRedis(): Redis {
  if (!redisClient) {
    redisClient = new Redis({
      host: REDIS_HOST,
      port: REDIS_PORT,
      password: REDIS_PASSWORD,
      maxRetriesPerRequest: 3,
      retryStrategy: (times) => {
        if (times > 3) return null;
        return Math.min(times * 100, 3000);
      },
      connectTimeout: 5000,
      commandTimeout: 2000,
      lazyConnect: true,
      enableOfflineQueue: true,
    });

    redisClient.on('error', (err) => {
      console.error('[TokenBlacklist] Redis error:', err.message);
    });
  }

  return redisClient;
}

// =============================================================================
// TOKEN BLACKLIST OPERATIONS
// =============================================================================

/**
 * Blacklist entry metadata stored in Redis
 */
interface BlacklistEntry {
  jti: string;
  userId?: string;
  revokedAt: number;
  reason: 'logout' | 'session_revoked' | 'password_change' | 'admin_action';
}

/**
 * Blacklist a token by its JTI (JWT ID)
 *
 * Stores the JTI in Redis with a fixed 2-hour TTL.
 * Backend services (API Gateway, microservices) check this blacklist
 * before processing requests.
 *
 * Key format: pml:blacklist:{jti}
 * TTL: 2 hours (auto-deleted by Redis)
 *
 * @param jti - JWT ID from the token's jti claim
 * @param options - Optional metadata (userId, reason)
 * @returns true if blacklisted successfully
 */
export async function blacklistToken(
  jti: string,
  options?: { userId?: string; reason?: BlacklistEntry['reason'] }
): Promise<boolean> {
  const client = getRedis();

  try {
    const entry: BlacklistEntry = {
      jti,
      userId: options?.userId,
      revokedAt: Math.floor(Date.now() / 1000),
      reason: options?.reason || 'logout',
    };

    // Store with fixed 2-hour TTL
    // Backend services use EXISTS check for O(1) performance
    await client.set(
      `${BLACKLIST_PREFIX}${jti}`,
      JSON.stringify(entry),
      'EX',
      TOKEN_BLACKLIST_TTL_SECONDS
    );

    console.log(`[TokenBlacklist] Blacklisted token ${maskJti(jti)} for ${TOKEN_BLACKLIST_TTL_SECONDS}s (1 hour)`);
    return true;
  } catch (error) {
    console.error('[TokenBlacklist] Failed to blacklist token:', error);
    return false;
  }
}


/**
 * Check if a token is blacklisted
 *
 * @param jti - JWT ID to check
 * @returns true if token is blacklisted
 */
export async function isTokenBlacklisted(jti: string): Promise<boolean> {
  const client = getRedis();

  try {
    // EXISTS is O(1) operation
    const exists = await client.exists(`${BLACKLIST_PREFIX}${jti}`);
    return exists === 1;
  } catch (error) {
    console.error('[TokenBlacklist] Failed to check blacklist:', error);
    // Fail-open: if Redis is unavailable, allow the request
    // Token signature validation will still protect the API
    return false;
  }
}

/**
 * Blacklist a token from its JWT string
 *
 * Extracts jti and sub (userId) claims from the token and blacklists it.
 * Uses fixed 2-hour TTL regardless of token's original expiration.
 *
 * @param token - Raw JWT string
 * @param reason - Reason for blacklisting (default: 'logout')
 * @returns true if blacklisted successfully
 */
export async function blacklistTokenFromJwt(
  token: string,
  reason: BlacklistEntry['reason'] = 'logout'
): Promise<boolean> {
  try {
    const payload = decodeJwt(token);
    const jti = payload.jti;
    const userId = payload.sub; // Subject = user ID

    if (!jti) {
      console.warn('[TokenBlacklist] Token has no jti claim, cannot blacklist');
      return false;
    }

    return blacklistToken(jti, { userId, reason });
  } catch (error) {
    console.error('[TokenBlacklist] Failed to decode token:', error);
    return false;
  }
}

// =============================================================================
// USER SESSION INDEX OPERATIONS (For Backchannel Logout)
// =============================================================================

/**
 * Add a session to the user's session index
 *
 * This enables efficient O(N) backchannel logout where N = sessions per user
 * (typically 1-3) instead of O(M) where M = all sessions in system.
 *
 * IMPORTANT: Sets TTL to match session lifetime (8 hours) to ensure
 * automatic cleanup. No data is stored indefinitely.
 *
 * @param userId - Keycloak subject ID
 * @param sessionId - Better Auth session ID
 */
export async function addSessionToUserIndex(userId: string, sessionId: string): Promise<void> {
  const client = getRedis();
  const key = `${USER_SESSIONS_PREFIX}${userId}:sessions`;

  try {
    // SADD is O(1) per element
    await client.sadd(key, sessionId);

    // Set/refresh TTL to match session lifetime
    // This ensures the index is cleaned up even if:
    // - User doesn't explicitly logout
    // - Session expires naturally
    // - Application crashes during logout
    await client.expire(key, SESSION_TTL_SECONDS);

    console.log(`[TokenBlacklist] Added session to user index with ${SESSION_TTL_SECONDS}s TTL: ${maskJti(userId)}`);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to add session to user index:', error);
  }
}

/**
 * Remove a session from the user's session index
 *
 * @param userId - Keycloak subject ID
 * @param sessionId - Better Auth session ID
 */
export async function removeSessionFromUserIndex(userId: string, sessionId: string): Promise<void> {
  const client = getRedis();

  try {
    // SREM is O(1) per element
    await client.srem(`${USER_SESSIONS_PREFIX}${userId}:sessions`, sessionId);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to remove session from user index:', error);
  }
}

/**
 * Get all session IDs for a user
 *
 * @param userId - Keycloak subject ID
 * @returns Array of session IDs
 */
export async function getUserSessionIds(userId: string): Promise<string[]> {
  const client = getRedis();

  try {
    // SMEMBERS is O(N) where N = number of sessions for this user
    return await client.smembers(`${USER_SESSIONS_PREFIX}${userId}:sessions`);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to get user sessions:', error);
    return [];
  }
}

/**
 * Store JTI to Session mapping
 *
 * Enables looking up session by token JTI for blacklisting.
 *
 * @param jti - JWT ID from access token
 * @param sessionId - Better Auth session ID
 * @param ttl - Time to live in seconds (should match token lifetime)
 */
export async function mapJtiToSession(jti: string, sessionId: string, ttl: number): Promise<void> {
  const client = getRedis();

  try {
    await client.set(`${JTI_SESSION_PREFIX}${jti}`, sessionId, 'EX', ttl);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to map JTI to session:', error);
  }
}

/**
 * Get session ID from JTI
 *
 * @param jti - JWT ID
 * @returns Session ID or null
 */
export async function getSessionByJti(jti: string): Promise<string | null> {
  const client = getRedis();

  try {
    return await client.get(`${JTI_SESSION_PREFIX}${jti}`);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to get session by JTI:', error);
    return null;
  }
}

// =============================================================================
// COMPREHENSIVE LOGOUT OPERATIONS
// =============================================================================

interface SessionData {
  userId?: string;
  user?: { id?: string };
  accessToken?: string;
  token?: string;
}

/**
 * Invalidate all sessions for a user and blacklist their tokens
 *
 * Used by backchannel logout for immediate SSO session termination.
 *
 * @param userId - Keycloak subject ID
 * @returns Number of sessions invalidated
 */
export async function invalidateUserSessionsWithBlacklist(userId: string): Promise<number> {
  const client = getRedis();
  let invalidatedCount = 0;

  try {
    // 1. Get all session IDs from user index (O(N) where N = user's sessions)
    const sessionIds = await getUserSessionIds(userId);

    if (sessionIds.length === 0) {
      // Fallback: Scan for sessions (only if index is empty)
      // This handles sessions created before indexing was implemented
      console.log(`[TokenBlacklist] No indexed sessions for user ${maskJti(userId)}, falling back to scan`);
      return await invalidateUserSessionsWithScan(userId);
    }

    // 2. Process each session
    for (const sessionId of sessionIds) {
      try {
        // Get session data to extract access token
        const sessionKey = `${SESSION_PREFIX}${sessionId}`;
        const sessionData = await client.get(sessionKey);

        if (sessionData) {
          const session: SessionData = JSON.parse(sessionData);

          // Extract and blacklist the access token
          const accessToken = session.accessToken || session.token;
          if (accessToken) {
            await blacklistTokenFromJwt(accessToken);
          }

          // Delete the session
          await client.del(sessionKey);
          invalidatedCount++;
        }
      } catch (parseError) {
        console.warn(`[TokenBlacklist] Failed to process session ${sessionId}:`, parseError);
      }
    }

    // 3. Clean up user session index
    await client.del(`${USER_SESSIONS_PREFIX}${userId}:sessions`);

    console.log(`[TokenBlacklist] Invalidated ${invalidatedCount} sessions for user ${maskJti(userId)}`);
    return invalidatedCount;
  } catch (error) {
    console.error('[TokenBlacklist] Failed to invalidate user sessions:', error);
    return 0;
  }
}

/**
 * Fallback: Scan for user sessions (used when index is empty)
 *
 * Uses SCAN instead of KEYS for production safety.
 */
async function invalidateUserSessionsWithScan(userId: string): Promise<number> {
  const client = getRedis();
  let invalidatedCount = 0;
  let cursor = '0';

  try {
    do {
      // SCAN is safe for production (non-blocking)
      const [nextCursor, keys] = await client.scan(
        cursor,
        'MATCH',
        `${SESSION_PREFIX}*`,
        'COUNT',
        100
      );
      cursor = nextCursor;

      for (const key of keys) {
        try {
          const sessionData = await client.get(key);
          if (sessionData) {
            const session: SessionData = JSON.parse(sessionData);

            // Check if session belongs to user
            if (session.userId === userId || session.user?.id === userId) {
              // Blacklist access token
              const accessToken = session.accessToken || session.token;
              if (accessToken) {
                await blacklistTokenFromJwt(accessToken);
              }

              // Delete session
              await client.del(key);
              invalidatedCount++;
            }
          }
        } catch {
          // Continue on parse errors
        }
      }
    } while (cursor !== '0');

    return invalidatedCount;
  } catch (error) {
    console.error('[TokenBlacklist] Scan failed:', error);
    return 0;
  }
}

/**
 * Invalidate a single session and blacklist its token
 *
 * Used for RP-initiated logout (user clicks logout).
 *
 * @param sessionId - Better Auth session ID
 * @param userId - Optional user ID for index cleanup
 * @returns true if session was invalidated
 */
export async function invalidateSessionWithBlacklist(
  sessionId: string,
  userId?: string
): Promise<boolean> {
  const client = getRedis();

  try {
    const sessionKey = `${SESSION_PREFIX}${sessionId}`;
    const sessionData = await client.get(sessionKey);

    if (!sessionData) {
      return false;
    }

    const session: SessionData = JSON.parse(sessionData);

    // Blacklist access token
    const accessToken = session.accessToken || session.token;
    if (accessToken) {
      await blacklistTokenFromJwt(accessToken);
    }

    // Delete session
    await client.del(sessionKey);

    // Clean up user index
    const userIdToUse = userId || session.userId || session.user?.id;
    if (userIdToUse) {
      await removeSessionFromUserIndex(userIdToUse, sessionId);
    }

    console.log(`[TokenBlacklist] Invalidated session ${maskJti(sessionId)}`);
    return true;
  } catch (error) {
    console.error('[TokenBlacklist] Failed to invalidate session:', error);
    return false;
  }
}

// =============================================================================
// USER REVOCATION (Defense-in-Depth)
// =============================================================================

/**
 * Key prefix for user revocation entries
 * SHARED across all services - must match backend (SessionBlacklistService.java)
 * These entries persist after logout to prevent token replay attacks
 */
const USER_REVOCATION_PREFIX = 'pml:revoked:';

/**
 * Revocation TTL - how long to remember that a user logged out
 * Set to 30 minutes (longer than typical access token lifetime)
 */
const USER_REVOCATION_TTL_SECONDS = 30 * 60;

/**
 * Create a user revocation entry
 *
 * This is a defense-in-depth measure. Even if token blacklisting fails,
 * this entry can be checked by the API Gateway to reject requests from
 * users who have recently logged out.
 *
 * The entry contains the logout timestamp and expires after 30 minutes.
 *
 * @param userId - User ID (Keycloak subject)
 * @returns true if revocation entry was created
 */
export async function revokeUserSessions(userId: string): Promise<boolean> {
  const client = getRedis();
  const key = `${USER_REVOCATION_PREFIX}${userId}`;

  try {
    const revokedAt = Math.floor(Date.now() / 1000);

    // Store revocation timestamp with TTL
    await client.set(key, JSON.stringify({
      revokedAt,
      reason: 'user_logout',
    }), 'EX', USER_REVOCATION_TTL_SECONDS);

    console.log(`[TokenBlacklist] Created user revocation entry for ${maskJti(userId)} (TTL: ${USER_REVOCATION_TTL_SECONDS}s)`);
    return true;
  } catch (error) {
    console.error('[TokenBlacklist] Failed to create user revocation:', error);
    return false;
  }
}

/**
 * Check if a user has been revoked (recently logged out)
 *
 * @param userId - User ID to check
 * @returns Revocation info if user was revoked, null otherwise
 */
export async function isUserRevoked(userId: string): Promise<{ revokedAt: number; reason: string } | null> {
  const client = getRedis();
  const key = `${USER_REVOCATION_PREFIX}${userId}`;

  try {
    const data = await client.get(key);
    if (data) {
      return JSON.parse(data);
    }
    return null;
  } catch (error) {
    console.error('[TokenBlacklist] Failed to check user revocation:', error);
    return null;
  }
}

/**
 * Clear user revocation entry (e.g., when user logs back in)
 *
 * @param userId - User ID
 */
export async function clearUserRevocation(userId: string): Promise<void> {
  const client = getRedis();
  const key = `${USER_REVOCATION_PREFIX}${userId}`;

  try {
    await client.del(key);
    console.log(`[TokenBlacklist] Cleared user revocation for ${maskJti(userId)}`);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to clear user revocation:', error);
  }
}

// =============================================================================
// UTILITIES
// =============================================================================

function maskJti(value: string): string {
  if (value.length <= 8) return '****';
  return `${value.substring(0, 4)}...${value.substring(value.length - 4)}`;
}

// =============================================================================
// EXPORTS
// =============================================================================

export { getRedis };

/**
 * Exported constants for documentation and testing
 * Backend services should use these same values:
 * - BLACKLIST_PREFIX: "pml:blacklist:"
 * - TOKEN_BLACKLIST_TTL_SECONDS: 7200 (2 hours)
 */
export const BLACKLIST_CONFIG = {
  prefix: BLACKLIST_PREFIX,
  ttlSeconds: TOKEN_BLACKLIST_TTL_SECONDS,
} as const;
