/**
 * Backchannel Logout Handler
 *
 * SERVER-ONLY: Handles Keycloak backchannel logout requests.
 *
 * Class-based implementation of IBackchannelLogoutHandler.
 * Verifies logout tokens from Keycloak, blacklists JTIs,
 * and revokes Better Auth sessions.
 *
 * ## Native jose Features Used
 *
 * - createRemoteJWKSet: Fetches and caches Keycloak JWKS
 * - jwtVerify: Verifies token signature and claims
 * - decodeJwt: Extracts claims without verification
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 * @see https://www.keycloak.org/docs/latest/server_admin/#_oidc-logout
 *
 * @example
 * ```typescript
 * const handler = new BackchannelLogoutHandler({
 *   keycloakJwksUrl: 'http://keycloak/realms/test/protocol/openid-connect/certs',
 *   keycloakIssuer: 'http://keycloak/realms/test',
 *   clientId: 'my-app',
 *   jtiBlacklist,
 *   revokeUserSessions: async (userId) => { ... },
 * });
 *
 * const result = await handler.handle(logoutToken);
 * ```
 *
 * @module libs/shared/src/auth/better-auth/services/BackchannelLogoutHandler
 */

import 'server-only';

import * as jose from 'jose';
import type {
  IBackchannelLogoutHandler,
  LogoutTokenClaims,
  BackchannelLogoutResult,
  BackchannelLogoutErrorCode,
  BackchannelLogoutDependencies,
} from '../interfaces/IBackchannelLogoutHandler';
import type { IJtiBlacklistService } from '../interfaces/IJtiBlacklistService';

// =============================================================================
// BACKCHANNEL LOGOUT EVENT URI
// =============================================================================

const BACKCHANNEL_LOGOUT_EVENT = 'http://schemas.openid.net/event/backchannel-logout';

// =============================================================================
// BACKCHANNEL LOGOUT HANDLER
// =============================================================================

/**
 * Backchannel Logout Handler
 *
 * Processes OIDC backchannel logout requests from Keycloak.
 */
export class BackchannelLogoutHandler implements IBackchannelLogoutHandler {
  private readonly JWKS: ReturnType<typeof jose.createRemoteJWKSet>;
  private readonly keycloakIssuer: string;
  private readonly clientId: string;
  private readonly jtiBlacklist: IJtiBlacklistService;
  private readonly revokeUserSessions: (userId: string) => Promise<void>;

  /**
   * Create backchannel logout handler
   *
   * @param deps - Handler dependencies
   */
  constructor(deps: BackchannelLogoutDependencies) {
    this.keycloakIssuer = deps.keycloakIssuer;
    this.clientId = deps.clientId;
    this.jtiBlacklist = deps.jtiBlacklist;
    this.revokeUserSessions = deps.revokeUserSessions;

    // Native jose: Create remote JWKS fetcher with caching
    this.JWKS = jose.createRemoteJWKSet(new URL(deps.keycloakJwksUrl));
  }

