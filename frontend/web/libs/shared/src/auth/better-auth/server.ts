/**
 * Better Auth Server-Only Exports
 *
 * SERVER-ONLY: Import this module only in server components, route handlers,
 * or other server-side code. Do NOT import in client components.
 *
 * ## Quick Start
 *
 * ```typescript
 * import { createAuth, type AuthServices } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * // Create auth instance (synchronous, uses lazy connections)
 * export const { auth, db, redis, jtiBlacklist, handleBackchannelLogout, env } = createAuth({
 *   appId: 'organization-admin',
 *   cookiePrefix: 'pml_org',
 * });
 *
 * // Use Better Auth's type inference for session types
 * type Session = typeof auth.$Infer.Session;
 * type User = typeof auth.$Infer.Session.user;
 * ```
 *
 * ## Advanced Usage (Full Container Access)
 *
 * ```typescript
 * import { getAuthContainer, AuthContainer } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * const container = getAuthContainer({ appId: 'admin', cookiePrefix: 'pml_admin' });
 * const auth = container.getAuth();
 * const blacklist = container.getJtiBlacklist();
 * ```
 *
 * ## Testing
 *
 * ```typescript
 * import { AuthContainer } from '@pml.tickets/shared/auth/better-auth/server';
 * import { TestEnvProvider, InMemoryDatabaseProvider, InMemoryRedisProvider }
 *   from '@pml.tickets/shared/auth/better-auth/providers/__mocks__';
 *
 * const container = new AuthContainer(
 *   { appId: 'test', cookiePrefix: 'test_' },
 *   new TestEnvProvider(),
 *   new InMemoryDatabaseProvider(),
 *   new InMemoryRedisProvider(),
 * );
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 * @module libs/shared/src/auth/better-auth/server
 */

import 'server-only';

// =============================================================================
// MAIN AUTH FACTORY (Backward Compatible)
// =============================================================================

export {
  createAuth,
  getAuthContainer,
  resetAuthContainers,
  AuthContainer,
  type AuthServices,
  type Auth,
} from './createAuth';

// =============================================================================
// CONFIGURATION TYPES
// =============================================================================

export type { AppAuthConfig, AppId } from './types';

// =============================================================================
// INTERFACES (For Custom Implementations)
// =============================================================================

export type {
  // Environment
  IEnvironmentProvider,
  PartialEnvConfig,
  // Database
  IDatabaseProvider,
  MongoPoolOptions,
  // Redis
  IRedisProvider,
  RedisConnectionStatus,
  RedisConnectionOptions,
  // Services
  IJtiBlacklistService,
  BlacklistEntry,
  BlacklistInput,
  BlacklistReason,
  BlacklistStats,
  IBackchannelLogoutHandler,
  LogoutTokenClaims,
  BackchannelLogoutResult,
  BackchannelLogoutDetails,
  BackchannelLogoutErrorCode,
  BackchannelLogoutDependencies,
} from './interfaces';

export { DEFAULT_POOL_OPTIONS, DEFAULT_REDIS_OPTIONS } from './interfaces';

// =============================================================================
// PROVIDERS (For Custom Wiring)
// =============================================================================

export {
  ProcessEnvProvider,
  MongoDatabaseProvider,
  RedisProvider,
} from './providers';

// =============================================================================
// SERVICES (For Direct Usage)
// =============================================================================

export {
  JtiBlacklistService,
  BackchannelLogoutHandler,
  extractJtiFromIdToken,
} from './services';

// =============================================================================
// KEYCLOAK UTILITIES
// =============================================================================

export { getKeycloakEndpoints, type KeycloakEndpoints } from './types';
