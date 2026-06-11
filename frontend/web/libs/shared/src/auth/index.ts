/**
 * Auth Module Exports
 *
 * Provides two authentication approaches:
 * 1. Better Auth - Server-side session management with MongoDB/Redis
 * 2. Keycloak Direct - Client-side keycloak-js integration
 *
 * ## Better Auth (Recommended for Next.js Apps)
 *
 * Better Auth handles session management server-side with:
 * - MongoDB for persistent session storage
 * - Redis for fast session caching (optional)
 * - Keycloak as the OAuth provider
 *
 * ```typescript
 * // Server Configuration
 * import { createBetterAuth, getBetterAuth } from '@pml.tickets/shared/auth/better-auth';
 *
 * // Client Hooks
 * import { createBetterAuthClient } from '@pml.tickets/shared/auth/better-auth';
 *
 * // Server Utilities
 * import { getServerSession, requireAuth } from '@pml.tickets/shared/auth/better-auth';
 *
 * // Middleware
 * import { createAuthMiddleware } from '@pml.tickets/shared/auth/better-auth';
 * ```
 *
 * ## Keycloak Direct (Legacy/Mobile Apps)
 *
 * Direct keycloak-js integration for client-side token management.
 * Use this for apps that need direct Keycloak access.
 */

// =============================================================================
// BETTER AUTH (Primary - Recommended)
// =============================================================================

// Re-export everything from better-auth module
export * from './better-auth';

// =============================================================================
// KEYCLOAK DIRECT (Legacy)
// =============================================================================

// Configuration
export {
  getKeycloakConfig,
  requireKeycloakConfig,
  getKeycloakEndpoints,
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
