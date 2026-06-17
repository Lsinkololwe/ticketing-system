/**
 * Complete Logout Endpoint
 *
 * SECURITY: This endpoint performs comprehensive logout with token revocation.
 *
 * What happens:
 * 1. Extracts session and any stored tokens from Redis
 * 2. Blacklists the access token JTI (if available)
 * 3. Creates a user revocation entry (defense-in-depth)
 * 4. Removes session from user index
 * 5. Deletes the Better Auth session from Redis
 * 6. Returns success for Keycloak SSO redirect
 *
 * The client then redirects to Keycloak's end_session endpoint to:
 * - Terminate the Keycloak SSO session
 * - Trigger backchannel-logout to all connected clients
 *
 * @see docs/TOKEN_VALIDATION_ARCHITECTURE_RECOMMENDATION.md
 */

import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { auth } from '@/lib/auth';
import {
  blacklistTokenFromJwt,
  removeSessionFromUserIndex,
  revokeUserSessions,
  getRedis,
} from '@/lib/auth/token-blacklist';

// =============================================================================
// TYPES
// =============================================================================

interface BetterAuthSession {
  session: {
    id: string;
    userId: string;
    token: string;
    expiresAt: Date;
  };
  user: {
    id: string;
    email: string;
    name?: string;
  };
}

// =============================================================================
// ROUTE HANDLER
// =============================================================================

/**
 * POST /api/auth/logout
 *
 * Performs complete logout with token blacklisting.
 *
 * Response:
 * - 200: Logout successful
 *   - tokenBlacklisted: Whether access token was blacklisted
 *   - userRevoked: Whether user revocation entry was created
 *   - sessionDeleted: Whether session was deleted
 * - 500: Internal error (still attempts cleanup)
 */
export async function POST(request: NextRequest) {
  const startTime = Date.now();
  const results = {
    tokenBlacklisted: false,
    userRevoked: false,
    sessionDeleted: false,
    sessionIndexCleaned: false,
  };

  try {
    // 1. Get current session cookie
    const cookieStore = await cookies();
    const sessionCookie = cookieStore.get('pml_session');

    if (!sessionCookie?.value) {
      console.log('[Logout] No session cookie found - user may already be logged out');
      return NextResponse.json({
        success: true,
        ...results,
        message: 'No active session',
      });
    }

    // 2. Get session data from Better Auth
    let sessionData: BetterAuthSession | null = null;
    try {
      const result = await auth.api.getSession({
        headers: request.headers,
      });
      sessionData = result as BetterAuthSession | null;
      console.log('[Logout] Session retrieved:', {
        hasSession: !!sessionData?.session,
        hasUser: !!sessionData?.user,
        userId: sessionData?.user?.id ? `${sessionData.user.id.slice(0, 8)}...` : null,
      });
    } catch (sessionError) {
      console.warn('[Logout] Failed to get session from Better Auth:', sessionError);
    }

    // 3. Try to get stored tokens from Redis session
    if (sessionData?.session) {
      const redis = getRedis();
      const sessionKey = `pml-admin:session:${sessionData.session.id}`;

      try {
        const storedSession = await redis.get(sessionKey);
        if (storedSession) {
          const sessionObj = JSON.parse(storedSession);
          console.log('[Logout] Session data from Redis:', {
            hasAccessToken: !!sessionObj.accessToken,
            hasToken: !!sessionObj.token,
            hasIdToken: !!sessionObj.idToken,
          });

          // 4. Blacklist access token if available
          const accessToken = sessionObj.accessToken || sessionObj.token;
          if (accessToken && typeof accessToken === 'string' && accessToken.includes('.')) {
            results.tokenBlacklisted = await blacklistTokenFromJwt(accessToken);
            console.log(`[Logout] Access token blacklisted: ${results.tokenBlacklisted}`);
          }
        }
      } catch (redisError) {
        console.warn('[Logout] Failed to read session from Redis:', redisError);
      }
    }

    // 5. Create user revocation entry (defense-in-depth)
    // This ensures that even if token blacklisting fails, we have a record
    if (sessionData?.user?.id) {
      try {
        await revokeUserSessions(sessionData.user.id);
        results.userRevoked = true;
        console.log(`[Logout] User revocation entry created for: ${sessionData.user.id.slice(0, 8)}...`);
      } catch (revokeError) {
        console.warn('[Logout] Failed to create user revocation:', revokeError);
      }
    }

    // 6. Clean up user session index
    if (sessionData?.session && sessionData?.user) {
      try {
        await removeSessionFromUserIndex(sessionData.user.id, sessionData.session.id);
        results.sessionIndexCleaned = true;
        console.log('[Logout] Session removed from user index');
      } catch (indexError) {
        console.warn('[Logout] Failed to remove session from index:', indexError);
      }
    }

    // 7. Sign out from Better Auth (deletes session from Redis + clears cookie)
    try {
      await auth.api.signOut({
        headers: request.headers,
      });
      results.sessionDeleted = true;
      console.log('[Logout] Better Auth signOut completed');
    } catch (signOutError) {
      console.warn('[Logout] Better Auth signOut failed:', signOutError);
    }

    const duration = Date.now() - startTime;
    console.log(`[Logout] Complete logout finished in ${duration}ms:`, results);

    return NextResponse.json({
      success: true,
      ...results,
    });
  } catch (error) {
    const err = error as Error;
    console.error('[Logout] Complete logout failed:', err.message);

    // Attempt cleanup even on error
    try {
      await auth.api.signOut({ headers: request.headers });
    } catch {
      // Ignore
    }

    return NextResponse.json(
      {
        success: false,
        error: 'Logout failed',
        message: err.message,
        ...results,
      },
      { status: 500 }
    );
  }
}

/**
 * GET /api/auth/logout
 *
 * Health check / info endpoint
 */
export async function GET() {
  return NextResponse.json({
    endpoint: 'logout',
    description: 'Enhanced logout with token blacklisting and session revocation',
    method: 'POST',
    securityFeatures: [
      'Access token blacklisting (JTI in Redis)',
      'User session revocation entry',
      'Session index cleanup',
      'Better Auth session deletion',
      'Keycloak SSO logout redirect (client-side)',
    ],
  });
}
