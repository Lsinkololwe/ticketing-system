/**
 * Organization Admin App - Better Auth Client
 *
 * Client-side authentication using official Better Auth patterns.
 *
 * ## Architecture Overview
 *
 * This file provides client-side authentication for React components in the
 * organization admin app. It uses Better Auth's official client API.
 *
 * ## Usage Locations
 *
 * - **Login Page** (`/login/page.tsx`): Uses `authClient.signIn.oauth2()` for Keycloak login
 * - **Header Component** (`/components/layout/Header.tsx`): Uses `useSession` hook for user display
 * - **Sidebar** (`/components/layout/Sidebar.tsx`): Uses `signOut()` for logout
 * - **Protected Components**: Use `useSession` hook for auth state
 *
 * ## Official Better Auth Patterns
 *
 * ```tsx
 * // Sign in with Keycloak
 * await authClient.signIn.oauth2({ providerId: 'keycloak', callbackURL: '/dashboard' });
 *
 * // Get session (hook - reactive)
 * const { data: session, isPending, error } = authClient.useSession();
 *
 * // Get session (one-time)
 * const session = await authClient.getSession();
 *
 * // Sign out
 * await authClient.signOut();
 * ```
 *
 * @see https://better-auth.com/docs/integrations/next
 * @module apps/organization-admin/src/lib/auth/client
 */

'use client';

import { createAuthClient } from 'better-auth/react';
import { genericOAuthClient } from 'better-auth/client/plugins';

// =============================================================================
// CONFIGURATION
// =============================================================================

/**
 * Application base URL for auth callbacks
 * @used-by authClient - Sets base URL for all auth API requests
 */
const APP_URL = process.env.NEXT_PUBLIC_APP_URL ?? 'http://localhost:3031';

/**
 * Keycloak server URL
 * @used-by signOut() - Builds Keycloak logout redirect URL
 */
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? 'http://localhost:8084';

/**
 * Keycloak realm name
 * @used-by signOut() - Part of Keycloak logout URL
 */
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? 'myticketzm';

/**
 * Keycloak client ID for this application
 * @used-by signOut() - Required for Keycloak logout
 */
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? 'myticketzm-organizer';

// =============================================================================
// AUTH CLIENT
// =============================================================================

/**
 * Better Auth client for organization admin app
 *
 * This is the main auth client instance. Use it directly with official patterns:
 *
 * ```tsx
 * // In a login button
 * onClick={() => authClient.signIn.oauth2({
 *   providerId: 'keycloak',
 *   callbackURL: '/dashboard'
 * })}
 *
 * // In a component
 * const { data: session } = authClient.useSession();
 * ```
 *
 * ## Session Expiration Handling
 *
 * Session expiration is handled at multiple layers:
 * 1. **Server-side (primary)**: `verifySession()` in layouts redirects to /logout
 * 2. **Client-side (Apollo)**: ErrorLink redirects to /logout on 401/UNAUTHENTICATED
 * 3. **Tab focus**: `refetchOnWindowFocus` re-validates when user returns
 *
 * @used-by
 * - Login page for OAuth sign-in
 * - Header component for user display
 * - Protected routes for auth checking
 */
export const authClient = createAuthClient({
  baseURL: APP_URL,
  plugins: [genericOAuthClient()],
  sessionOptions: {
    /**
     * Re-fetch session when user returns to this browser tab
     * Catches cases where session expired while user was away
     */
    refetchOnWindowFocus: true,
  },
});

// =============================================================================
// CONVENIENCE EXPORTS
// =============================================================================

/**
 * Hook to access session in Client Components
 *
 * Returns reactive session data that updates when auth state changes.
 *
 * @returns {Object} Session hook result
 * - `data` - Session object with user info, or null if not authenticated
 * - `isPending` - True while fetching session
 * - `error` - Error if session fetch failed
 *
 * @example
 * ```tsx
 * function UserProfile() {
 *   const { data: session, isPending } = useSession();
 *
 *   if (isPending) return <Spinner />;
 *   if (!session) return <LoginPrompt />;
 *
 *   return <div>Welcome, {session.user.name}</div>;
 * }
 * ```
 *
 * @used-by
 * - Header component for displaying user name/avatar
 * - Sidebar for showing user role
 * - Protected pages for auth gating
 */
export const useSession = authClient.useSession;

