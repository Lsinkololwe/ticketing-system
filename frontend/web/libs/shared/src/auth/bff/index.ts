import 'server-only';

import { createHash } from 'node:crypto';
import { resolveConfig, type BffConfig } from './config';
import type { BffDeps } from './context';
import { cookieNames } from './cookies';
import { parseEncKeys } from './crypto';
import { FlowService } from './flow';
import { buildHandlers } from './handlers';
import { createFlowApi } from './handoff';
import { createLogger } from './logger';
import { createProxy } from './proxy';
import { RateLimiter, resolveClientIp, type Subjects } from './ratelimit';
import { TokenManager } from './refresh';
import { createRequire } from './require';
import { createRevocationAdapter } from './revocation';
import { createOidcClient } from './oidc';
import { SessionService } from './session';
import { createRedisStore, type KeyValueStore } from './store';
import { createUpstream } from './upstream';
import type { CspOptions } from './csp';
import { MemoryStore } from './store/memory';

export type { BffConfig, PostLoginContext, PostLoginResult, GuardedPrefix } from './config';
export type { PublicSession, SessionRecord } from './session';
export { BffAuthError } from './require';
export { safeReturnTo } from './guards';
export { buildCsp, newNonce, securityHeaders } from './csp';
export { CSRF_HEADER, assertSameOrigin } from './csrf';
export { createLogger, redact } from './logger';
export { defaultPolicies } from './ratelimit';
export type { RateRule, PolicyName } from './ratelimit';

/**
 * One BFF per app. Redis is connected lazily on first use so `next build` needs no services.
 *
 *   export const bff = createBff({ ... });
 *   // app/api/auth/[action]/route.ts
 *   export const { GET, POST } = bff.handlers;
 *   // proxy.ts
 *   export const proxy = bff.proxy;
 */
export function createBff(config: BffConfig, opts: { csp?: CspOptions } = {}) {
  const cfg = resolveConfig(config, (c) => createLogger({ app: c.app, level: c.logLevel }));
  let depsPromise: Promise<BffDeps> | null = null;

  async function build(): Promise<BffDeps> {
    let store: KeyValueStore;
    if (cfg.redis?.store) store = cfg.redis.store;
    else if (cfg.redis?.url) store = await createRedisStore(cfg.redis.url);
    else if (cfg.production) throw new Error('BFF: REDIS_URL is required in production');
    else store = new MemoryStore(cfg.clock);
    const keys = parseEncKeys(cfg.encKeys);
    const sessions = new SessionService(cfg, store, cfg.encKeys);
    const oidc = createOidcClient(cfg);
    const limiter = new RateLimiter(store, {
      app: cfg.app,
      prefix: sessions.prefix,
      secret: createHash('sha256').update(keys[0].key).update('bff-rl').digest(),
      clock: cfg.clock,
      overrides: cfg.rateLimits,
      logger: cfg.logger,
    });
    return {
      cfg,
      names: cookieNames(cfg),
      sessions,
      flows: new FlowService(sessions),
      tokens: new TokenManager(cfg, sessions, oidc),
      oidc,
      limiter,
      revocation: createRevocationAdapter(cfg),
    };
  }
  const getDeps = () => (depsPromise ??= build().catch((e) => { depsPromise = null; throw e; }));

  // Handlers resolve deps per call, so route modules can be constructed at import time.
  const lazy = <T extends (...a: never[]) => Promise<Response>>(make: (d: BffDeps) => T) =>
    (async (...a: Parameters<T>) => make(await getDeps())(...a)) as T;
  let built: { deps: BffDeps; h: ReturnType<typeof buildHandlers> } | null = null;
  const h = (d: BffDeps) => (built && built.deps === d ? built.h : (built = { deps: d, h: buildHandlers(d) }).h);
  const handlers = {
    start: lazy((d) => h(d).start),
    callback: lazy((d) => h(d).callback),
    logout: lazy((d) => h(d).logout),
    'backchannel-logout': lazy((d) => h(d)['backchannel-logout']),
    stepup: lazy((d) => h(d).stepup),
    session: lazy((d) => h(d).session),
    GET: lazy((d) => h(d).GET),
    POST: lazy((d) => h(d).POST),
  };

  const deferred = <A extends unknown[], R>(f: (d: BffDeps) => (...a: A) => Promise<R>) => async (...a: A) => f(await getDeps())(...a);
  const requireApi = {
    requireSession: deferred((d) => createRequire(d).requireSession),
    requireRole: deferred((d) => createRequire(d).requireRole),
    getSession: deferred((d) => createRequire(d).getSession),
    getAccessToken: deferred((d) => createRequire(d).getAccessToken),
  };
  return {
    config: cfg,
    handlers,
    proxy: createProxy(getDeps, opts.csp),
    ...requireApi,
    upstream: {
      graphql: (o?: { allowAnonymous?: boolean }) => {
        const make = async () => createUpstream(await getDeps()).graphql(o);
        return { POST: async (req: Request) => (await make()).POST(req) };
      },
      rest: (o?: { allowAnonymous?: boolean }) => {
        const make = async () => createUpstream(await getDeps()).rest(o);
        const call = (m: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE') => async (req: Request, c: { params: Promise<{ path?: string[] }> | { path?: string[] } }) => (await make())[m](req, c);
        return { GET: call('GET'), POST: call('POST'), PUT: call('PUT'), PATCH: call('PATCH'), DELETE: call('DELETE') };
      },
    },
    /** Buyer handoff helpers for the identify routes. */
    flow: {
      read: async (req: Request) => createFlowApi(await getDeps()).read(req),
      update: async (req: Request, fn: Parameters<ReturnType<typeof createFlowApi>['update']>[1]) => createFlowApi(await getDeps()).update(req, fn),
      attachHandle: async (req: Request, handle: Parameters<ReturnType<typeof createFlowApi>['attachHandle']>[1]) => createFlowApi(await getDeps()).attachHandle(req, handle),
    },
    /** For app routes that need the shared limiter (identify routes): `await bff.rateLimit('challenge', { ip, contact, device })`. */
    rateLimit: async (policy: string, subjects: Subjects) => (await getDeps()).limiter.consume(policy, subjects),
    /** Trusted-proxy client IP for the current request. */
    clientIp: (req: Request) => resolveClientIp(req.headers, cfg.trustProxyHops),
    /** Escape hatch for tests and advanced callers. */
    internals: getDeps,
  };
}

export type Bff = ReturnType<typeof createBff>;
