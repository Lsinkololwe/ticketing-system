import 'server-only';
import type { Session } from './types';
import type { SessionOptions } from './types';

/**
 * Session service interface for managing user authentication sessions.
 *
 * This service handles session verification, retrieval, and termination
 * using Better Auth's session management capabilities.
 *
 * @example
 * ```typescript
 * const sessionService: ISessionService = new SessionService();
 * const session = await sessionService.verifySession();
 * console.log('Authenticated user:', session.user.email);
 * ```
 */
export interface ISessionService {
  /**
   * Verifies the current session and returns session data.
   *
   * This method checks if a valid session exists and throws an error
   * if the session is invalid or expired. Use this for protected routes
   * where authentication is required.
   *
   * @param options - Optional configuration for session verification
   * @returns Promise resolving to the verified session
   * @throws {Error} When session is invalid or expired
   *
   * @example
   * ```typescript
   * try {
   *   const session = await sessionService.verifySession();
   *   // Session is valid, proceed with authenticated logic
   * } catch (error) {
   *   // Session is invalid, redirect to login
   * }
   * ```
   */
  verifySession(options?: SessionOptions): Promise<Session>;

  /**
   * Retrieves the current session without throwing errors.
   *
   * This method returns null if no session exists, making it suitable
   * for optional authentication checks or conditional rendering.
   *
   * @returns Promise resolving to session data or null
   *
   * @example
   * ```typescript
   * const session = await sessionService.getSession();
   * if (session) {
   *   console.log('User is logged in:', session.user.email);
   * } else {
   *   console.log('No active session');
   * }
   * ```
   */
  getSession(): Promise<Session | null>;

  /**
   * Signs out the current user and invalidates their session.
   *
   * This method terminates the Better Auth session and can optionally
   * trigger additional cleanup such as Keycloak logout.
   *
   * @param headers - Request headers for cookie management
   * @returns Promise that resolves when sign out is complete
   *
   * @example
   * ```typescript
   * const headers = new Headers();
   * await sessionService.signOut(headers);
   * // User is now logged out
   * ```
   */
  signOut(headers: Headers): Promise<void>;

  /**
   * Refreshes the current session to extend its lifetime.
   *
   * @returns Promise resolving to the refreshed session or null
   *
   * @example
   * ```typescript
   * const refreshedSession = await sessionService.refreshSession();
   * if (refreshedSession) {
   *   console.log('Session refreshed until:', refreshedSession.expiresAt);
   * }
   * ```
   */
  refreshSession(): Promise<Session | null>;

  /**
   * Checks if a session is currently active without full verification.
   *
   * This is a lightweight check suitable for UI rendering decisions.
   *
   * @returns Promise resolving to true if session exists, false otherwise
   *
   * @example
   * ```typescript
   * const isAuthenticated = await sessionService.isAuthenticated();
   * if (isAuthenticated) {
   *   // Show authenticated UI
   * }
   * ```
   */
  isAuthenticated(): Promise<boolean>;
}
