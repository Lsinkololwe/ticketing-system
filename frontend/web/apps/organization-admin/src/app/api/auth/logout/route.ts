/**
 * Logout Endpoint with JTI Blacklisting
 *
 * Handles user-initiated logout with defense-in-depth:
 * 1. Blacklists the access token JTI in Redis
 * 2. Signs out via Better Auth (clears session)
 * 3. Returns Keycloak logout URL for SSO termination
 *
 * This ensures the Keycloak access token is immediately invalidated,
 * not just when backchannel logout arrives.
 *
 * @see https://better-auth.com/docs/concepts/session-management
 */

import { NextRequest, NextResponse } from 'next/server';
import { auth, db, jtiBlacklist } from '@/lib/auth';
import { decodeJwt } from 'jose';

// =============================================================================
// CONFIGURATION
// =============================================================================

const APP_URL = process.env.NEXT_PUBLIC_APP_URL ?? 'http://localhost:3031';
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? 'http://localhost:8084';
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? 'myticketzm';
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? 'myticketzm-organizer';

// =============================================================================
// LOGOUT HANDLER
// =============================================================================

export async function POST(request: NextRequest) {
  const startTime = Date.now();
  const results = {
    sessionFound: false,
    jtiBlacklisted: false,
    signedOut: false,
  };

  try {
    // =========================================================================
    // Step 1: Get current session
    // =========================================================================
    const session = await auth.api.getSession({
      headers: request.headers,
    });

    if (!session?.user) {
      console.log('[Logout] No active session, returning logout URL anyway');
      return NextResponse.json({
        success: true,
        logoutUrl: buildKeycloakLogoutUrl(),
        results,
      });
    }

    results.sessionFound = true;
    const userId = session.user.id;
    console.log('[Logout] Processing logout for user:', userId?.slice(0, 8) + '...');

    // =========================================================================
    // Step 2: Blacklist access token JTI (defense-in-depth)
    // =========================================================================
    if (jtiBlacklist && db) {
      try {
        // Find the Keycloak account to get the access token
        const account = await db.collection('account').findOne({
          userId: userId,
          providerId: 'keycloak',
        });

        if (account?.accessToken) {
          try {
            const payload = decodeJwt(account.accessToken);

            if (payload.jti) {
              await jtiBlacklist.add({
                jti: payload.jti as string,
                userId: (payload.sub as string) || userId,
                reason: 'session_revoke',
                tokenExpiry: payload.exp as number | undefined,
              });
              results.jtiBlacklisted = true;
              console.log('[Logout] Access token JTI blacklisted:', (payload.jti as string).slice(0, 8) + '...');
            }
          } catch (decodeError) {
            console.warn('[Logout] Failed to decode access token:', decodeError);
          }
        } else {
          console.log('[Logout] No access token found for user');
        }
      } catch (blacklistError) {
        // Non-critical - continue with logout
        console.warn('[Logout] JTI blacklisting failed (non-critical):', blacklistError);
      }
    } else {
      console.log('[Logout] JTI blacklist not available (Redis not enabled?)');
    }

    // =========================================================================
    // Step 3: Sign out via Better Auth
    // =========================================================================
    try {
      await auth.api.signOut({
        headers: request.headers,
      });
      results.signedOut = true;
      console.log('[Logout] Better Auth session cleared');
    } catch (signOutError) {
      console.error('[Logout] Better Auth signOut failed:', signOutError);
      // Continue - we still want to redirect to Keycloak
    }

    // =========================================================================
    // Step 4: Return success with Keycloak logout URL
    // =========================================================================
    const duration = Date.now() - startTime;
    console.log(`[Logout] Completed in ${duration}ms:`, results);

    return NextResponse.json({
      success: true,
      logoutUrl: buildKeycloakLogoutUrl(),
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

    // Still return logout URL so user can complete logout
    return NextResponse.json({
      success: false,
      error: err.message,
      logoutUrl: buildKeycloakLogoutUrl(),
      results,
    });
  }
}

// =============================================================================
// HELPERS
// =============================================================================

/**
 * Build Keycloak logout URL
 *
 * Constructs the OIDC end_session_endpoint URL for Keycloak.
 */
function buildKeycloakLogoutUrl(): string {
  const logoutUrl = new URL(
    `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`
  );
  logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
  logoutUrl.searchParams.set('post_logout_redirect_uri', `${APP_URL}/login`);
  return logoutUrl.toString();
}

// =============================================================================
// INFO ENDPOINT
// =============================================================================

export async function GET() {
  return NextResponse.json({
    endpoint: 'logout',
    description: 'User-initiated logout with JTI blacklisting',
    method: 'POST',
    flow: [
      '1. Blacklist access token JTI in Redis',
      '2. Clear Better Auth session',
      '3. Return Keycloak logout URL',
    ],
  });
}
