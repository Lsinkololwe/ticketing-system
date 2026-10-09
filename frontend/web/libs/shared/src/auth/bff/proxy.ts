import { NextResponse, type NextRequest } from 'next/server';
import type { BffDeps } from './context';
import { buildCsp, newNonce, securityHeaders, type CspOptions } from './csp';
import { safeReturnTo } from './guards';
import { isUnsafeMethod } from './csrf';

function matches(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(prefix.endsWith('/') ? prefix : prefix + '/');
}

/**
 * Next 16 `proxy.ts` helper: per-request CSP nonce, security headers, Origin check for unsafe
 * page requests (Server Actions), and COARSE gating of `guarded` prefixes (session exists + role).
 * Authoritative checks stay in `requireSession` at the top of pages, actions and route handlers.
 *
 * `export const proxy = bff.proxy; export const config = { matcher: [...] }`
 */
export function createProxy(getDeps: () => Promise<BffDeps>, csp: CspOptions = {}) {
  return async function proxy(request: NextRequest): Promise<NextResponse> {
    const deps = await getDeps();
    const isDev = !deps.cfg.production;
    const nonce = newNonce();
    const policy = buildCsp(nonce, { isDev, ...csp });
    const { pathname, search } = request.nextUrl;
    const decorate = (res: NextResponse, noStore: boolean) => {
      res.headers.set('content-security-policy', policy);
      for (const [k, v] of Object.entries(securityHeaders({ isDev, noStore }))) res.headers.set(k, v);
      return res;
    };

    if (isUnsafeMethod(request.method)) {
      const origin = request.headers.get('origin');
      if (origin !== deps.cfg.appOrigin) return decorate(new NextResponse(null, { status: 403 }), true);
    }

    const publicHit = deps.cfg.publicPaths?.some((p) => matches(pathname, p));
    const rule = publicHit ? undefined : deps.cfg.guarded?.find((g) => matches(pathname, g.prefix));
    if (rule) {
      const cookie = request.cookies.get(deps.names.session)?.value;
      let s = null;
      try {
        s = await deps.sessions.loadLight(cookie);
      } catch (err) {
        deps.cfg.logger.error('proxy.session_store_error', { err });
        return decorate(new NextResponse('Service unavailable', { status: 503, headers: { 'retry-after': '5' } }), true);
      }
      if (!s) {
        const url = new URL(deps.cfg.loginPath, deps.cfg.appUrl);
        url.searchParams.set('next', safeReturnTo(pathname + search));
        return decorate(NextResponse.redirect(url, 303), true);
      }
      if (rule.roles?.length && !rule.roles.some((r) => s.record.roles.includes(r))) {
        return decorate(NextResponse.redirect(new URL(deps.cfg.unauthorizedPath, deps.cfg.appUrl), 303), true);
      }
    }

    const headers = new Headers(request.headers);
    headers.set('x-nonce', nonce);
    headers.set('x-pml-path', safeReturnTo(pathname + search));
    headers.set('content-security-policy', policy);
    return decorate(NextResponse.next({ request: { headers } }), Boolean(rule));
  };
}
