/**
 * Back-Channel Logout Endpoint (Admin)
 *
 * Receives logout tokens from Keycloak when a user logs out from any client in
 * the SSO session, enabling immediate session invalidation. Delegates all JWT
 * verification, claim validation, and session/token invalidation to the shared
 * Better Auth backchannel logout handler (`BackchannelLogoutHandler`) — the same
 * class-based service the organization-admin app uses.
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 */

import { NextRequest, NextResponse } from 'next/server';
import { handleBackchannelLogout, env } from '@/lib/auth';

/**
 * POST /api/auth/backchannel-logout
 *
 * Receives the logout token from Keycloak and invalidates the user's sessions
 * (and blacklists their tokens) via the shared handler.
 */
export async function POST(request: NextRequest) {
  const startTime = Date.now();

  try {
    // 1. Validate content type (OIDC back-channel uses form-encoded)
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

    // 3. Shared handler (null when Redis is not enabled)
    if (!handleBackchannelLogout) {
      console.error('[BackChannelLogout] Handler not available (Redis not enabled?)');
      return new NextResponse('Backchannel logout not configured', { status: 503 });
    }

    // 4. Verify + invalidate via the shared service
    const result = await handleBackchannelLogout(logoutToken);
    if (!result.success) {
      console.error('[BackChannelLogout] Handler failed:', result.error);
      return new NextResponse(result.error || 'Invalid logout token', { status: 400 });
    }

    const duration = Date.now() - startTime;
    console.log(`[BackChannelLogout] Completed in ${duration}ms`);

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
 * GET /api/auth/backchannel-logout — health/info endpoint.
 */
export async function GET() {
  return NextResponse.json({
    status: 'ok',
    endpoint: 'backchannel-logout',
    issuer: env?.KEYCLOAK_ISSUER,
    handler: 'shared BackchannelLogoutHandler',
  });
}
