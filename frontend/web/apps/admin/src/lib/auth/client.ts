/**
 * Admin App - Better Auth Client
 *
 * Client-side authentication using official Better Auth patterns.
 *
 * ## Architecture Overview
 *
 * This file provides client-side authentication for React components in the
 * admin app. It uses Better Auth's official client API.
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
 * @module apps/admin/src/lib/auth/client
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
const APP_URL = process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3030';

/**
 * Keycloak server URL
 * @used-by signOutComplete() - Builds Keycloak logout redirect URL
 */
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8084';

/**
 * Keycloak realm name
 * @used-by signOutComplete() - Part of Keycloak logout URL
 */
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM || 'myticketzm';

/**
 * Keycloak client ID for this application
 * @used-by signOutComplete() - Required for Keycloak logout
 */
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'myticketzm-admin';

// =============================================================================
// AUTH CLIENT
// =============================================================================

/**
 * Better Auth client for admin app
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
 * @used-by
 * - Login page for OAuth sign-in
 * - Header component for user display
 * - Protected routes for auth checking
 */
export const authClient = createAuthClient({
  baseURL: APP_URL,
  plugins: [genericOAuthClient()],
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
 * Get session (non-reactive, for one-time checks)
 *
 * Unlike useSession hook, this doesn't subscribe to changes.
 * Use for initialization or one-time auth checks.
 *
 * @example
 * ```tsx
 * // In an effect or action
 * const session = await getSession();
 * if (!session) redirect('/login');
 * ```
 *
 * @used-by
 * - Form submissions that need current user ID
 * - API calls that need auth token
 */
export const getSession = authClient.getSession;

/**
 * Sign in methods from Better Auth client
 *
 * @example
 * ```tsx
 * // OAuth sign in (Keycloak)
 * await signIn.oauth2({ providerId: 'keycloak', callbackURL: '/dashboard' });
 * ```
 *
 * @used-by
 * - Login page sign-in button
 */
export const signIn = authClient.signIn;

/**
 * Sign in with Keycloak OAuth (convenience function)
 *
 * Initiates OAuth flow with Keycloak as the provider.
 *
 * @param callbackURL - Where to redirect after successful auth (default: '/dashboard')
 * @returns Promise that resolves with the OAuth response containing redirect URL
 *
 * @example
 * ```tsx
 * // Basic usage
 * await signInWithKeycloak();
 *
 * // With custom callback
 * <Button onClick={() => signInWithKeycloak('/events')}>Sign In</Button>
 * ```
 *
 * @used-by
 * - Login page sign-in button
 */
export function signInWithKeycloak(callbackURL = '/dashboard') {
  return authClient.signIn.oauth2({
    providerId: 'keycloak',
    callbackURL,
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

/**
 * Get the Keycloak logout URL for manual navigation
 *
 * Use this when you need the URL without performing the logout.
 * For example, to show in a confirmation dialog.
 *
 * @param postLogoutRedirectUri - Where to redirect after Keycloak logout
 * @returns Full Keycloak logout URL
 *
 * @example
 * ```tsx
 * const logoutUrl = getKeycloakLogoutUrl('/goodbye');
 * // Use in a link or redirect
 * ```
 *
 * @used-by
 * - Logout confirmation dialogs
 * - External logout links
 */
export function getKeycloakLogoutUrl(postLogoutRedirectUri?: string): string {
  const logoutUrl = new URL(
    `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`
  );
  logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID);
  logoutUrl.searchParams.set('post_logout_redirect_uri', postLogoutRedirectUri || `${APP_URL}/login`);
  return logoutUrl.toString();
}

// =============================================================================
// TYPE EXPORTS
// =============================================================================

/**
 * Type of the auth client instance
 * Use for typing props that accept the client
 */
export type AuthClient = typeof authClient;
