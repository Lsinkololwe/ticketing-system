import { createBff, type Bff } from '../index';
import type { BffConfig } from '../config';
import { createLogger } from '../logger';
import { MemoryStore, type KeyValueStore } from '../store';
import { composeFetch, createFakeKeycloak, FakeClock, TEST_ENC_KEYS, type FakeKeycloak, type FetchFn } from '../testing';

export const ISSUER = 'http://kc.test/realms/pml';
export const APP = 'https://org.example.com';

export interface IdentityStub {
  calls: Array<{ path: string; body: Record<string, unknown> }>;
  status: number;
  accountStatus: Record<string, string>;
  fetch: FetchFn;
}

export function identityStub(): IdentityStub {
  const stub: IdentityStub = {
    calls: [],
    status: 200,
    accountStatus: {},
    fetch: async (input, init) => {
      const url = new URL(typeof input === 'string' ? input : input.toString());
      if (url.host !== 'identity.test') return new Response('not_keycloak', { status: 404 });
      if (url.pathname === '/token') {
        return new Response(JSON.stringify({ access_token: 'svc-token', expires_in: 300 }), { status: 200 });
      }
      const m = /^\/api\/internal\/auth\/accounts\/(.+)\/status$/.exec(url.pathname);
      if (m) {
        const status = stub.accountStatus[decodeURIComponent(m[1])] ?? 'ACTIVE';
        return new Response(JSON.stringify({ accountId: decodeURIComponent(m[1]), status }), { status: 200 });
      }
      stub.calls.push({ path: url.pathname, body: JSON.parse(String(init?.body ?? '{}')) });
      return new Response(JSON.stringify({ revoked: stub.status === 200, records: [{ id: '1' }] }), { status: stub.status });
    },
  };
  return stub;
}

export interface Harness {
  bff: Bff;
  kc: FakeKeycloak;
  clock: FakeClock;
  identity: IdentityStub;
  store: KeyValueStore;
  logs: string[];
  req(path: string, init?: RequestInit & { cookies?: Record<string, string> }): Request;
  deps(): ReturnType<Bff['internals']>;
}

export async function makeHarness(over: Partial<BffConfig> & { store?: KeyValueStore; kc?: FakeKeycloak; clock?: FakeClock } = {}): Promise<Harness> {
  const clock = over.clock ?? new FakeClock();
  const kc = over.kc ?? (await createFakeKeycloak({ issuer: ISSUER, clientId: 'web', clientSecret: 's3cret', clock }));
  const identity = identityStub();
  const logs: string[] = [];
  const store = over.store ?? new MemoryStore(clock.now);
  const { store: _s, kc: _k, clock: _c, ...rest } = over;
  void _s; void _k; void _c;
  const bff = createBff({
    app: 'organizer',
    appUrl: APP,
    oidc: { issuer: ISSUER, clientId: 'web', clientSecret: 's3cret' },
    encKeys: TEST_ENC_KEYS,
    redis: { store },
    clock: clock.now,
    fetch: composeFetch(kc.fetch, identity.fetch),
    logger: createLogger({ app: 'organizer', level: 'debug', sink: (l) => logs.push(l) }),
    identity: { baseUrl: 'http://identity.test', tokenUrl: 'http://identity.test/token', clientId: 'svc', clientSecret: 'x' },
    access: { roles: ['ORGANIZER', 'ADMIN'], audience: 'myticketzm-api' },
    upstream: { graphql: 'http://gateway.test/graphql', rest: 'http://gateway.test/api' },
    trustProxyHops: 1,
    production: false,
    ...rest,
  });
  return {
    bff, kc, clock, identity, store, logs,
    deps: () => bff.internals(),
    req(path, init = {}) {
      const { cookies, ...i } = init;
      const headers = new Headers(i.headers);
      if (cookies) headers.set('cookie', Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join('; '));
      if (!headers.has('x-forwarded-for')) headers.set('x-forwarded-for', '203.0.113.9');
      return new Request(path.startsWith('http') ? path : APP + path, { ...i, headers });
    },
  };
}

/** Collects Set-Cookie headers into a name -> value jar (empty value = cleared). */
export function jarFrom(res: Response, jar: Record<string, string> = {}): Record<string, string> {
  for (const c of res.headers.getSetCookie()) {
    const [pair] = c.split(';');
    const i = pair.indexOf('=');
    const name = pair.slice(0, i);
    const value = pair.slice(i + 1);
    if (/Max-Age=0/i.test(c) || value === '') delete jar[name];
    else jar[name] = value;
  }
  return jar;
}

export const staffUser = { sub: 'kc-user-1', roles: ['ORGANIZER'], name: 'Org Admin' };

/** Drives start -> (fake Keycloak login) -> callback. Returns the session cookie jar. */
export async function login(h: Harness, user = staffUser, opts: { next?: string; sid?: string; jar?: Record<string, string> } = {}) {
  const jar = opts.jar ?? {};
  const start = await h.bff.handlers.start(h.req(`/api/auth/start${opts.next ? `?next=${encodeURIComponent(opts.next)}` : ''}`, { cookies: jar }));
  jarFrom(start, jar);
  const authUrl = start.headers.get('location')!;
  const { code, state } = h.kc.issueCode(authUrl, user, { sid: opts.sid });
  const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
  jarFrom(cb, jar);
  return { jar, start, cb, authUrl };
}
