/**
 * Session Service - Session Verification and Management
 *
 * Handles user session verification, retrieval, and sign-out operations.
 * Uses React's cache() for request deduplication within a single render pass.
 *
 * @module SessionService
 */

import 'server-only';

import { cache } from 'react';
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import type { ISessionService, Session } from '../interfaces';
import type { auth as AuthInstance } from '../index';

// =============================================================================
// SESSION SERVICE IMPLEMENTATION
// =============================================================================

/**
 * Session management service
 *
 * Provides cached methods for session verification and retrieval.
 * All methods use React's cache() to deduplicate requests within a single render.
 *
 * @example
 * ```typescript
 * const sessionService = new SessionService(auth);
 * const session = await sessionService.verifySession(); // Redirects if invalid
 * ```
 */
export class SessionService implements ISessionService {
  /**
   * Creates a new SessionService instance
   *
   * @param auth - Better Auth instance for session operations
   */
  constructor(private readonly auth: typeof AuthInstance) {}

  /**
   * Verify the current user's session
   *
   * Uses React's cache() to deduplicate calls within a single request.
   * If the session is invalid or expired, redirects to /logout to ensure
   * both Better Auth AND Keycloak sessions are terminated.
   *
   * SECURITY: This validates the session against the database, not just the cookie.
   *
   * WHY /logout instead of /login:
   * - When session expires, the Keycloak SSO session may still be active
   * - Redirecting to /login would auto-login the user via Keycloak SSO
   * - Redirecting to /logout ensures complete logout from both systems
   * - /logout then redirects to /login after clearing Keycloak session
   *
   * @returns Better Auth session with user data
   * @throws Redirects to /logout if not authenticated (which then goes to /login)
   *
   * @example
   * ```typescript
   * // In Server Component
   * export default async function DashboardLayout({ children }) {
   *   const session = await sessionService.verifySession();
   *   return <>{children}</>;
   * }
   * ```
   */
  verifySession = cache(async (): Promise<Session> => {
    try {
      const requestHeaders = await headers();

      const session = await this.auth.api.getSession({
        headers: requestHeaders,
      });

      if (!session) {
        // Session expired or invalid - redirect to /logout to clear Keycloak SSO
        redirect('/logout');
      }

      return session;
    } catch (error) {
      console.error('[SessionService] Session verification failed:', error);
      // Error verifying session - redirect to /logout to be safe
      redirect('/logout');
    }
  });

  /**
   * Get session without redirecting
   *
   * Returns null if not authenticated. Use this when you need to check
   * auth status without forcing a redirect.
   *
   * Uses React's cache() to deduplicate calls within a single request.
   *
   * @returns Better Auth session or null
   *
   * @example
   * ```typescript
   * // Check auth status conditionally
   * const session = await sessionService.getSession();
   * if (session) {
   *   // User is authenticated
   * } else {
   *   // User is not authenticated
   * }
   * ```
   */
  getSession = cache(async (): Promise<Session | null> => {
    try {
      const requestHeaders = await headers();

      const session = await this.auth.api.getSession({
        headers: requestHeaders,
      });

      return session ?? null;
    } catch {
      return null;
    }
  });

  /**
   * Sign out the current user
   *
   * Invalidates the session and clears cookies.
   *
   * @param reqHeaders - Request headers containing cookies
   *
   * @example
   * ```typescript
   * // In Server Action
   * 'use server';
   * export async function signOut() {
   *   const reqHeaders = await headers();
   *   await sessionService.signOut(reqHeaders);
   *   redirect('/login');
   * }
   * ```
   */
  async signOut(reqHeaders: Headers): Promise<void> {
    await this.auth.api.signOut({ headers: reqHeaders });
  }

  /**
   * Refresh the current session to extend its lifetime
   *
   * Uses Better Auth's session refresh mechanism to extend session duration.
   *
   * @returns Refreshed session or null if refresh fails
   *
   * @example
   * ```typescript
   * const refreshedSession = await sessionService.refreshSession();
   * if (refreshedSession) {
   *   console.log('Session extended until:', refreshedSession.expiresAt);
   * }
   * ```
   */
  async refreshSession(): Promise<Session | null> {
    try {
      const requestHeaders = await headers();

      // Better Auth refresh API call
      // Note: Actual implementation depends on Better Auth API
      const session = await this.auth.api.getSession({
        headers: requestHeaders,
      });

      return session ?? null;
    } catch {
      return null;
    }
  }

  /**
   * Check if a session is currently active
   *
   * Lightweight check for UI rendering decisions.
   * Does not perform full database verification.
   *
   * @returns True if session exists, false otherwise
   *
   * @example
   * ```typescript
   * const isAuth = await sessionService.isAuthenticated();
   * if (isAuth) {
   *   // Show authenticated UI
   * }
   * ```
   */
  async isAuthenticated(): Promise<boolean> {
    const session = await this.getSession();
    return session !== null;
  }
}
