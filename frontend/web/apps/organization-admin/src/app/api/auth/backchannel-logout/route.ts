/**
 * Back-Channel Logout Endpoint
 *
 * Receives logout tokens from Keycloak when a user logs out from any client
 * in the SSO session. Uses the shared Better Auth backchannel logout handler.
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 */

import { NextRequest, NextResponse } from 'next/server';
import { handleBackchannelLogout, env } from '@/lib/auth';

export async function POST(request: NextRequest) {
  const startTime = Date.now();

  try {
    // Step 1: Validate content type
    const contentType = request.headers.get('content-type');
    if (!contentType?.includes('application/x-www-form-urlencoded')) {
      console.warn('[BackChannelLogout] Invalid content-type:', contentType);
      return new NextResponse('Invalid content-type', { status: 400 });
    }

    // Step 2: Extract logout token
    const formData = await request.formData();
    const logoutToken = formData.get('logout_token');

    if (!logoutToken || typeof logoutToken !== 'string') {
      console.warn('[BackChannelLogout] Missing logout_token');
      return new NextResponse('Missing logout_token', { status: 400 });
    }

    // Step 3: Get the shared backchannel logout handler
    if (!handleBackchannelLogout) {
      console.error('[BackChannelLogout] Handler not available (Redis not enabled?)');
      return new NextResponse('Backchannel logout not configured', { status: 503 });
    }

    // Step 4: Process the logout token
    const result = await handleBackchannelLogout(logoutToken);

    if (!result.success) {
      console.error('[BackChannelLogout] Handler failed:', result.error);
      return new NextResponse(result.error || 'Invalid logout token', { status: 400 });
    }

    // Step 5: Return success
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

export async function GET() {
  return NextResponse.json({
    status: 'ok',
    endpoint: 'backchannel-logout',
    issuer: env.KEYCLOAK_ISSUER,
  });
}
