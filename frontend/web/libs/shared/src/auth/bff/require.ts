import type { BffDeps } from './context';
import { safeReturnTo } from './guards';
import { toPublic, type PublicSession } from './session';

export type BffAuthErrorCode = 'UNAUTHENTICATED' | 'FORBIDDEN' | 'STEP_UP_REQUIRED';

/** Thrown in `kind: 'action'` mode so a Server Action can return a typed error instead of redirecting. */
export class BffAuthError extends Error {
  constructor(public readonly code: BffAuthErrorCode, public readonly stepUpUrl?: string) {
    super(code);
    this.name = 'BffAuthError';
  }
}

export interface RequireOptions {
  /** Any-of. */
  roles?: string[];
  /** Seconds since the last interactive authentication (`auth_time`). */
  freshAuthSec?: number;
  /** `page` redirects (default); `action` throws BffAuthError. */
  kind?: 'page' | 'action';
  returnTo?: string;
}

export function createRequire(deps: BffDeps) {
  async function sessionCookie(): Promise<string | undefined> {
    const { cookies } = await import('next/headers');
    return (await cookies()).get(deps.names.session)?.value;
  }

  async function current() {
    return deps.sessions.load(await sessionCookie());
  }

  async function requireSession(opts: RequireOptions = {}): Promise<PublicSession> {
    const kind = opts.kind ?? 'page';
    const s = await current();
    let returnTo = opts.returnTo;
    if (!returnTo && kind === 'page') {
      const { headers } = await import('next/headers');
      returnTo = (await headers()).get('x-pml-path') ?? '/';
    }
    const redirect = (await import('next/navigation')).redirect as (url: string) => never;
    if (!s) {
      if (kind === 'action') throw new BffAuthError('UNAUTHENTICATED');
      return redirect(`${deps.cfg.loginPath}?next=${encodeURIComponent(safeReturnTo(returnTo))}`);
    }
    if (opts.roles?.length && !opts.roles.some((r) => s.record.roles.includes(r))) {
      if (kind === 'action') throw new BffAuthError('FORBIDDEN');
      return redirect(deps.cfg.unauthorizedPath);
    }
    if (opts.freshAuthSec !== undefined) {
      const age = s.record.authTime === null ? Infinity : deps.cfg.clock() / 1000 - s.record.authTime;
      if (age > opts.freshAuthSec) {
        const url = `/api/auth/stepup?next=${encodeURIComponent(safeReturnTo(returnTo))}&maxAge=${opts.freshAuthSec}`;
        if (kind === 'action') throw new BffAuthError('STEP_UP_REQUIRED', url);
        return redirect(url);
      }
    }
    return toPublic(s);
  }

  /** Non-redirecting read for components that adapt to anonymous visitors. */
  async function getSession(): Promise<PublicSession | null> {
    const s = await current();
    return s ? toPublic(s) : null;
  }

  /** Bearer for server-to-server calls made while serving THIS request. Never return it to client code. */
  async function getAccessToken(): Promise<string | null> {
    const r = await deps.tokens.getAccessToken(await sessionCookie());
    return r.ok ? r.accessToken : null;
  }

  return { requireSession, requireRole: (roles: string[], o: Omit<RequireOptions, 'roles'> = {}) => requireSession({ ...o, roles }), getSession, getAccessToken };
}
