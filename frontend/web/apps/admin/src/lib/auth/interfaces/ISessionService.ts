import 'server-only';
import type { Session, SessionOptions } from './types';

/**
 * Session service interface — verification, retrieval, and termination of
 * Better Auth sessions for the admin app.
 */
export interface ISessionService {
  /**
   * Verify the current session (validated against the DB, not just the cookie).
   * Redirects to /login when there is no valid session.
   */
  verifySession(options?: SessionOptions): Promise<Session>;

  /** Retrieve the current session, or null if none (no redirect). */
  getSession(): Promise<Session | null>;

  /** Sign out: invalidate the Better Auth session. */
  signOut(headers: Headers): Promise<void>;

  /** Refresh/extend the current session, or null if it cannot be refreshed. */
  refreshSession(): Promise<Session | null>;

  /** Lightweight check — does a session exist (for UI decisions). */
  isAuthenticated(): Promise<boolean>;
}
