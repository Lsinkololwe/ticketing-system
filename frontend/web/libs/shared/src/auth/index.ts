/**
 * Auth Module Exports
 *
 * Unified authentication using Keycloak for all applications.
 */

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
