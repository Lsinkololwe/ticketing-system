/**
 * Keycloak Backchannel Logout Handler
 *
 * Handles OIDC Backchannel Logout requests from Keycloak.
 * When a user logs out of Keycloak (or admin revokes session),
 * Keycloak sends a POST request to this handler to notify Better Auth.
 *
 * ## OIDC Backchannel Logout Flow
 *
 * ```
 * 1. User logs out at Keycloak (or admin revokes session)
 *        │
 *        ▼
 * 2. Keycloak sends POST to /api/auth/backchannel-logout
 *    with logout_token (JWT)
 *        │
 *        ▼
 * 3. This handler:
 *    ├── Verifies logout_token signature using Keycloak JWKS
 *    ├── Validates claims (iss, aud, events, iat, jti)
 *    ├── Extracts sub (user ID) and sid (session ID)
 *    ├── Adds JTI to blacklist
 *    └── Revokes Better Auth sessions for the user
 *        │
 *        ▼
 * 4. Returns 200 OK (or 400/401 on error)
 * ```
 *
 * ## Keycloak Configuration
 *
 * In Keycloak Admin Console:
 * 1. Go to Clients → your-client → Settings
 * 2. Enable "Backchannel Logout"
 * 3. Set "Backchannel Logout URL" to:
 *    `https://your-app.com/api/auth/backchannel-logout`
 * 4. Enable "Backchannel Logout Session Required" (recommended)
 *
 * ## Logout Token Structure
 *
 * ```json
 * {
 *   "iss": "https://keycloak.example.com/realms/my-realm",
 *   "sub": "user-uuid",
 *   "aud": "my-client-id",
 *   "iat": 1709721234,
 *   "jti": "unique-token-id",
 *   "sid": "keycloak-session-id",
 *   "events": {
 *     "http://schemas.openid.net/event/backchannel-logout": {}
 *   }
 * }
 * ```
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 * @see https://www.keycloak.org/docs/latest/server_admin/#_oidc-logout
 * @module libs/shared/src/auth/better-auth/backchannel-logout
 */

import * as jose from 'jose';
import type { JtiBlacklistService } from './jti-blacklist';

// =============================================================================
// TYPES
// =============================================================================

/**
 * Keycloak logout token claims
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html#LogoutToken
 */
export interface LogoutTokenClaims {
  /** Issuer - Keycloak realm URL */
  iss: string;
  /** Subject - User ID */
  sub?: string;
  /** Audience - Client ID(s) */
  aud: string | string[];
  /** Issued at timestamp */
  iat: number;
  /** JWT ID - unique identifier for this logout token */
  jti: string;
  /** Keycloak session ID (if "Backchannel Logout Session Required" is enabled) */
  sid?: string;
  /** Logout event claim (must be present) */
  events: {
    'http://schemas.openid.net/event/backchannel-logout': Record<string, unknown>;
  };
  /** Expiry time (optional) */
  exp?: number;
}

/**
 * Configuration for backchannel logout handler
 */
export interface BackchannelLogoutConfig {
  /** Keycloak JWKS URL for token verification */
  keycloakJwksUrl: string;
  /** Expected issuer (Keycloak realm URL) */
  keycloakIssuer: string;
  /** Expected audience (client ID) */
  clientId: string;
  /** JTI blacklist service */
  jtiBlacklist: JtiBlacklistService;
  /** Function to revoke Better Auth sessions for a user */
  revokeUserSessions: (userId: string) => Promise<void>;
  /** Optional: function to revoke a specific session by Keycloak session ID */
  revokeSessionBySid?: (sid: string) => Promise<void>;
}

/**
 * Result of processing a backchannel logout request
 */
export interface BackchannelLogoutResult {
  success: boolean;
  error?: string;
  details?: {
    userId?: string;
    sessionId?: string;
    jti: string;
    sessionsRevoked: number;
  };
}

// =============================================================================
// BACKCHANNEL LOGOUT HANDLER
// =============================================================================

/**
 * Create a backchannel logout handler
 *
 * @param config - Handler configuration
 * @returns Handler function for backchannel logout requests
 *
 * @example
 * ```typescript
 * const handleBackchannelLogout = createBackchannelLogoutHandler({
 *   keycloakJwksUrl: 'https://keycloak.example.com/realms/my-realm/protocol/openid-connect/certs',
 *   keycloakIssuer: 'https://keycloak.example.com/realms/my-realm',
 *   clientId: 'my-client-id',
 *   jtiBlacklist,
 *   revokeUserSessions: async (userId) => {
 *     // Revoke all sessions for this user in Better Auth
 *   },
 * });
 *
 * // In Next.js route handler:
 * export async function POST(request: Request) {
 *   const formData = await request.formData();
 *   const logoutToken = formData.get('logout_token') as string;
 *   const result = await handleBackchannelLogout(logoutToken);
 *   return new Response(null, { status: result.success ? 200 : 400 });
 * }
 * ```
 */
