/**
 * Session Service (admin) — verification, retrieval, sign-out.
 *
 * Uses React's cache() to deduplicate within a single request. Validates the
 * session against the DB (not just the cookie).
 *
 * @module auth/services/SessionService
 */

import 'server-only';

import { cache } from 'react';
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import type { ISessionService, Session } from '../interfaces';
import type { auth as AuthInstance } from '../index';

export class SessionService implements ISessionService {
  constructor(private readonly auth: typeof AuthInstance) {}

  /**
   * Verify the current session. Redirects to /login when absent/invalid.
   * SECURITY: validates against the database, not just the cookie.
   */
  verifySession = cache(async (): Promise<Session> => {
    try {
      const session = await this.auth.api.getSession({ headers: await headers() });
      if (!session) {
        redirect('/login');
      }
      return session as Session;
    } catch (error) {
      // redirect() throws internally — re-throw so Next can handle it.
      if (isRedirectError(error)) throw error;
      console.error('[SessionService] Session verification failed:', error);
      redirect('/login');
    }
  });

  /** Get the session without redirecting (null if none). */
  getSession = cache(async (): Promise<Session | null> => {
    try {
      const session = await this.auth.api.getSession({ headers: await headers() });
      return (session as Session) ?? null;
    } catch {
      return null;
    }
  });

  async signOut(reqHeaders: Headers): Promise<void> {
    await this.auth.api.signOut({ headers: reqHeaders });
  }

  async refreshSession(): Promise<Session | null> {
    try {
      const session = await this.auth.api.getSession({ headers: await headers() });
      return (session as Session) ?? null;
    } catch {
      return null;
    }
  }

  async isAuthenticated(): Promise<boolean> {
    return (await this.getSession()) !== null;
  }
}

/** Detect Next.js redirect() control-flow errors so they are not swallowed. */
function isRedirectError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'digest' in error &&
    typeof (error as { digest?: unknown }).digest === 'string' &&
    (error as { digest: string }).digest.startsWith('NEXT_REDIRECT')
  );
}
