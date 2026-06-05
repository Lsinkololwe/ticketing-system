/**
 * Back-Channel Logout Endpoint
 *
 * Receives logout tokens from Keycloak when a user logs out from any client
 * in the SSO session. This enables immediate session invalidation across
 * all applications.
 *
 * OIDC Back-Channel Logout Spec:
 * https://openid.net/specs/openid-connect-backchannel-1_0.html
 *
 * Enhanced Flow (with Token Blacklisting):
 * 1. User logs out from any Keycloak client (mobile app, another web app, etc.)
 * 2. Keycloak sends POST request to this endpoint with logout_token
 * 3. We validate the JWT signature using Keycloak's JWKS
 * 4. We validate the required claims (iss, aud, events, sid/sub)
 * 5. We blacklist all access tokens for the user (immediate invalidation)
 * 6. We invalidate all Better Auth sessions in Redis
 * 7. Return 200 OK (or 400 for invalid token)
 *
 * OWASP Security:
 * - JWT signature validated against Keycloak JWKS
 * - Issuer must match our Keycloak realm
 * - Audience must include our client ID
 * - Events claim must contain back-channel logout event
 * - Token must not be expired
 * - Access tokens blacklisted for immediate revocation
 */

import { NextRequest, NextResponse } from 'next/server';
import { createRemoteJWKSet, jwtVerify, JWTPayload } from 'jose';
import { invalidateUserSessionsWithBlacklist } from '@/lib/auth/token-blacklist';

// =============================================================================
// CONFIGURATION
// =============================================================================

const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL;
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM;
const KEYCLOAK_CLIENT_ID = process.env.AUTH_KEYCLOAK_ID;

const KEYCLOAK_ISSUER = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`;
const KEYCLOAK_JWKS_URI = `${KEYCLOAK_ISSUER}/protocol/openid-connect/certs`;

// OIDC Back-Channel Logout event claim
const BACKCHANNEL_LOGOUT_EVENT = 'http://schemas.openid.net/event/backchannel-logout';

// =============================================================================
// JWKS CLIENT (cached)
// =============================================================================

let jwksClient: ReturnType<typeof createRemoteJWKSet> | null = null;

function getJwksClient() {
  if (!jwksClient) {
    jwksClient = createRemoteJWKSet(new URL(KEYCLOAK_JWKS_URI), {
      cooldownDuration: 30000,
      timeoutDuration: 5000,
    });
  }
  return jwksClient;
}

// =============================================================================
// TYPES
// =============================================================================

interface LogoutTokenPayload extends JWTPayload {
  sid?: string;
  sub?: string;
  events?: {
    [BACKCHANNEL_LOGOUT_EVENT]?: Record<string, unknown>;
  };
  nonce?: string;
}

// =============================================================================
// VALIDATION
// =============================================================================

function validateLogoutTokenClaims(payload: LogoutTokenPayload): { valid: boolean; error?: string } {
  if (!payload.sid && !payload.sub) {
    return { valid: false, error: 'Logout token must contain sid or sub claim' };
  }

  if (!payload.events || !payload.events[BACKCHANNEL_LOGOUT_EVENT]) {
    return { valid: false, error: 'Logout token must contain back-channel logout event' };
  }

  if (payload.nonce !== undefined) {
    return { valid: false, error: 'Logout token must not contain nonce claim' };
  }

  if (payload.iss !== KEYCLOAK_ISSUER) {
    return { valid: false, error: `Invalid issuer: expected ${KEYCLOAK_ISSUER}, got ${payload.iss}` };
  }

  const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!aud.includes(KEYCLOAK_CLIENT_ID)) {
    return { valid: false, error: `Invalid audience: ${KEYCLOAK_CLIENT_ID} not found` };
  }

  return { valid: true };
}

// =============================================================================
// ROUTE HANDLERS
// =============================================================================

/**
 * POST /api/auth/backchannel-logout
 *
 * Receives logout token from Keycloak and invalidates sessions.
 *
 * Enhanced with token blacklisting for immediate access token revocation.
 */
export async function POST(request: NextRequest) {
  const startTime = Date.now();

  try {
    // 1. Validate content type
    const contentType = request.headers.get('content-type');
    if (!contentType?.includes('application/x-www-form-urlencoded')) {
      console.warn('[BackChannelLogout] Invalid content-type:', contentType);
      return new NextResponse('Invalid content-type', { status: 400 });
    }

    // 2. Extract logout token
    const formData = await request.formData();
    const logoutToken = formData.get('logout_token');

    if (!logoutToken || typeof logoutToken !== 'string') {
      console.warn('[BackChannelLogout] Missing logout_token');
      return new NextResponse('Missing logout_token', { status: 400 });
    }

    // 3. Verify JWT signature
    let payload: LogoutTokenPayload;
    try {
      const jwks = getJwksClient();
      const { payload: verifiedPayload } = await jwtVerify(logoutToken, jwks, {
        issuer: KEYCLOAK_ISSUER,
        audience: KEYCLOAK_CLIENT_ID,
        clockTolerance: 30,
      });
      payload = verifiedPayload as LogoutTokenPayload;
    } catch (jwtError) {
      const error = jwtError as Error;
      console.error('[BackChannelLogout] JWT verification failed:', error.message);
      return new NextResponse(`Invalid logout token: ${error.message}`, { status: 400 });
    }

    // 4. Validate claims
    const validation = validateLogoutTokenClaims(payload);
    if (!validation.valid) {
      console.error('[BackChannelLogout] Claim validation failed:', validation.error);
      return new NextResponse(validation.error, { status: 400 });
    }

    // 5. Invalidate sessions AND blacklist tokens
    let invalidatedCount = 0;

    if (payload.sub) {
      // Uses the enhanced function that:
      // 1. Finds all sessions for this user (via index or scan)
      // 2. Extracts and blacklists each session's access token
      // 3. Deletes the sessions
      // 4. Cleans up the user session index
      invalidatedCount = await invalidateUserSessionsWithBlacklist(payload.sub);
      console.log(`[BackChannelLogout] Invalidated ${invalidatedCount} sessions (with token blacklist) for user: ${maskValue(payload.sub)}`);
    }

    // 6. Return success
    const duration = Date.now() - startTime;
    console.log(`[BackChannelLogout] Completed in ${duration}ms, invalidated: ${invalidatedCount}`);

    return new NextResponse(null, {
      status: 200,
      headers: { 'Cache-Control': 'no-store' },
    });
  } catch (error) {
    const err = error as Error;
    console.error('[BackChannelLogout] Unexpected error:', err.message);
    return new NextResponse('Internal error', { status: 400 });
  }
}

/**
 * GET /api/auth/backchannel-logout
 *
 * Health check endpoint
 */
export async function GET() {
  return NextResponse.json({
    status: 'ok',
    endpoint: 'backchannel-logout',
    issuer: KEYCLOAK_ISSUER,
    features: [
      'JWT signature validation',
      'Session invalidation',
      'Token blacklisting',
      'User session indexing',
    ],
  });
}

function maskValue(value: string): string {
  if (value.length <= 8) return '****';
  return `${value.substring(0, 4)}...${value.substring(value.length - 4)}`;
}
