import type { ResolvedConfig } from './config';

/**
 * Cookie names. `__Host-` whenever the public origin is https (requires Secure, Path=/, no
 * Domain). Plain names only for http dev (Safari refuses Secure cookies on http://localhost).
 */
export interface CookieNames {
  session: string;
  flow: string;
  /** Non-secret marker: this browser may hold a Keycloak SSO session (buyer handoff). */
  sso: string;
  /** Per-device id keying the encrypted id_token hint. */
  device: string;
}

export function cookieNames(cfg: Pick<ResolvedConfig, 'secure' | 'cookieBase'>): CookieNames {
  const p = cfg.secure ? '__Host-' : '';
  return {
    session: `${p}${cfg.cookieBase}`,
    flow: `${p}${cfg.cookieBase}_f`,
    sso: `${p}${cfg.cookieBase}_sso`,
    device: `${p}${cfg.cookieBase}_dev`,
  };
}

export function parseCookies(header: string | null | undefined): Map<string, string> {
  const out = new Map<string, string>();
  if (!header) return out;
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i < 1) continue;
    const k = part.slice(0, i).trim();
    const v = part.slice(i + 1).trim();
    if (!out.has(k)) out.set(k, v);
  }
  return out;
}

export interface CookieAttrs {
  maxAge: number;
  sameSite: 'lax' | 'strict';
  secure: boolean;
  httpOnly?: boolean;
}

export function serializeCookie(name: string, value: string, a: CookieAttrs): string {
  if (!/^[A-Za-z0-9_\-.%]*$/.test(value)) throw new Error('cookie value must be base64url-safe');
  const parts = [`${name}=${value}`, 'Path=/', `Max-Age=${Math.max(0, Math.floor(a.maxAge))}`, `SameSite=${a.sameSite === 'strict' ? 'Strict' : 'Lax'}`];
  if (a.httpOnly !== false) parts.push('HttpOnly');
  if (a.secure) parts.push('Secure');
  return parts.join('; ');
}

export function clearCookie(name: string, secure: boolean, sameSite: 'lax' | 'strict' = 'lax'): string {
  return serializeCookie(name, '', { maxAge: 0, sameSite, secure });
}

export function readCookie(req: Request, name: string): string | null {
  return parseCookies(req.headers.get('cookie')).get(name) ?? null;
}
