/**
 * Better Auth Server-Only Exports
 *
 * SERVER-ONLY: Import this module only in server components, route handlers,
 * or other server-side code. Do NOT import in client components.
 *
 * @example
 * ```typescript
 * import { createAuth, type AuthServices } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * // Create auth instance (synchronous, uses lazy connections)
 * const { auth, db, redis, jtiBlacklist, handleBackchannelLogout, env } = createAuth({
 *   appId: 'organization-admin',
 *   cookiePrefix: 'pml_org',
 * });
 *
 * // Use Better Auth's type inference for session types
 * type Session = typeof auth.$Infer.Session;
 * type User = typeof auth.$Infer.Session.user;
 * ```
 *
 * @module libs/shared/src/auth/better-auth/server
 */

import 'server-only';

// =============================================================================
// MAIN AUTH FACTORY
// =============================================================================

export {
  createAuth,
  type AuthServices,
  type Auth,
} from './config';

// =============================================================================
// CONFIGURATION TYPES
// =============================================================================

export type { AppAuthConfig, AppId } from './types';

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
  type BackchannelLogoutConfig,
  type BackchannelLogoutResult,
} from './backchannel-logout';
