/**
 * Complete Logout Endpoint
 *
 * Uses Better Auth's official signOut API for proper session cleanup.
 * Additionally blacklists the access token JTI for defense-in-depth.
 *
 * @see https://better-auth.com/docs/concepts/session-management
 */

import { NextRequest, NextResponse } from 'next/server';
import { auth, db, jtiBlacklist } from '@/lib/auth';
import { decodeJwt } from 'jose';

export async function POST(request: NextRequest) {
  const startTime = Date.now();
  const results = {
    sessionFound: false,
    tokenBlacklisted: false,
    signedOut: false,
  };

  try {
    // Step 1: Get current session
    const sessionResult = await auth.api.getSession({
      headers: request.headers,
    });

    if (!sessionResult?.session) {
      console.log('[Logout] No active session found');
      return NextResponse.json({
        success: true,
        message: 'No active session',
        results,
      });
    }

    results.sessionFound = true;
    const userId = sessionResult.user?.id;

    console.log('[Logout] Session found for user:', userId?.slice(0, 8) + '...');

    // Step 2: Blacklist access token JTI (defense-in-depth)
    if (jtiBlacklist) {
      try {
        const account = await db.collection('account').findOne({
          userId: userId,
          providerId: 'keycloak',
        });

        if (account?.accessToken) {
          try {
            const payload = decodeJwt(account.accessToken);
            if (payload.jti && payload.sub) {
              await jtiBlacklist.add({
                jti: payload.jti,
                userId: payload.sub as string,
                reason: 'session_revoke',
                tokenExpiry: payload.exp as number | undefined,
              });
              results.tokenBlacklisted = true;
              console.log('[Logout] Access token JTI blacklisted');
            }
          } catch (decodeError) {
            console.warn('[Logout] Failed to decode access token:', decodeError);
          }
        }
      } catch (blacklistError) {
        console.warn('[Logout] Token blacklisting failed (non-critical):', blacklistError);
      }
    }

    // Step 3: Sign out via Better Auth (handles all cleanup)
    try {
      await auth.api.signOut({
        headers: request.headers,
      });
      results.signedOut = true;
      console.log('[Logout] Better Auth signOut completed');
    } catch (signOutError) {
      console.error('[Logout] Better Auth signOut failed:', signOutError);
    }

    const duration = Date.now() - startTime;
    console.log(`[Logout] Complete in ${duration}ms:`, results);

    return NextResponse.json({
      success: true,
      results,
      duration,
    });
  } catch (error) {
    const err = error as Error;
    console.error('[Logout] Failed:', err.message);

    // Attempt signOut even on error
    try {
      await auth.api.signOut({ headers: request.headers });
    } catch {
      // Ignore cleanup error
    }

    return NextResponse.json(
      {
        success: false,
        error: err.message,
        results,
      },
      { status: 500 }
    );
  }
}

export async function GET() {
  return NextResponse.json({
    endpoint: 'complete-logout',
    description: 'Better Auth signOut with JTI blacklisting',
    method: 'POST',
  });
}
