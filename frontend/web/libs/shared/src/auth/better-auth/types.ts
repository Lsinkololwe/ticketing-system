/**
 * Better Auth Configuration Types
 *
 * Configuration types for Better Auth setup. Session and user types
 * should be inferred from Better Auth using `typeof auth.$Infer`.
 */

// =============================================================================
// APPLICATION IDENTIFIERS
// =============================================================================

/**
 * Supported application identifiers for Better Auth configuration
 */
export type AppId = 'admin' | 'organization-admin' | 'ticketing';

// =============================================================================
// CONFIGURATION TYPES
// =============================================================================

/**
 * Application-specific configuration options
 */
export interface AppAuthConfig {
  /** Application identifier */
  appId: AppId;
  /** Cookie prefix for session cookies */
  cookiePrefix: string;
  /** Redis key prefix for session storage (defaults to `${appId}:`) */
  redisKeyPrefix?: string;
  /** Enable Redis secondary storage (defaults to true) */
  enableRedis?: boolean;
}

// =============================================================================
// KEYCLOAK UTILITIES
// =============================================================================

/**
 * Keycloak OIDC endpoints
 */
export interface KeycloakEndpoints {
  authorization: string;
  token: string;
  userinfo: string;
  endSession: string;
  jwks: string;
  discovery: string;
}

/**
 * Get Keycloak endpoints from issuer URL
 */
export function getKeycloakEndpoints(issuer: string): KeycloakEndpoints {
  return {
    authorization: `${issuer}/protocol/openid-connect/auth`,
    token: `${issuer}/protocol/openid-connect/token`,
    userinfo: `${issuer}/protocol/openid-connect/userinfo`,
    endSession: `${issuer}/protocol/openid-connect/logout`,
    jwks: `${issuer}/protocol/openid-connect/certs`,
    discovery: `${issuer}/.well-known/openid-configuration`,
  };
}
