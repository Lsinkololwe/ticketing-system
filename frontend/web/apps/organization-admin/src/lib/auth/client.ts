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
 * - **Sidebar** (`/components/layout/Sidebar.tsx`): Uses `signOutComplete()` for logout
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
 * @used-by signOutComplete() - Builds Keycloak logout redirect URL
 */
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? 'http://localhost:8084';

/**
 * Keycloak realm name
 * @used-by signOutComplete() - Part of Keycloak logout URL
 */
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? 'myticketzm';

/**
 * Keycloak client ID for this application
 * @used-by signOutComplete() - Required for Keycloak logout
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
 * Fetches the Keycloak access token from the server API.
 * The token is stored securely on the server and retrieved via authenticated request.
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
    // Fetch access token from server API
    // Server validates session and returns token from accounts collection
    const response = await fetch('/api/auth/access-token', {
      credentials: 'include', // Include session cookies
    });

    if (!response.ok) {
      if (response.status === 401) {
        return null; // Not authenticated
      }
      throw new Error(`Failed to get access token: ${response.status}`);
    }

    const data = await response.json();

    if (data.expired) {
      console.warn('[Auth] Access token expired, need to re-authenticate');
      return null;
    }

    return data.accessToken || null;
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

/**
 * Basic sign out (Better Auth only)
 *
 * Clears the Better Auth session but does NOT terminate Keycloak SSO.
 * For complete logout, use signOutComplete() instead.
 *
 * @used-by Internal use only - prefer signOutComplete() for user-facing logout
 */
export const signOut = authClient.signOut;

// =============================================================================
// KEYCLOAK SSO LOGOUT (Complete)
// =============================================================================

/**
 * Complete sign out with Keycloak SSO termination
 *
 * This is the RECOMMENDED logout method for production.
 *
 * ## What it does:
 * 1. Calls Better Auth signOut to clear session in MongoDB/Redis
 * 2. Redirects to Keycloak end_session_endpoint
 * 3. Keycloak terminates the SSO session
 * 4. Keycloak redirects back to /login
 *
 * ## Why use this instead of signOut():
 * - signOut() only clears Better Auth session
 * - User stays logged into Keycloak SSO
 * - Next login attempt auto-authenticates (bad UX for logout)
 * - signOutComplete() ensures full SSO termination
 *
 * @example
 * ```tsx
 * // In a logout button
 * <Button onClick={signOutComplete}>Sign Out</Button>
 * ```
 *
 * @used-by
 * - Header logout button
 * - Sidebar logout menu item
 * - Session expired handler
 */
export async function signOutComplete() {
  try {
    // Step 1: Sign out from Better Auth
    // Clears session from MongoDB and Redis, removes cookies
    await authClient.signOut();

    // Step 2: Build Keycloak end_session URL
    // https://www.keycloak.org/docs/latest/securing_apps/#logout
    const logoutUrl = new URL(
      `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`
    );
    logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
    logoutUrl.searchParams.set('post_logout_redirect_uri', `${APP_URL}/login`);

    // Step 3: Redirect to Keycloak logout
    // This terminates the SSO session and redirects back to /login
    window.location.href = logoutUrl.toString();
  } catch (error) {
    console.error('[Auth] Complete logout failed:', error);
    // Fallback: redirect to login even if signOut failed
    window.location.href = '/login';
  }
}

