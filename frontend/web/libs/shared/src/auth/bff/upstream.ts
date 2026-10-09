import type { BffDeps } from './context';
import { clearCookie } from './cookies';
import { assertSameOrigin, isUnsafeMethod } from './csrf';
import { gate, problem, reqCtx } from './http';
import { sha256Hex } from './crypto';
import { endSso } from './sso';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
const SEGMENT = /^[A-Za-z0-9._~!$&'()*+,;=:@%-]+$/;

export interface UpstreamOptions {
  /** GraphQL serves anonymous reads (public catalogue); REST defaults to the same. */
  allowAnonymous?: boolean;
}

async function readBody(req: Request, max: number): Promise<{ body: ArrayBuffer | null; tooLarge: boolean }> {
  const len = Number(req.headers.get('content-length') ?? 0);
  if (len > max) return { body: null, tooLarge: true };
  if (!req.body) return { body: null, tooLarge: false };
  const reader = req.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > max) {
      await reader.cancel();
      return { body: null, tooLarge: true };
    }
    chunks.push(value);
  }
  const out = new Uint8Array(total);
  let o = 0;
  for (const c of chunks) {
    out.set(c, o);
    o += c.byteLength;
  }
  return { body: out.buffer, tooLarge: false };
}

/** Safe join of catch-all segments onto the upstream base. Null when any segment looks like traversal. */
export function joinPath(base: string, segments: string[]): string | null {
  for (const seg of segments) {
    if (!SEGMENT.test(seg) || seg === '.' || seg === '..') return null;
    let dec: string;
    try {
      dec = decodeURIComponent(seg);
    } catch {
      return null;
    }
    if (dec === '..' || dec === '.' || /[/\\\u0000-\u001f]/.test(dec)) return null;
  }
  return `${base.replace(/\/+$/, '')}/${segments.join('/')}`;
}

export function createUpstream(deps: BffDeps) {
  const maxBody = deps.cfg.upstream?.maxBodyBytes ?? 1_048_576;
  const timeoutMs = deps.cfg.upstream?.timeoutMs ?? 15_000;

  async function forward(req: Request, target: string, opts: UpstreamOptions): Promise<Response> {
    const ctx = reqCtx(req, deps);
    const log = deps.cfg.logger;
    if (isUnsafeMethod(req.method) && assertSameOrigin(req, deps.cfg.appOrigin)) return problem(403, 'ORIGIN_REJECTED');

    const cookie = ctx.cookies.get(deps.names.session) ?? null;
    const limited = cookie
      ? await gate(deps, 'api', { session: sha256Hex(cookie), ip: ctx.ip })
      : await gate(deps, 'api_anon', { ip: ctx.ip });
    if (limited) return limited;

    const headers = new Headers({ 'x-correlation-id': ctx.correlationId });
    // The browser's address, resolved with TRUST_PROXY_HOPS, so the router and subgraphs can rate-limit
    // each anonymous visitor separately. The caller's own X-Forwarded-For is never copied through: a
    // header is sent only when an address was resolved, and it is that address alone.
    if (ctx.ip && ctx.ip !== 'unknown') headers.set('x-forwarded-for', ctx.ip);
    for (const h of ['content-type', 'accept', 'accept-language']) {
      const v = req.headers.get(h);
      if (v) headers.set(h, v);
    }
    const endSession = () => problem(401, 'SESSION_ENDED', {}, { 'set-cookie': clearCookie(deps.names.session, deps.cfg.secure, deps.cfg.sameSite) });

    let hash: string | null = null;
    let signedIn: { idToken: string | null; refreshToken: string | null } | null = null;
    if (cookie) {
      const t = await deps.tokens.getAccessToken(cookie);
      if (t.ok) {
        headers.set('authorization', `Bearer ${t.accessToken}`);
        hash = t.session.hash;
        signedIn = { idToken: t.session.record.idToken, refreshToken: t.session.record.refreshToken };
      } else if (t.reason === 'SESSION_ENDED') return endSession();
      else if (t.reason === 'REFRESH_BUSY' || t.reason === 'UPSTREAM_UNAVAILABLE') {
        return problem(503, 'AUTH_REFRESH_BUSY', { retryAfterSeconds: 1 }, { 'retry-after': '1' });
      }
      // NO_SESSION (stale cookie): behave as anonymous below.
    }
    if (!hash && opts.allowAnonymous === false) return problem(401, 'UNAUTHENTICATED');

    const hasBody = isUnsafeMethod(req.method);
    const { body, tooLarge } = hasBody ? await readBody(req, maxBody) : { body: null, tooLarge: false };
    if (tooLarge) return problem(413, 'PAYLOAD_TOO_LARGE');

    let res: Response;
    try {
      res = await deps.cfg.fetchImpl(target, {
        method: req.method,
        headers,
        body: body ?? undefined,
        cache: 'no-store',
        redirect: 'manual',
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (err) {
      log.warn('upstream.unreachable', { cid: ctx.correlationId, err });
      return problem(502, 'UPSTREAM_UNAVAILABLE');
    }
    if (res.status === 401 && res.headers.get('x-token-revoked') === 'true' && hash) {
      await deps.sessions.destroyHash(hash).catch(() => undefined);
      // The session id is revoked, so the Keycloak session behind it must end too: otherwise the next sign-in is
      // issued the same revoked id and the person is refused again and again.
      if (signedIn) await endSso(deps, signedIn, ctx.correlationId);
      log.info('upstream.token_revoked', { cid: ctx.correlationId, session: hash.slice(0, 8) });
      return endSession();
    }
    const out = new Headers({ 'cache-control': 'no-store', 'x-correlation-id': ctx.correlationId });
    for (const h of ['content-type', 'content-language', 'retry-after']) {
      const v = res.headers.get(h);
      if (v) out.set(h, v);
    }
    return new Response(res.body, { status: res.status, headers: out });
  }

  return {
    /** `app/api/graphql/route.ts`: `export const POST = bff.upstream.graphql.POST`. */
    graphql(opts: UpstreamOptions = {}) {
      const endpoint = deps.cfg.upstream?.graphql;
      const handler = async (req: Request) => {
        if (!endpoint) return problem(500, 'UPSTREAM_NOT_CONFIGURED');
        return forward(req, endpoint, opts);
      };
      return { POST: handler };
    },
    /** `app/api/rest/[...path]/route.ts`. */
    rest(opts: UpstreamOptions = {}) {
      const handler = async (req: Request, routeCtx: { params: Promise<{ path?: string[] }> | { path?: string[] } }) => {
        const base = deps.cfg.upstream?.rest;
        if (!base) return problem(500, 'UPSTREAM_NOT_CONFIGURED');
        const { path } = await routeCtx.params;
        const joined = joinPath(base, path ?? []);
        if (!joined) return problem(400, 'BAD_PATH');
        return forward(req, joined + new URL(req.url).search, opts);
      };
      const m = handler as (req: Request, c: { params: Promise<{ path?: string[] }> | { path?: string[] } }) => Promise<Response>;
      const out: Record<Method, typeof m> = { GET: m, POST: m, PUT: m, PATCH: m, DELETE: m };
      return out;
    },
  };
}
