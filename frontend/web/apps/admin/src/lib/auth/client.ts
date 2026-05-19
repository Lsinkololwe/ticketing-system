/**
 * Better Auth Client (React)
 *
 * Provides client-side authentication utilities:
 * - useSession: React hook for accessing session in Client Components
 * - signIn: Initiate sign-in with Keycloak
 * - signOut: Sign out and clear session
 * - signOutComplete: Enhanced logout with Keycloak SSO termination
 *
 * IMPORTANT: Uses 'better-auth/react' for React hook support
 *
 * @see https://better-auth.com/docs/integrations/next
 * @see docs/TOKEN_VALIDATION_ARCHITECTURE_RECOMMENDATION.md
 */

'use client';

import { createAuthClient } from 'better-auth/react';
import { genericOAuthClient } from 'better-auth/client/plugins';

// =============================================================================
// CONFIGURATION
// =============================================================================

const APP_URL = process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3030';
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8084';
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM || 'event-ticketing';
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'event-ticketing-admin';

// Keycloak OIDC endpoints
const KEYCLOAK_END_SESSION_ENDPOINT = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`;

// Create React auth client with Keycloak OAuth support
const authClient = createAuthClient({
  baseURL: APP_URL,
  plugins: [genericOAuthClient()],
});

// =============================================================================
// HOOK EXPORTS
// =============================================================================

/**
 * Hook to access session in Client Components
 *
 * Usage:
 * ```tsx
 * const { data: session, isPending, error } = useSession();
 * ```
 */
export const useSession = authClient.useSession;

/**
 * Get session (non-reactive, for one-time checks)
 */
export const getSession = authClient.getSession;

// =============================================================================
// SIGN IN
// =============================================================================

/**
 * Sign in with Keycloak
 * Redirects to Keycloak login page
 */
export async function signInWithKeycloak(callbackURL?: string) {
  return authClient.signIn.oauth2({
    providerId: 'keycloak',
    callbackURL: callbackURL || '/dashboard',
  });
}

// =============================================================================
// SIGN OUT - MULTIPLE OPTIONS
// =============================================================================

/**
 * Simple sign out - Better Auth only
 *
 * Clears:
 * - Better Auth session in Redis
 * - Session cookie
 *
 * Does NOT:
 * - Terminate Keycloak SSO session
 * - Blacklist access tokens
 *
 * Use this for: Quick logout without SSO propagation
 */
export async function signOut() {
  return authClient.signOut();
}

/**
 * Sign out and redirect to login page
 *
 * Same as signOut() but with redirect.
 */
export async function signOutAndRedirect() {
  await authClient.signOut({
    fetchOptions: {
      onSuccess: () => {
        window.location.href = '/login';
      },
    },
  });
}

/**
 * Complete sign out - Better Auth + Keycloak SSO
 *
 * This is the RECOMMENDED logout method for production.
 *
 * Flow:
 * 1. Call /api/auth/logout/complete to:
 *    - Delete the Better Auth session from Redis
 *    - Clean up user session index
 * 2. Redirect to Keycloak end_session_endpoint
 * 3. Keycloak terminates SSO session
 * 4. Keycloak sends backchannel-logout to all clients
 * 5. Backchannel-logout handler blacklists tokens
 * 6. Redirect back to login page
 *
 * Result:
 * - User logged out from this app
 * - User logged out from all SSO apps
 * - Tokens blacklisted via backchannel-logout
 */
export async function signOutComplete() {
  try {
    // 1. Call our enhanced logout endpoint to clean up session
    const response = await fetch('/api/auth/logout/complete', {
      method: 'POST',
      credentials: 'include',
    });

    if (response.ok) {
      const data = await response.json();
      console.log('[Auth] Session cleanup completed:', data);
    } else {
      console.warn('[Auth] Session cleanup returned:', response.status);
    }

    // 2. Build Keycloak end_session URL
    const logoutUrl = new URL(KEYCLOAK_END_SESSION_ENDPOINT);
    logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
    logoutUrl.searchParams.set('post_logout_redirect_uri', `${APP_URL}/login`);

    // 3. Redirect to Keycloak logout - this terminates SSO session
    // and triggers backchannel-logout to all connected clients
    window.location.href = logoutUrl.toString();
  } catch (error) {
    console.error('[Auth] Complete logout failed:', error);

    // Fallback: Simple logout with redirect
    await signOutAndRedirect();
  }
}

/**
 * Sign out with Keycloak SSO termination (alias for signOutComplete)
 *
 * @deprecated Use signOutComplete() instead
 */
export const signOutWithKeycloak = signOutComplete;

// =============================================================================
// UTILITIES
// =============================================================================

/**
 * Get the Keycloak logout URL for manual navigation
 *
 * Useful when you need to control the redirect yourself.
 */
export function getKeycloakLogoutUrl(postLogoutRedirectUri?: string): string {
  const logoutUrl = new URL(KEYCLOAK_END_SESSION_ENDPOINT);
  logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
  logoutUrl.searchParams.set(
    'post_logout_redirect_uri',
    postLogoutRedirectUri || `${APP_URL}/login`
  );
  return logoutUrl.toString();
}

// =============================================================================
// EXPORTS
// =============================================================================

export { authClient };
export type AuthClient = typeof authClient;
