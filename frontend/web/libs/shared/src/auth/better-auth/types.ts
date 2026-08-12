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
  /**
   * Redis key prefix for *session* storage (defaults to `${appId}:`).
   *
   * Applies to Better Auth's secondary storage only. Revocation keys are global and never
   * namespaced by app — see `libs/shared/src/auth/revocation/keys.ts`.
   */
  redisKeyPrefix?: string;
  /** Enable Redis secondary storage (defaults to true) */
  enableRedis?: boolean;
  /**
   * The app's durable revocation service.
   *
   * Supplied by the app rather than built by the container, because the durable half needs an
   * identity-service URL and a service account, which differ per deployment. Backchannel logout
   * is disabled when it is absent.
   */
  revocationService?: import('../revocation').IRevocationService;
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
