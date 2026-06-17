/**
 * Auth Module Exports
 *
 * Provides authentication utilities for the ticketing system.
 *
 * ## Better Auth (Recommended for Next.js Apps)
 *
 * Better Auth handles session management with Keycloak as OAuth provider.
 *
 * ### Server Components
 *
 * ```typescript
 * import { createAuth } from '@pml.tickets/shared/auth/better-auth/server';
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
 * ### Client Components
 *
 * Use official Better Auth React client directly:
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
 * await authClient.signIn.oauth2({ providerId: 'keycloak' });
 * ```
 *
 * ### Route Handler
 *
 * ```typescript
 * import { toNextJsHandler } from 'better-auth/next-js';
 * import { auth } from '@/lib/auth';
 *
 * export const { GET, POST } = toNextJsHandler(auth);
 * ```
 *
 * ## Keycloak Direct (Legacy/Mobile Apps)
 *
 * Direct keycloak-js integration for client-side token management.
 *
 * @see https://better-auth.com/docs/integrations/next
 */

// =============================================================================
// BETTER AUTH (Types Only - Client Safe)
// =============================================================================

// Re-export only client-safe types from better-auth
export type {
  AppId,
  AppAuthConfig,
  KeycloakEndpoints,
} from './better-auth';

export { getKeycloakEndpoints } from './better-auth';

// =============================================================================
// KEYCLOAK DIRECT (Legacy)
// =============================================================================

// Configuration
export {
  getKeycloakConfig,
  requireKeycloakConfig,
  getKeycloakEndpoints as getKeycloakDirectEndpoints,
  KEYCLOAK_CLIENTS,
  KEYCLOAK_ROLES,
  DEFAULT_SCOPES,
  ADMIN_SCOPES,
  TOKEN_CONFIG,
  type KeycloakConfig,
  type KeycloakRole,
} from './keycloak-config';

// Provider and hooks
export {
  KeycloakProvider,
  useKeycloak,
  useAuth,
  type KeycloakUser,
  type KeycloakContextValue,
  type KeycloakProviderProps,
  type KeycloakInitOptions,
} from './keycloak-provider';

// Protected routes
export {
  ProtectedRoute,
  withAuth,
  useCanAccessByRole,
  type ProtectedRouteProps,
} from './protected-route';
