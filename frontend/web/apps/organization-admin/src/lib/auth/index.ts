/**
 * Organization Admin App - Better Auth Configuration
 *
 * Uses shared Better Auth configuration from @pml.tickets/shared.
 * This file provides the auth instance and supporting services for this app.
 *
 * ## Architecture Overview
 *
 * ```
 * getBetterAuth(config)
 *     │
 *     ├── auth: BetterAuthInstance (main auth object)
 *     ├── jtiBlacklist: JtiBlacklistService (token blacklist)
 *     ├── handleBackchannelLogout: Function (Keycloak logout handler)
 *     ├── mongoDb: Db (MongoDB database)
 *     └── redis: Redis | null (Redis client)
 * ```
 *
 * ## Backchannel Logout Support
 *
 * When Redis is enabled, this app supports Keycloak backchannel logout:
 * - Keycloak can notify this app when a user logs out
 * - JTI blacklist prevents session creation with revoked tokens
 * - Configure Keycloak: Clients → organizer → Backchannel Logout URL:
 *   `https://your-domain.com/api/auth/backchannel-logout`
 *
 * @see https://better-auth.com/docs/reference/options
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 */

import {
  getBetterAuth,
  type BetterAuthResult,
  type BetterAuthInstance,
} from '@pml.tickets/shared/auth/better-auth/server';

// =============================================================================
// AUTH CONFIGURATION
// =============================================================================

/**
 * Organization admin app auth configuration
 *
 * @used-by getBetterAuth() - Creates auth instance with these settings
 */
const AUTH_CONFIG = {
  appId: 'organization-admin' as const,
  cookiePrefix: 'pml_org',
  redisKeyPrefix: 'pml-organizer:',
  enableRedis: process.env.ENABLE_REDIS_SECONDARY_STORAGE === 'true',
  debug: process.env.NODE_ENV === 'development',
};

// =============================================================================
// AUTH RESULT (Complete)
// =============================================================================

/**
 * Complete Better Auth result promise
 *
 * Includes auth instance, JTI blacklist, and backchannel logout handler.
 *
 * @used-by
 * - `/api/auth/backchannel-logout` route handler
 * - Services that need direct access to blacklist
 */
export const authResultPromise: Promise<BetterAuthResult> = getBetterAuth(AUTH_CONFIG);

// =============================================================================
// AUTH INSTANCE (Convenience)
// =============================================================================

/**
 * Better Auth instance promise (just the auth object)
 *
 * Use this for standard auth operations like session management.
 *
 * @used-by
 * - `/api/auth/[...all]/route.ts` - Route handler
 * - Server Components that need session data
 */
export const authPromise: Promise<BetterAuthInstance> = authResultPromise.then(r => r.auth);

/**
 * Better Auth instance (async-aware proxy)
 *
 * This proxy automatically awaits the auth instance for each method call.
 * For performance-critical code, use `authPromise` directly.
 *
 * @example
 * ```typescript
 * // In a Server Component
 * const session = await auth.api.getSession({ headers: await headers() });
 * ```
 *
 * @used-by
 * - Server Components for session access
 * - API routes for auth operations
 */
export const auth = new Proxy({} as BetterAuthInstance, {
  get(_, prop) {
    return async (...args: unknown[]) => {
      const authInstance = await authPromise;
      const value = (authInstance as Record<string, unknown>)[prop as string];
      if (typeof value === 'function') {
        return (value as (...args: unknown[]) => unknown).apply(authInstance, args);
      }
      return value;
    };
  },
});

// =============================================================================
// BACKCHANNEL LOGOUT HANDLER
// =============================================================================

/**
 * Get the backchannel logout handler
 *
 * Returns null if Redis/JTI blacklist is not enabled.
 *
 * @used-by `/api/auth/backchannel-logout/route.ts`
 *
 * @example
 * ```typescript
 * const handler = await getBackchannelLogoutHandler();
 * if (handler) {
 *   const result = await handler(logoutToken);
 * }
 * ```
 */
export async function getBackchannelLogoutHandler() {
  const result = await authResultPromise;
  return result.handleBackchannelLogout;
}

/**
 * Get the JTI blacklist service
 *
 * Returns null if Redis is not enabled.
 *
 * @used-by
 * - Admin tools for viewing/managing blacklist
 * - Debugging token revocation issues
 */
export async function getJtiBlacklist() {
  const result = await authResultPromise;
  return result.jtiBlacklist;
}

// =============================================================================
// DATABASE ACCESS
// =============================================================================

/**
 * Get the Redis client from the auth result
 *
 * Returns the Redis client used for session caching.
 * Throws if Redis is not enabled.
 *
 * @used-by `/api/auth/logout/complete` route handler
 */
export function getRedisClient() {
  // Return a proxy that waits for initialization on each call
  return new Proxy({} as NonNullable<Awaited<typeof authResultPromise>['redis']>, {
    get(_, prop) {
      return async (...args: unknown[]) => {
        const result = await authResultPromise;
        if (!result.redis) {
          throw new Error('Redis is not enabled');
        }
        const value = (result.redis as unknown as Record<string, unknown>)[prop as string];
        if (typeof value === 'function') {
          return (value as (...args: unknown[]) => unknown).apply(result.redis, args);
        }
        return value;
      };
    },
  });
}

/**
 * Get the MongoDB database instance
 *
 * Returns the MongoDB Db instance used by Better Auth.
 * Note: This returns a Db instance, not a MongoClient.
 *
 * @used-by `/api/auth/logout/complete` route handler
 */
export async function getMongoDb() {
  const result = await authResultPromise;
  return result.mongoDb;
}

// Alias for backward compatibility
export const getMongoClientPromise = getMongoDb;

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

/**
 * Check if auth is ready (initialized without errors)
 *
 * @used-by Health check endpoints
 */
export async function isAuthReady(): Promise<boolean> {
  try {
    await authPromise;
    return true;
  } catch {
    return false;
  }
}

/**
 * Get the auth initialization error, if any
 *
 * @used-by Debugging and error reporting
 */
export async function getAuthError(): Promise<Error | null> {
  try {
    await authPromise;
    return null;
  } catch (error) {
    return error instanceof Error ? error : new Error(String(error));
  }
}

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type { BetterAuthInstance as Auth, BetterAuthResult } from '@pml.tickets/shared/auth/better-auth/server';
export type { SessionResponse as Session, AuthUser } from '@pml.tickets/shared/auth/better-auth';