/**
 * Get OAuth access token for API calls
 *
 * Uses Better Auth's native client API to get the Keycloak access token.
 * Better Auth handles:
 * - Token retrieval from accounts
 * - Automatic token refresh when expired
 * - Session validation
 *
 * @returns Access token string or null if not authenticated
 *
 * @example
 * ```tsx
 * const token = await getAccessToken();
 * if (token) {
 *   fetch('/api/protected', {
 *     headers: { Authorization: `Bearer ${token}` }
 *   });
 * }
 * ```
 *
 * @used-by
 * - Apollo Client token getter
 * - REST API calls
 */
export async function getAccessToken(): Promise<string | null> {
  try {
    // Use Better Auth's native client API
    // This internally calls /api/auth/get-access-token (handled by [...all]/route.ts)
    const { data, error } = await authClient.getAccessToken({
      providerId: 'keycloak',
    });

    if (error) {
      // Handle "no linked account" gracefully
      if (error.message?.includes('No linked account')) {
        return null;
      }
      console.error('[Auth] Failed to get access token:', error.message);
      return null;
    }

    return data?.accessToken ?? null;
  } catch (error) {
    console.error('[Auth] Failed to get access token:', error);
    return null;
  }
}

/**
 * Sign in with Keycloak OAuth
 *
 * Convenience wrapper for Keycloak-specific OAuth sign-in.
 *
 * @param callbackURL - Where to redirect after successful authentication (default: '/dashboard')
 * @returns Promise that resolves with the OAuth response containing redirect URL
 *
 * @example
 * ```tsx
 * // Basic usage
 * await signInWithKeycloak();
 *
 * // With custom callback
 * await signInWithKeycloak('/events');
 * ```
 *
 * @used-by
 * - Login page auto-redirect
 * - Login button click handler
 */
export function signInWithKeycloak(callbackURL = '/dashboard') {
  return authClient.signIn.oauth2({
    providerId: 'keycloak',
    callbackURL,
  });
}

/**
 * Register with Keycloak (redirects to Keycloak registration page)
 *
 * Uses the `requestSignUp: true` hint to tell Keycloak to show
 * the registration form instead of the login form.
 *
 * @param callbackURL - Where to redirect after successful registration (default: '/login?registered=true')
 * @returns Promise that resolves with the OAuth response containing redirect URL
 *
 * @example
 * ```tsx
 * // Basic usage
 * await registerWithKeycloak();
 *
 * // With custom callback
 * await registerWithKeycloak('/onboarding');
 * ```
 *
 * @used-by
 * - Login page "Create Account" button
 * - Landing page registration CTA
 */
export function registerWithKeycloak(callbackURL = '/login?registered=true') {
  return authClient.signIn.oauth2({
    providerId: 'keycloak',
    callbackURL,
    requestSignUp: true,
  });
}

// =============================================================================
// SIGN OUT WITH JTI BLACKLISTING
// =============================================================================

/**
 * Sign out with JTI blacklisting and Keycloak SSO termination
 *
 * This is the RECOMMENDED logout method for production.
 *
 * ## What it does:
 * 1. Calls /api/auth/logout to blacklist JTI in Redis (defense-in-depth)
 * 2. Server clears Better Auth session in MongoDB/Redis
 * 3. Redirects to Keycloak end_session_endpoint
 * 4. Keycloak terminates the SSO session
 * 5. Keycloak redirects back to /login
 *
 * ## Why JTI Blacklisting?
 * - JWTs are stateless - Keycloak can't invalidate issued tokens
 * - Without blacklisting, token remains valid until expiry
 * - Blacklisting ensures immediate token invalidation
 *
 * @example
 * ```tsx
 * // In a logout button
 * <Button onClick={signOut}>Sign Out</Button>
 * ```
 *
 * @used-by
 * - Header logout button
 * - Sidebar logout menu item
 * - Session expired handler
 */
export async function signOut() {
  try {
    // Step 1: Call logout endpoint (blacklists JTI + clears session)
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    });

    const data = await response.json();

    // Step 2: Redirect to Keycloak logout URL
    // Server returns the URL with proper client_id and redirect_uri
    if (data.logoutUrl) {
      window.location.href = data.logoutUrl;
    } else {
      // Fallback: build URL client-side
      const logoutUrl = new URL(
        `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`
      );
      logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
      logoutUrl.searchParams.set('post_logout_redirect_uri', `${APP_URL}/login`);
      window.location.href = logoutUrl.toString();
    }
  } catch (error) {
    console.error('[Auth] Logout failed:', error);
    // Fallback: redirect to login even if logout failed
    window.location.href = '/login';
  }
}

