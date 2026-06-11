/**
 * OAuth Blacklist Guard
 *
 * Intercepts OAuth callback to check if the token's JTI is blacklisted
 * before allowing session creation.
 *
 * ## Why This Is Needed
 *
 * When a user logs out via backchannel logout:
 * 1. Keycloak sends logout notification → we blacklist the token's JTI
 * 2. But if the user has the OAuth callback URL open in another tab...
 * 3. They could complete the login flow with the now-blacklisted token
 * 4. This guard prevents that by checking the blacklist before session creation
 *
 * ## Integration
 *
 * Use this in the OAuth callback route to validate tokens before processing:
 *
 * ```typescript
 * // In /api/auth/[...all]/route.ts
 * import { validateOAuthCallback } from '@/lib/auth/oauth-blacklist-guard';
 *
 * export async function GET(request: Request) {
 *   // Check if this is an OAuth callback
 *   if (request.url.includes('/callback/')) {
 *     const validation = await validateOAuthCallback(request);
 *     if (!validation.valid) {
 *       return Response.redirect(new URL(`/login?error=${validation.error}`, request.url));
 *     }
 *   }
 *   // Continue with normal Better Auth handling
 *   const { GET } = await authHandler;
 *   return GET(request);
 * }
 * ```
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 */

import { decodeJwt } from 'jose';
import { isTokenBlacklisted, isUserRevoked } from './token-blacklist';

// =============================================================================
// TYPES
// =============================================================================

export interface ValidationResult {
  valid: boolean;
  error?: 'token_blacklisted' | 'user_revoked' | 'invalid_token' | 'missing_token';
  details?: {
    jti?: string;
    userId?: string;
    reason?: string;
  };
}

// =============================================================================
// TOKEN EXTRACTION
// =============================================================================

/**
 * Extract tokens from OAuth callback URL or request body
 *
 * OAuth callbacks can receive tokens in different ways:
 * - Authorization code flow: code in query params (tokens come later)
 * - Implicit flow: tokens in URL fragment (not accessible server-side)
 * - Direct token response: tokens in response body
 *
 * For authorization code flow, we check during the token exchange.
 */
function extractTokensFromUrl(url: URL): { idToken?: string; accessToken?: string; code?: string } {
  const params = url.searchParams;

  return {
    idToken: params.get('id_token') || undefined,
    accessToken: params.get('access_token') || undefined,
    code: params.get('code') || undefined,
  };
}

/**
 * Extract JTI and other claims from a JWT
 */
function extractTokenClaims(token: string): {
  jti?: string;
  sub?: string;
  iat?: number;
  exp?: number;
} | null {
  try {
    const payload = decodeJwt(token);
    return {
      jti: payload.jti as string | undefined,
      sub: payload.sub as string | undefined,
      iat: payload.iat as number | undefined,
      exp: payload.exp as number | undefined,
    };
  } catch {
    return null;
  }
}

// =============================================================================
// VALIDATION
// =============================================================================

/**
 * Validate OAuth callback tokens against the blacklist
 *
 * Checks if the token's JTI is blacklisted or if the user has been revoked.
 *
 * @param request - The OAuth callback request
 * @returns Validation result
 *
 * @used-by OAuth callback route handler
 */
export async function validateOAuthCallback(request: Request): Promise<ValidationResult> {
  try {
    const url = new URL(request.url);

    // Check if this is a callback URL
    if (!url.pathname.includes('/callback/')) {
      // Not an OAuth callback, allow through
      return { valid: true };
    }

    const tokens = extractTokensFromUrl(url);

    // For authorization code flow, we don't have tokens yet
    // The blacklist check will happen when Better Auth exchanges the code
    if (tokens.code && !tokens.idToken && !tokens.accessToken) {
      // This is an authorization code callback
      // We can't check blacklist here - tokens haven't been issued yet
      // The blacklist check needs to happen after token exchange
      return { valid: true };
    }

    // If we have tokens (implicit flow or direct response), validate them
    const tokenToValidate = tokens.idToken || tokens.accessToken;

    if (!tokenToValidate) {
      // No tokens to validate
      return { valid: true };
    }

    const claims = extractTokenClaims(tokenToValidate);

    if (!claims) {
      console.warn('[OAuthGuard] Failed to decode token');
      return {
        valid: false,
        error: 'invalid_token',
        details: { reason: 'Failed to decode token' },
      };
    }

    // Check 1: Is the token's JTI blacklisted?
    if (claims.jti) {
      const isBlacklisted = await isTokenBlacklisted(claims.jti);
      if (isBlacklisted) {
        console.warn(`[OAuthGuard] Token JTI is blacklisted: ${claims.jti.substring(0, 8)}...`);
        return {
          valid: false,
          error: 'token_blacklisted',
          details: {
            jti: claims.jti,
            reason: 'Token has been revoked via backchannel logout',
          },
        };
      }
    }

    // Check 2: Has the user been revoked?
    if (claims.sub) {
      const revocation = await isUserRevoked(claims.sub);
      if (revocation) {
        // Check if the token was issued BEFORE the revocation
        if (claims.iat && claims.iat < revocation.revokedAt) {
          console.warn(`[OAuthGuard] User was revoked, token issued before revocation: ${claims.sub.substring(0, 8)}...`);
          return {
            valid: false,
            error: 'user_revoked',
            details: {
              userId: claims.sub,
              reason: `User sessions were revoked at ${new Date(revocation.revokedAt * 1000).toISOString()}`,
            },
          };
        }
      }
    }

    // All checks passed
    return { valid: true };
  } catch (error) {
    console.error('[OAuthGuard] Validation error:', error);
    // Fail open - if validation fails, allow the request
    // Better to let them through than lock everyone out
    return { valid: true };
  }
}

/**
 * Validate a token directly (for use in hooks or middleware)
 *
 * @param token - JWT token to validate
 * @returns Validation result
 *
 * @used-by Better Auth hooks, API middleware
 */
export async function validateToken(token: string): Promise<ValidationResult> {
  try {
    const claims = extractTokenClaims(token);

    if (!claims) {
      return {
        valid: false,
        error: 'invalid_token',
        details: { reason: 'Failed to decode token' },
      };
    }

    // Check JTI blacklist
    if (claims.jti) {
      const isBlacklisted = await isTokenBlacklisted(claims.jti);
      if (isBlacklisted) {
        return {
          valid: false,
          error: 'token_blacklisted',
          details: { jti: claims.jti },
        };
      }
    }

    // Check user revocation
    if (claims.sub) {
      const revocation = await isUserRevoked(claims.sub);
      if (revocation && claims.iat && claims.iat < revocation.revokedAt) {
        return {
          valid: false,
          error: 'user_revoked',
          details: { userId: claims.sub },
        };
      }
    }

    return { valid: true };
  } catch (error) {
    console.error('[OAuthGuard] Token validation error:', error);
    return { valid: true }; // Fail open
  }
}

/**
 * Check if a specific JTI is blacklisted
 *
 * Simple wrapper for direct JTI checks.
 *
 * @param jti - JWT ID to check
 * @returns true if blacklisted
 */
export async function isJtiBlacklisted(jti: string): Promise<boolean> {
  return isTokenBlacklisted(jti);
}
