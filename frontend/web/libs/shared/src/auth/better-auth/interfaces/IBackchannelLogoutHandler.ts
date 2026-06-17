/**
 * Backchannel Logout Handler Interface
 *
 * Handles OIDC Backchannel Logout requests from Keycloak.
 * When a user logs out of Keycloak (or admin revokes session),
 * Keycloak sends a POST request with a logout_token JWT.
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 * @see https://www.keycloak.org/docs/latest/server_admin/#_oidc-logout
 *
 * @example
 * ```typescript
 * // In Next.js API route handler
 * export async function POST(request: Request) {
 *   const formData = await request.formData();
 *   const logoutToken = formData.get('logout_token') as string;
 *
 *   const result = await backchannelHandler.handle(logoutToken);
 *
 *   return new Response(null, {
 *     status: result.success ? 200 : 400,
 *   });
 * }
 * ```
 *
 * @module libs/shared/src/auth/better-auth/interfaces/IBackchannelLogoutHandler
 */

// =============================================================================
// LOGOUT TOKEN TYPES
// =============================================================================

/**
 * Keycloak logout token claims
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html#LogoutToken
 */
export interface LogoutTokenClaims {
  /** Issuer - Keycloak realm URL */
  iss: string;

  /** Subject - User ID (optional per spec, but Keycloak includes it) */
  sub?: string;

  /** Audience - Client ID(s) */
  aud: string | string[];

  /** Issued at timestamp (epoch seconds) */
  iat: number;

  /** JWT ID - unique identifier for this logout token */
  jti: string;

  /** Keycloak session ID (if "Backchannel Logout Session Required" enabled) */
  sid?: string;

  /** Expiry time (optional) */
  exp?: number;

  /** Logout event claim (must be present per OIDC spec) */
  events: {
    'http://schemas.openid.net/event/backchannel-logout': Record<string, unknown>;
  };
}

// =============================================================================
// HANDLER RESULT TYPES
// =============================================================================

/**
 * Result of processing a backchannel logout request
 */
export interface BackchannelLogoutResult {
  /** Whether the logout was processed successfully */
  success: boolean;

  /** Error message if unsuccessful */
  error?: string;

  /** Error code for programmatic handling */
  errorCode?: BackchannelLogoutErrorCode;

  /** Details about what was done (on success) */
  details?: BackchannelLogoutDetails;
}

/**
 * Details of a successful backchannel logout
 */
export interface BackchannelLogoutDetails {
  /** User ID from the logout token */
  userId?: string;

  /** Session ID from the logout token */
  sessionId?: string;

  /** JWT ID that was blacklisted */
  jti: string;

  /** Number of Better Auth sessions revoked */
  sessionsRevoked: number;
}

/**
 * Error codes for backchannel logout failures
 */
export type BackchannelLogoutErrorCode =
  | 'INVALID_TOKEN'           // Token signature verification failed
  | 'MISSING_EVENT_CLAIM'     // Missing backchannel-logout event
  | 'MISSING_JTI'             // Missing jti claim
  | 'MISSING_SUBJECT'         // Missing both sub and sid claims
  | 'ISSUER_MISMATCH'         // Token issuer doesn't match config
  | 'AUDIENCE_MISMATCH'       // Token audience doesn't match client ID
  | 'TOKEN_EXPIRED'           // Token has expired
  | 'BLACKLIST_FAILED'        // Failed to add JTI to blacklist
  | 'SESSION_REVOKE_FAILED'   // Failed to revoke sessions
  | 'UNKNOWN_ERROR';          // Unexpected error

// =============================================================================
// BACKCHANNEL LOGOUT HANDLER INTERFACE
// =============================================================================

/**
 * Backchannel logout handler contract
 *
 * Implementations verify logout tokens from Keycloak,
 * blacklist the JTI, and revoke associated sessions.
 */
export interface IBackchannelLogoutHandler {
  /**
   * Handle a backchannel logout request
   *
   * Processing steps:
   * 1. Verify token signature using Keycloak JWKS
   * 2. Validate required claims (iss, aud, jti, events)
   * 3. Add JTI to blacklist
   * 4. Revoke Better Auth sessions for the user
   *
   * @param logoutToken - The logout_token JWT from Keycloak
   * @returns Result indicating success or failure
   *
   * @example
   * ```typescript
   * const result = await handler.handle(logoutToken);
   *
   * if (!result.success) {
   *   console.error('Logout failed:', result.error, result.errorCode);
   * } else {
   *   console.log('Revoked sessions:', result.details?.sessionsRevoked);
   * }
   * ```
   */
  handle(logoutToken: string): Promise<BackchannelLogoutResult>;

  /**
   * Verify a logout token without processing
   *
   * Useful for validation/debugging without side effects.
   *
   * @param logoutToken - The logout_token JWT
   * @returns Decoded claims if valid, error result if invalid
   */
  verify(logoutToken: string): Promise<
    | { valid: true; claims: LogoutTokenClaims }
    | { valid: false; error: string; errorCode: BackchannelLogoutErrorCode }
  >;
}

// =============================================================================
// HANDLER DEPENDENCIES
// =============================================================================

/**
 * Dependencies required by BackchannelLogoutHandler
 *
 * Injected via constructor for testability.
 */
export interface BackchannelLogoutDependencies {
  /** Keycloak JWKS URL for token verification */
  keycloakJwksUrl: string;

  /** Expected issuer (Keycloak realm URL) */
  keycloakIssuer: string;

  /** Expected audience (client ID) */
  clientId: string;

  /** JTI blacklist service */
  jtiBlacklist: import('./IJtiBlacklistService').IJtiBlacklistService;

  /**
   * Function to revoke all Better Auth sessions for a user.
   * Uses Better Auth's native auth.api.revokeSessions() internally.
   */
  revokeUserSessions: (userId: string) => Promise<void>;
}
