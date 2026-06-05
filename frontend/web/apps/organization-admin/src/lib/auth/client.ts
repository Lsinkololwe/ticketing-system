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
 */

'use client';

import { createAuthClient } from 'better-auth/react';
import { genericOAuthClient } from 'better-auth/client/plugins';

// =============================================================================
// CONFIGURATION
// =============================================================================

const APP_URL = process.env.NEXT_PUBLIC_APP_URL;
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL;
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM;
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID;

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

/**
 * Register with Keycloak
 *
 * SECURITY: Uses Better Auth's OAuth flow with kc_action=register parameter.
 * This ensures PKCE, state, and all security measures are properly handled.
 */
export async function registerWithKeycloak(callbackURL?: string) {
  return authClient.signIn.oauth2({
    providerId: 'keycloak',
    callbackURL: callbackURL || '/dashboard',
    requestSignUp: true,
  });
}

// =============================================================================
// SIGN OUT
// =============================================================================

/**
 * Simple sign out - Better Auth only
 *
 * Clears:
 * - Better Auth session in Redis/MongoDB
 * - Session cookie (server-side deletion)
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
 *    - Delete the Better Auth session from Redis/MongoDB
 *    - Blacklist access tokens
 *    - Clean up user session index
 * 2. Redirect to Keycloak end_session_endpoint
 * 3. Keycloak terminates SSO session
 * 4. Redirect back to login page
 *
 * Result:
 * - User logged out from this app
 * - User logged out from all SSO apps
 * - Tokens blacklisted
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
    logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID as string);
    logoutUrl.searchParams.set('post_logout_redirect_uri', `${APP_URL}/login`);

    // 3. Redirect to Keycloak logout - this terminates SSO session
    window.location.href = logoutUrl.toString();
  } catch (error) {
    console.error('[Auth] Complete logout failed:', error);
    // Fallback: Redirect to login
    window.location.href = '/login';
  }
}

/**
 * Sign out with Keycloak SSO termination (alias for signOutComplete)
 *
 * @deprecated Use signOutComplete() instead
 */
export const signOutWithKeycloak = signOutComplete;

// =============================================================================
// SESSION MANAGEMENT (Multi-Device Support)
// =============================================================================

/**
 * List all active sessions for the current user
 */
export async function listActiveSessions() {
  return authClient.listSessions();
}

/**
 * Revoke a specific session by token
 */
export async function revokeSession(token: string) {
  return authClient.revokeSession({ token });
}

/**
 * Revoke all other sessions except the current one
 */
export async function revokeOtherSessions() {
  return authClient.revokeOtherSessions();
}

/**
 * Revoke ALL sessions including the current one
 */
export async function revokeAllSessions() {
  return authClient.revokeSessions();
}

/**
 * Revoke all sessions and sign out completely
 *
 * Combines revokeAllSessions with SSO logout.
 * Use when user changes password or suspects account compromise.
 */
export async function revokeAllAndSignOut() {
  try {
    await authClient.revokeSessions();
    await signOutComplete();
  } catch (error) {
    console.error('[Auth] Revoke all and sign out failed:', error);
    window.location.href = '/login';
  }
}

// =============================================================================
// EXPORTS
// =============================================================================

export { authClient };
export type AuthClient = typeof authClient;