export function createBackchannelLogoutHandler(config: BackchannelLogoutConfig) {
  const {
    keycloakJwksUrl,
    keycloakIssuer,
    clientId,
    jtiBlacklist,
    revokeUserSessions,
    revokeSessionBySid,
  } = config;

  // Create JWKS client for verifying Keycloak signatures
  const JWKS = jose.createRemoteJWKSet(new URL(keycloakJwksUrl));

  /**
   * Process a backchannel logout request
   *
   * @param logoutToken - The logout_token from Keycloak (JWT)
   * @returns Result indicating success or failure
   *
   * @used-by
   * - `/api/auth/backchannel-logout` route handler
   */
  return async function handleBackchannelLogout(
    logoutToken: string
  ): Promise<BackchannelLogoutResult> {
    try {
      // =======================================================================
      // STEP 1: Verify token signature and decode claims
      // =======================================================================
      let claims: LogoutTokenClaims;

      try {
        const { payload } = await jose.jwtVerify(logoutToken, JWKS, {
          issuer: keycloakIssuer,
          audience: clientId,
        });

        claims = payload as unknown as LogoutTokenClaims;
      } catch (error) {
        console.error('[Backchannel Logout] Token verification failed:', error);
        return {
          success: false,
          error: 'Invalid logout token: signature verification failed',
        };
      }

      // =======================================================================
      // STEP 2: Validate required claims
      // =======================================================================

      // Check for backchannel-logout event claim
      const backchannelEvent = claims.events?.['http://schemas.openid.net/event/backchannel-logout'];
      if (backchannelEvent === undefined) {
        return {
          success: false,
          error: 'Invalid logout token: missing backchannel-logout event claim',
        };
      }

      // JTI must be present
      if (!claims.jti) {
        return {
          success: false,
          error: 'Invalid logout token: missing jti claim',
        };
      }

      // Must have either sub or sid
      if (!claims.sub && !claims.sid) {
        return {
          success: false,
          error: 'Invalid logout token: must contain sub or sid claim',
        };
      }

      console.log('[Backchannel Logout] Processing logout for:', {
        jti: claims.jti,
        sub: claims.sub,
        sid: claims.sid,
        iss: claims.iss,
      });

      // =======================================================================
      // STEP 3: Add JTI to blacklist
      // =======================================================================
      await jtiBlacklist.add({
        jti: claims.jti,
        userId: claims.sub || 'unknown',
        sessionId: claims.sid,
        reason: 'backchannel_logout',
        tokenExpiry: claims.exp,
      });

      // =======================================================================
      // STEP 4: Revoke Better Auth sessions
      // =======================================================================
      let sessionsRevoked = 0;

      // If we have a specific session ID and handler, revoke that session
      if (claims.sid && revokeSessionBySid) {
        try {
          await revokeSessionBySid(claims.sid);
          sessionsRevoked++;
          console.log(`[Backchannel Logout] Revoked session by sid: ${claims.sid}`);
        } catch (error) {
          console.warn('[Backchannel Logout] Failed to revoke session by sid:', error);
        }
      }

      // Also revoke all sessions for the user (if sub is provided)
      if (claims.sub) {
        try {
          await revokeUserSessions(claims.sub);
          console.log(`[Backchannel Logout] Revoked all sessions for user: ${claims.sub}`);
          // We don't know exact count here, but mark as successful
          sessionsRevoked = sessionsRevoked || 1;
        } catch (error) {
          console.error('[Backchannel Logout] Failed to revoke user sessions:', error);
          // Continue anyway - JTI is blacklisted
        }
      }

      // =======================================================================
      // STEP 5: Return success
      // =======================================================================
      return {
        success: true,
        details: {
          userId: claims.sub,
          sessionId: claims.sid,
          jti: claims.jti,
          sessionsRevoked,
        },
      };
    } catch (error) {
      console.error('[Backchannel Logout] Unexpected error:', error);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  };
}

// =============================================================================
// HELPER: Create logout token decoder (for debugging)
// =============================================================================

/**
 * Decode a logout token without verification (for debugging only)
 *
 * ⚠️ DO NOT use this for actual validation - always use the handler
 *
 * @param logoutToken - JWT to decode
 * @returns Decoded claims or null if invalid
 */
export function decodeLogoutToken(logoutToken: string): LogoutTokenClaims | null {
  try {
    const decoded = jose.decodeJwt(logoutToken);
    return decoded as unknown as LogoutTokenClaims;
  } catch {
    return null;
  }
}

// =============================================================================
// HELPER: Extract JTI from ID token (for session creation)
// =============================================================================

/**
 * Extract JTI from an ID token
 *
 * Used during OAuth callback to get the JTI for blacklist checking.
 *
 * @param idToken - The id_token from OAuth callback
 * @returns JTI or null if not present
 *
 * @used-by
 * - `config.ts` databaseHooks.session.create.before
 */
export function extractJtiFromIdToken(idToken: string): {
  jti: string | null;
  sub: string | null;
  iat: number | null;
  exp: number | null;
} {
  try {
    const decoded = jose.decodeJwt(idToken);
    return {
      jti: (decoded.jti as string) || null,
      sub: (decoded.sub as string) || null,
      iat: (decoded.iat as number) || null,
      exp: (decoded.exp as number) || null,
    };
  } catch {
    return { jti: null, sub: null, iat: null, exp: null };
  }
}
