/**
 * Keycloak Configuration
 *
 * Centralized configuration for Keycloak integration across all applications.
 * Uses environment variables for different deployment environments.
 *
 * Based on realm configuration from docker-resources/keycloak/myticketzm-realm.json
 */

export interface KeycloakConfig {
  url: string;
  realm: string;
  clientId: string;
}

/**
 * Known client IDs from the Keycloak realm configuration
 */
export const KEYCLOAK_CLIENTS = {
  /** Public ticketing web app */
  TICKETING_WEB: 'myticketzm-web',
  /** Admin portal */
  ADMIN: 'myticketzm-admin',
  /** Mobile app (public client with PKCE) */
  MOBILE: 'myticketzm-mobile',
  /** Organizer portal */
  ORGANIZER: 'myticketzm-organizer',
} as const;

/**
 * User roles from the Keycloak realm configuration
 */
export const KEYCLOAK_ROLES = {
  CUSTOMER: 'CUSTOMER',
  ORGANIZER: 'ORGANIZER',
  SCANNER: 'SCANNER',
  FINANCE: 'FINANCE',
  ADMIN: 'ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN',
} as const;

export type KeycloakRole = (typeof KEYCLOAK_ROLES)[keyof typeof KEYCLOAK_ROLES];

/**
 * Get Keycloak configuration from environment variables
 */
export function getKeycloakConfig(): KeycloakConfig {
  const url = process.env.NEXT_PUBLIC_KEYCLOAK_URL;
  const realm = process.env.NEXT_PUBLIC_KEYCLOAK_REALM || 'myticketzm';
  const clientId = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID;

  if (!url || !clientId) {
    throw new Error(
      'Missing Keycloak configuration. Ensure NEXT_PUBLIC_KEYCLOAK_URL and NEXT_PUBLIC_KEYCLOAK_CLIENT_ID are set.'
    );
  }

  return { url, realm, clientId };
}

/**
 * Get Keycloak configuration with validation (throws if not configured)
 */
export function requireKeycloakConfig(): KeycloakConfig {
  return getKeycloakConfig();
}

/**
 * Keycloak OAuth2/OIDC endpoints
 */
export function getKeycloakEndpoints(config: KeycloakConfig) {
  const baseUrl = `${config.url}/realms/${config.realm}`;

  return {
    authorization: `${baseUrl}/protocol/openid-connect/auth`,
    token: `${baseUrl}/protocol/openid-connect/token`,
    userinfo: `${baseUrl}/protocol/openid-connect/userinfo`,
    logout: `${baseUrl}/protocol/openid-connect/logout`,
    revocation: `${baseUrl}/protocol/openid-connect/revoke`,
    introspection: `${baseUrl}/protocol/openid-connect/token/introspect`,
    jwks: `${baseUrl}/protocol/openid-connect/certs`,
    wellKnown: `${baseUrl}/.well-known/openid-configuration`,
  };
}

/**
 * Default scopes for authentication
 */
export const DEFAULT_SCOPES = ['openid', 'profile', 'email', 'offline_access'];

/**
 * Admin-specific scopes (includes roles)
 */
export const ADMIN_SCOPES = [...DEFAULT_SCOPES, 'roles'];

/**
 * Token refresh configuration
 */
export const TOKEN_CONFIG = {
  /** Refresh token when it has less than this many seconds until expiry */
  minValiditySeconds: 60,
  /** Buffer time before token expiry to trigger refresh (in seconds) */
  refreshBufferSeconds: 300, // 5 minutes
  /** How often to check token validity (in milliseconds) */
  checkIntervalMs: 60000, // 1 minute
};
