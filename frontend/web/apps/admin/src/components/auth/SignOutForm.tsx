'use client';

/**
 * POST /api/auth/logout. A plain form post (no script needed): the BFF ends the local session, revokes
 * the tokens and returns through Keycloak's end-session endpoint.
 */
import type { ReactNode } from 'react';

export const LOGOUT_ACTION = '/api/auth/logout';

/** Programmatic sign-out for menu items: submits a transient same-origin form. */
export function signOut(): void {
  const form = document.createElement('form');
  form.method = 'post';
  form.action = LOGOUT_ACTION;
  form.hidden = true;
  document.body.appendChild(form);
  form.submit();
}

export function SignOutForm({ children }: { children: ReactNode }) {
  return (
    <form method="post" action={LOGOUT_ACTION}>
      {children}
    </form>
  );
}
