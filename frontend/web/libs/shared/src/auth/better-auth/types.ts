/**
 * Better Auth Shared Types
 *
 * Type definitions for Better Auth configuration and session management.
 */

import type { BetterAuthOptions } from 'better-auth';

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
 * Required environment variables for Better Auth
 */
export interface BetterAuthEnv {
  /** MongoDB connection URI */
  MONGODB_URI: string;
  /** MongoDB database name */
  MONGODB_DATABASE: string;
  /** Application base URL */
  APP_URL: string;
  /** Better Auth secret (min 32 chars) */
  AUTH_SECRET: string;
  /** Keycloak client ID */
  KEYCLOAK_CLIENT_ID: string;
  /** Keycloak client secret */
  KEYCLOAK_CLIENT_SECRET: string;
  /** Keycloak issuer URL */
  KEYCLOAK_ISSUER: string;
  /** Redis host (optional) */
  REDIS_HOST?: string;
  /** Redis port (optional) */
  REDIS_PORT?: string;
  /** Redis password (optional) */
  REDIS_PASSWORD?: string;
}

/**
 * Application-specific configuration options
 */
export interface AppAuthConfig {
  /** Application identifier */
  appId: AppId;
  /** Cookie prefix for session cookies */
  cookiePrefix: string;
  /** Redis key prefix for session storage */
  redisKeyPrefix: string;
  /** Enable Redis secondary storage */
  enableRedis?: boolean;
  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Environment validation result
 */
export type EnvValidationResult =
  | { valid: true; env: BetterAuthEnv }
  | { valid: false; errors: string[] };

// =============================================================================
// SESSION TYPES
// =============================================================================

/**
 * User object from Better Auth session
 */
export interface AuthUser {
  id: string;
  email: string;
  name: string;
  emailVerified: boolean;
  image: string | null;
  roles?: string[];
  keycloakId?: string;
}

/**
 * Session object from Better Auth
 */
export interface AuthSession {
  id: string;
  userId: string;
  token: string;
  expiresAt: Date;
  ipAddress?: string;
  userAgent?: string;
}

/**
 * Full session response from Better Auth API
 */
export interface SessionResponse {
  user: AuthUser;
  session: AuthSession;
}

// =============================================================================
// KEYCLOAK CONFIGURATION
// =============================================================================

/**
 * Keycloak OIDC endpoints
 */
export interface KeycloakEndpoints {
  /** Authorization endpoint */
  authorization: string;
  /** Token endpoint */
  token: string;
  /** Userinfo endpoint */
  userinfo: string;
  /** End session (logout) endpoint */
  endSession: string;
  /** JWKS endpoint */
  jwks: string;
  /** Discovery document URL */
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

// =============================================================================
// EXPORTS
// =============================================================================

export type { BetterAuthOptions };
