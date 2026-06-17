/**
 * Access Token API Route
 *
 * Returns the OAuth access token for the current user's Keycloak session.
 * Used by Apollo Client to authenticate with backend GraphQL services.
 *
 * Uses Better Auth's official getAccessToken API which:
 * - Gets the access token for the specified provider
 * - Automatically refreshes the token if expired
 * - Returns the current valid token
 *
 * @security
 * - Requires valid session
 * - Returns token only to authenticated users
 *
 * @see https://better-auth.com/docs/concepts/oauth
 */

import { NextResponse } from 'next/server';
import { headers } from 'next/headers';
import { auth } from '@/lib/auth';

export async function GET() {
  try {
    const requestHeaders = await headers();

    // Get the current session first
    const session = await auth.api.getSession({
      headers: requestHeaders,
    });

    if (!session?.user) {
      console.log('[AccessToken] No session found');
      return NextResponse.json({ accessToken: null }, { status: 401 });
    }

    // Use Better Auth's official API to get the access token
    // This handles token refresh automatically
    const tokenResponse = await auth.api.getAccessToken({
      body: {
        providerId: 'keycloak',
      },
      headers: requestHeaders,
    });

    if (!tokenResponse?.accessToken) {
      console.warn('[AccessToken] No access token returned for user:', session.user.id);
      return NextResponse.json({ accessToken: null }, { status: 200 });
    }

    // Log token info in development (first 20 chars only for security)
    if (process.env.NODE_ENV === 'development') {
      console.log('[AccessToken] Token retrieved successfully:', {
        userId: session.user.id,
        tokenPrefix: tokenResponse.accessToken.substring(0, 20) + '...',
      });
    }

    return NextResponse.json({ accessToken: tokenResponse.accessToken });
  } catch (error) {
    console.error('[AccessToken] Error fetching access token:', error);

    // Check if it's a "no linked account" error
    if (error instanceof Error && error.message.includes('No linked account')) {
      return NextResponse.json({ accessToken: null, error: 'No Keycloak account linked' }, { status: 200 });
    }

    return NextResponse.json({ error: 'Failed to get access token' }, { status: 500 });
  }
}
