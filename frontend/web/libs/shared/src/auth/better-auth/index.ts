/**
 * Better Auth Module Exports (Client-Safe)
 *
 * This module exports ONLY client-safe types and utilities.
 * For server-side configuration, import from './server' instead.
 *
 * ## Client Usage (Types Only)
 *
 * ```typescript
 * import type { AppId, KeycloakEndpoints } from '@pml.tickets/shared/auth/better-auth';
 * ```
 *
 * ## Server Usage (Configuration)
 *
 * ```typescript
 * import { createAuth, type AuthServices } from '@pml.tickets/shared/auth/better-auth/server';
 *
 * export const { auth, db, redis, jtiBlacklist, handleBackchannelLogout, env } = createAuth({
 *   appId: 'organization-admin',
 *   cookiePrefix: 'pml_org',
 * });
 *
 * // Type inference
 * type Session = typeof auth.$Infer.Session;
 * type User = typeof auth.$Infer.Session.user;
 * ```
 *
 * ## Testing
 *
 * ```typescript
 * import { AuthContainer } from '@pml.tickets/shared/auth/better-auth/server';
 * import {
 *   TestEnvProvider,
 *   InMemoryDatabaseProvider,
 *   InMemoryRedisProvider,
 * } from '@pml.tickets/shared/auth/better-auth/testing';
 *
 * const container = new AuthContainer(
 *   { appId: 'test', cookiePrefix: 'test_' },
 *   new TestEnvProvider(),
 *   new InMemoryDatabaseProvider(),
 *   new InMemoryRedisProvider(),
 * );
 * ```
 *
 * ## Client Components (Official Better Auth Pattern)
 *
 * ```typescript
 * import { createAuthClient } from 'better-auth/react';
 *
 * const authClient = createAuthClient({
 *   baseURL: process.env.NEXT_PUBLIC_APP_URL,
 * });
 *
 * const { data: session } = authClient.useSession();
 * await authClient.signOut();
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 * @module libs/shared/src/auth/better-auth
 */

// =============================================================================
// TYPES (Client-Safe)
// =============================================================================

export type {
  AppId,
  AppAuthConfig,
  KeycloakEndpoints,
} from './types';

export { getKeycloakEndpoints } from './types';

// =============================================================================
// INTERFACE TYPES (Client-Safe)
// =============================================================================

export type {
  // These are type-only exports, safe for client
  BlacklistReason,
  BackchannelLogoutErrorCode,
} from './interfaces';
