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
import { decodeJwt } from 'jose';
import { auth, db, getTokenService, jtiBlacklist } from '@/lib/auth';

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
    keycloakSessionEnded: false,
  };
  // Keycloak ID token — required as id_token_hint for a clean RP-initiated
  // logout (terminates the SSO session without a confirmation page).
  let idTokenHint: string | undefined;

  try {
    // 1. Resolve the session the NATIVE Better Auth way — ask the auth instance,
    //    not a hardcoded cookie name. `auth.api.getSession()` reads whatever
    //    cookie Better Auth is actually configured with (prefix `pml_admin`) and
    //    validates it. The previous `cookies().get('pml_session')` check looked
    //    for a cookie that never exists, so it short-circuited EVERY logout
    //    before any teardown ran (the session was never actually cleared).
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

    // 2. No active session — nothing to tear down. Still return a front-channel
    //    Keycloak logout URL so the client can finish the redirect cleanly.
    if (!sessionData?.user) {
      console.log('[Logout] No active session - user may already be logged out');
      return NextResponse.json({
        success: true,
        logoutUrl: getTokenService().getLogoutUrl(),
        ...results,
        message: 'No active session',
      });
    }

    // 3. Read the Keycloak tokens NATIVELY from the Better Auth `account`
    //    collection (MongoDB) — the canonical store Better Auth writes OAuth
    //    tokens to. Same native source as the organization-admin app, rather
    //    than digging into a Redis session blob.
    if (sessionData?.user?.id) {
      try {
        const account = await db.collection('account').findOne({
          userId: sessionData.user.id,
          providerId: 'keycloak',
        });

        if (account) {
          console.log('[Logout] Keycloak account tokens:', {
            hasAccessToken: !!account.accessToken,
            hasIdToken: !!account.idToken,
          });

          // Capture the ID token (fallback id_token_hint for front-channel logout).
          if (typeof account.idToken === 'string') {
            idTokenHint = account.idToken;
          }

          // 3a. Back-channel logout: terminate the Keycloak SSO session
          //     server-side using the refresh token (confidential client). This
          //     is what avoids the browser-facing "Do you want to log out?" page.
          const refreshToken = account.refreshToken;
          if (typeof refreshToken === 'string' && refreshToken.length > 0) {
            results.keycloakSessionEnded =
              await getTokenService().endKeycloakSession(refreshToken);
            console.log(`[Logout] Keycloak SSO session ended: ${results.keycloakSessionEnded}`);
          }

          // 4. Blacklist the access token JTI via the shared JtiBlacklistService
          //    (skips already-expired tokens internally).
          const accessToken = account.accessToken;
          if (jtiBlacklist && typeof accessToken === 'string' && accessToken.includes('.')) {
            try {
              const payload = decodeJwt(accessToken);
              if (payload.jti) {
                results.tokenBlacklisted = await jtiBlacklist.add({
                  jti: payload.jti as string,
                  userId: (payload.sub as string) || sessionData.user.id,
                  reason: 'session_revoke',
                  tokenExpiry: payload.exp as number | undefined,
                });
                console.log(`[Logout] Access token blacklisted: ${results.tokenBlacklisted}`);
              }
            } catch (decodeError) {
              console.warn('[Logout] Failed to decode access token for blacklist:', decodeError);
            }
          }
        } else {
          console.log('[Logout] No Keycloak account found for user');
        }
      } catch (accountError) {
        console.warn('[Logout] Failed to read Keycloak account tokens:', accountError);
      }
    }

    // 5. Revoke all the user's tokens issued before now (defense-in-depth), via
    //    the shared service's user-level blacklist.
    if (jtiBlacklist && sessionData?.user?.id) {
      try {
        const nowSeconds = Math.floor(Date.now() / 1000);
        results.userRevoked = await jtiBlacklist.blacklistUserTokensBefore(
          sessionData.user.id,
          nowSeconds
        );
        console.log(`[Logout] User tokens revoked for: ${sessionData.user.id.slice(0, 8)}...`);
      } catch (revokeError) {
        console.warn('[Logout] Failed to revoke user tokens:', revokeError);
      }
    }

    // 6. Sign out from Better Auth (deletes session from Redis + clears cookie)
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

    // The Keycloak SSO session is normally terminated server-side above
    // (keycloakSessionEnded), so the client can just redirect to /login with no
    // confirmation page. `logoutUrl` is only a front-channel fallback for when
    // back-channel logout could not run (e.g. no refresh token).
    const logoutUrl = getTokenService().getLogoutUrl(idTokenHint);

    return NextResponse.json({
      success: true,
      logoutUrl,
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