  /**
   * Handle a backchannel logout request
   */
  async handle(logoutToken: string): Promise<BackchannelLogoutResult> {
    try {
      // =========================================================================
      // STEP 1: Verify and decode token
      // =========================================================================
      const verifyResult = await this.verify(logoutToken);

      if (!verifyResult.valid) {
        return {
          success: false,
          error: verifyResult.error,
          errorCode: verifyResult.errorCode,
        };
      }

      const claims = verifyResult.claims;

      console.log('[BackchannelLogoutHandler] Processing logout:', {
        jti: claims.jti,
        sub: claims.sub,
        sid: claims.sid,
        iss: claims.iss,
      });

      // =========================================================================
      // STEP 2: Add JTI to blacklist
      // =========================================================================
      const blacklistResult = await this.jtiBlacklist.add({
        jti: claims.jti,
        userId: claims.sub || 'unknown',
        sessionId: claims.sid,
        reason: 'backchannel_logout',
        tokenExpiry: claims.exp,
      });

      if (!blacklistResult) {
        console.warn('[BackchannelLogoutHandler] Failed to add JTI to blacklist, continuing...');
      }

      // =========================================================================
      // STEP 3: Revoke Better Auth sessions (native API)
      // =========================================================================
      let sessionsRevoked = 0;

      // Revoke all sessions for user using Better Auth's native auth.api.revokeSessions()
      // Note: We don't use sid-based revocation because Better Auth doesn't store Keycloak's sid
      if (claims.sub) {
        try {
          await this.revokeUserSessions(claims.sub);
          sessionsRevoked = 1;
          console.log(`[BackchannelLogoutHandler] Revoked all sessions for user: ${claims.sub}`);
        } catch (error) {
          console.error('[BackchannelLogoutHandler] Failed to revoke user sessions:', error);
          // Continue - JTI is blacklisted
        }
      }

      // =========================================================================
      // STEP 4: Return success
      // =========================================================================
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
      console.error('[BackchannelLogoutHandler] Unexpected error:', error);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
        errorCode: 'UNKNOWN_ERROR',
      };
    }
  }

  /**
   * Verify a logout token without processing
   */
  async verify(logoutToken: string): Promise<
    | { valid: true; claims: LogoutTokenClaims }
    | { valid: false; error: string; errorCode: BackchannelLogoutErrorCode }
  > {
    // =========================================================================
    // Verify signature using Keycloak JWKS
    // =========================================================================
    let claims: LogoutTokenClaims;

    try {
      // Native jose: Verify JWT with remote JWKS
      const { payload } = await jose.jwtVerify(logoutToken, this.JWKS, {
        issuer: this.keycloakIssuer,
        audience: this.clientId,
      });

      claims = payload as unknown as LogoutTokenClaims;
    } catch (error) {
      console.error('[BackchannelLogoutHandler] Token verification failed:', error);

      // Determine specific error type
      if (error instanceof jose.errors.JWTExpired) {
        return {
          valid: false,
          error: 'Logout token has expired',
          errorCode: 'TOKEN_EXPIRED',
        };
      }

      if (error instanceof jose.errors.JWTClaimValidationFailed) {
        const message = error.message;
        if (message.includes('iss')) {
          return {
            valid: false,
            error: `Invalid issuer: expected ${this.keycloakIssuer}`,
            errorCode: 'ISSUER_MISMATCH',
          };
        }
        if (message.includes('aud')) {
          return {
            valid: false,
            error: `Invalid audience: expected ${this.clientId}`,
            errorCode: 'AUDIENCE_MISMATCH',
          };
        }
      }

      return {
        valid: false,
        error: 'Invalid logout token: signature verification failed',
        errorCode: 'INVALID_TOKEN',
      };
    }

    // =========================================================================
    // Validate required claims
    // =========================================================================

    // Check for backchannel-logout event claim (required by OIDC spec)
    const backchannelEvent = claims.events?.[BACKCHANNEL_LOGOUT_EVENT];
    if (backchannelEvent === undefined) {
      return {
        valid: false,
        error: 'Invalid logout token: missing backchannel-logout event claim',
        errorCode: 'MISSING_EVENT_CLAIM',
      };
    }

    // JTI must be present
    if (!claims.jti) {
      return {
        valid: false,
        error: 'Invalid logout token: missing jti claim',
        errorCode: 'MISSING_JTI',
      };
    }

    // Must have either sub or sid (per OIDC spec)
    if (!claims.sub && !claims.sid) {
      return {
        valid: false,
        error: 'Invalid logout token: must contain sub or sid claim',
        errorCode: 'MISSING_SUBJECT',
      };
    }

    return { valid: true, claims };
  }
}

// =============================================================================
// HELPER: Extract JTI from ID token
// =============================================================================

/**
 * Extract JTI and related claims from an ID token
 *
 * Used during OAuth callback to get the JTI for blacklist checking.
 * Does NOT verify the token signature (use for already-verified tokens).
 *
 * @param idToken - The id_token from OAuth callback
 * @returns Extracted claims or nulls if not present
 */
export function extractJtiFromIdToken(idToken: string): {
  jti: string | null;
  sub: string | null;
  iat: number | null;
  exp: number | null;
} {
  try {
    // Native jose: Decode without verification
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
