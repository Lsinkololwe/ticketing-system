/**
 * Admin App - Better Auth Configuration
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
 * - Configure Keycloak: Clients → admin → Backchannel Logout URL:
 *   `https://your-domain.com/api/auth/backchannel-logout`
 *
 * @see https://better-auth.com/docs/reference/options
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 */

import {
  getBetterAuth,
  type BetterAuthResult,
  type BetterAuthInstance,
} from '@pml.tickets/shared/auth/better-auth';

// =============================================================================
// AUTH CONFIGURATION
// =============================================================================

/**
 * Admin app auth configuration
 *
 * @used-by getBetterAuth() - Creates auth instance with these settings
 */
const AUTH_CONFIG = {
  appId: 'admin' as const,
  cookiePrefix: 'pml_admin',
  redisKeyPrefix: 'pml-admin:',
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

export type { BetterAuthInstance as Auth } from '@pml.tickets/shared/auth/better-auth';
export type { SessionResponse as Session, AuthUser, BetterAuthResult } from '@pml.tickets/shared/auth/better-auth';
