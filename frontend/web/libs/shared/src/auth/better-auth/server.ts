/**
 * Better Auth Server-Only Exports
 *
 * SERVER-ONLY: Import this module only in server components, route handlers,
 * or other server-side code. Do NOT import in client components.
 *
 * @example
 * ```typescript
 * // In app's lib/auth/index.ts (server-side)
 * import { getBetterAuth, createBetterAuth } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * export const authResultPromise = getBetterAuth({
 *   appId: 'admin',
 *   cookiePrefix: 'pml_admin',
 *   redisKeyPrefix: 'pml-admin:',
 * });
 * ```
 *
 * @module libs/shared/src/auth/better-auth/server
 */

import 'server-only';

// =============================================================================
// SERVER CONFIGURATION
// =============================================================================

export {
  createBetterAuth,
  getBetterAuth,
  getBetterAuthInstance,
  validateEnv,
  type BetterAuthInstance,
  type BetterAuthResult,
} from './config';

// =============================================================================
// JTI BLACKLIST (Backchannel Logout Support)
// =============================================================================

export {
  createJtiBlacklist,
  type JtiBlacklistService,
  type JtiBlacklistConfig,
  type BlacklistEntry,
} from './jti-blacklist';

// =============================================================================
// BACKCHANNEL LOGOUT HANDLER
// =============================================================================

export {
  createBackchannelLogoutHandler,
  extractJtiFromIdToken,
  type BackchannelLogoutConfig,
  type BackchannelLogoutResult,
  type LogoutTokenClaims,
} from './backchannel-logout';
