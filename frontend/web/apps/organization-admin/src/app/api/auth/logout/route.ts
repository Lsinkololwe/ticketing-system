/**
 * Logout Endpoint with JTI Blacklisting
 *
 * Handles user-initiated logout:
 * 1. Revokes the access token, SSO session and user via identity-service (durable)
 * 2. Signs out via Better Auth (clears the local session)
 * 3. Returns the Keycloak logout URL for SSO termination
 *
 * Responds 207 when the local session was cleared but the revocation did not persist.
 *
 * @see https://better-auth.com/docs/concepts/session-management
 */

import { NextRequest, NextResponse } from 'next/server';
import { auth, db } from '@/lib/auth';
import { revocationService } from '@/lib/auth/revocation';
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
  const results: {
    sessionFound: boolean;
    /** True only when the revocation reached identity-service's system of record. */
    jtiBlacklisted: boolean;
    signedOut: boolean;
    /** Present when the token could not be revoked. It is still valid until it expires. */
    revocationError?: string;
  } = {
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
    // Step 2: Revoke the Keycloak credentials durably
    // =========================================================================
    // identity-service owns the revocation record and persists it to MongoDB before caching it
    // in Redis, so the revocation survives a cache outage and is visible to every backend
    // service. All three identifiers are sent: `jti` covers the token in hand, `sid` every
    // token minted for the same SSO session, and `sub` the user.
    if (db) {
      try {
        const account = await db.collection('account').findOne({
          userId: userId,
          providerId: 'keycloak',
        });

        if (account?.accessToken) {
          const payload = decodeJwt(account.accessToken);

          const outcome = await revocationService.revokeSignOut({
            jti: payload.jti as string | undefined,
            sid: payload.sid as string | undefined,
            sub: (payload.sub as string) || userId,
            reason: 'user_logout',
            revokedBy: userId,
          });

          results.jtiBlacklisted = outcome.persisted;
          results.revocationError = outcome.error;

          if (outcome.persisted) {
            console.log(
              `[Logout] Revoked ${outcome.identifiersRevoked} identifier(s), cached=${outcome.cachePrimed}`
            );
          } else {
            // The local session is cleared regardless, but the access token stays valid until it
            // expires, so the outcome is reported rather than dropped.
            console.error('[Logout] Revocation was NOT persisted:', outcome.error);
          }
        } else {
          console.log('[Logout] No Keycloak access token on file — nothing to revoke');
          results.jtiBlacklisted = true;
        }
      } catch (revocationError) {
        const message =
          revocationError instanceof Error ? revocationError.message : String(revocationError);
        results.revocationError = message;
        console.error('[Logout] Revocation failed:', message);
      }
    } else {
      results.revocationError = 'account store unavailable';
      console.error('[Logout] Cannot resolve the access token to revoke it');
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

    // The user is redirected either way; 207 distinguishes a sign-out that cleared the local
    // session from one that also revoked the access token centrally.
    return NextResponse.json(
      {
        success: results.jtiBlacklisted,
        partial: !results.jtiBlacklisted,
        warning: results.jtiBlacklisted
          ? undefined
          : 'Signed out on this device, but the access token could not be revoked centrally. ' +
            'It remains valid until it expires.',
        logoutUrl: buildKeycloakLogoutUrl(),
        results,
        duration,
      },
      { status: results.jtiBlacklisted ? 200 : 207 }
    );

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
    description: 'User-initiated logout with durable token revocation',
    method: 'POST',
    flow: [
      '1. Revoke jti, sid and sub via identity-service',
      '2. Clear Better Auth session',
      '3. Return Keycloak logout URL',
    ],
  });
}
