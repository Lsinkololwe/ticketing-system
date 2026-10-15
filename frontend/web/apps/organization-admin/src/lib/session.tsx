'use client';

import { useEffect, useState } from 'react';

/** Public session as served by `GET /api/auth/session` (no tokens). */
export interface ClientSession {
  user: { id: string; name: string | null; roles: string[] };
}

interface SessionState {
  data: ClientSession | null;
  isPending: boolean;
}

interface SessionPayload {
  authenticated?: boolean;
  accountId?: string | null;
  displayName?: string | null;
  roles?: string[];
}

/** Reads the BFF session (same origin, cookie only). `data` is null when signed out or unreachable. */
export function useSession(): SessionState {
  const [state, setState] = useState<SessionState>({ data: null, isPending: true });
  useEffect(() => {
    const ctl = new AbortController();
    fetch('/api/auth/session', { credentials: 'same-origin', cache: 'no-store', signal: ctl.signal })
      .then((r) => (r.ok ? (r.json() as Promise<SessionPayload>) : null))
      .then((j) => {
        if (j?.authenticated && j.accountId) {
          setState({ data: { user: { id: j.accountId, name: j.displayName ?? null, roles: j.roles ?? [] } }, isPending: false });
        } else setState({ data: null, isPending: false });
      })
      .catch((e: unknown) => {
        if ((e as { name?: string }).name !== 'AbortError') setState({ data: null, isPending: false });
      });
    return () => ctl.abort();
  }, []);
  return state;
}

/**
 * Asks the BFF to re-read the person's roles now, so an approval that has just happened is in the
 * session without signing out. Resolves true when the session was refreshed.
 */
export async function refreshSession(): Promise<boolean> {
  try {
    const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin', cache: 'no-store' });
    return response.ok;
  } catch {
    return false;
  }
}

/** Sign-in entry: the BFF starts the Keycloak authorization-code + PKCE flow. */
export function signInUrl(next = '/dashboard'): string {
  return `/api/auth/start?next=${encodeURIComponent(next)}`;
}

/** Ends the session: a same-origin POST form (the browser sends Origin), then Keycloak RP-initiated logout. */
export function signOut(): void {
  const form = document.createElement('form');
  form.method = 'post';
  form.action = '/api/auth/logout';
  document.body.appendChild(form);
  form.submit();
}
