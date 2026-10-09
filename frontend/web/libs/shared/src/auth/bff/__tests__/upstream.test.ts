import { describe, expect, it } from 'vitest';
import { joinPath } from '../upstream';
import { APP, login, makeHarness, type Harness } from './harness';
import { composeFetch, type FetchFn } from '../testing';

interface Seen { url: string; method: string; headers: Headers; body: string }
async function withGateway(over: Parameters<typeof makeHarness>[0] = {}, respond: (s: Seen) => Response = () => new Response('{"data":{}}', { status: 200, headers: { 'content-type': 'application/json', 'set-cookie': 'leak=1' } })) {
  const seen: Seen[] = [];
  const gw: FetchFn = async (input, init) => {
    const url = String(input);
    if (!url.startsWith('http://gateway.test')) return new Response('not_keycloak', { status: 404 });
    const s = { url, method: String(init?.method), headers: new Headers(init?.headers), body: init?.body ? new TextDecoder().decode(init.body as ArrayBuffer) : '' };
    seen.push(s);
    return respond(s);
  };
  const h = await makeHarness({ ...over, fetch: undefined });
  // rebuild with the gateway stub composed in
  const kcFetch = h.kc.fetch;
  const h2 = await makeHarness({ ...over, kc: h.kc, clock: h.clock, fetch: composeFetch(kcFetch, h.identity.fetch, gw) });
  return { h: h2, seen };
}
const post = (h: Harness, jar: Record<string, string>, body = '{"query":"{a}"}', headers: Record<string, string> = {}) =>
  h.bff.upstream.graphql().POST(h.req('/api/graphql', { method: 'POST', body, cookies: jar, headers: { 'content-type': 'application/json', origin: APP, 'sec-fetch-site': 'same-origin', 'x-pml-csrf': '1', ...headers } }));

describe('graphql upstream', () => {
  it('adds the bearer server-side, strips cookies both ways and never leaks the token to the browser', async () => {
    const { h, seen } = await withGateway();
    const { jar } = await login(h);
    const res = await post(h, jar);
    expect(res.status).toBe(200);
    expect(seen).toHaveLength(1);
    expect(seen[0].headers.get('authorization')).toMatch(/^Bearer eyJ/);
    expect(seen[0].headers.get('cookie')).toBeNull();
    expect(res.headers.get('set-cookie')).toBeNull();
    expect(await res.text()).not.toMatch(/eyJ/);
  });
  it('requires the CSRF triple on POST', async () => {
    const { h, seen } = await withGateway();
    const { jar } = await login(h);
    expect((await post(h, jar, undefined, { origin: 'https://evil.example' })).status).toBe(403);
    expect((await post(h, jar, undefined, { 'x-pml-csrf': '' })).status).toBe(403);
    expect((await post(h, jar, undefined, { 'sec-fetch-site': 'cross-site' })).status).toBe(403);
    expect(seen).toHaveLength(0);
  });
  it('anonymous requests go through without Authorization', async () => {
    const { h, seen } = await withGateway();
    expect((await post(h, {})).status).toBe(200);
    expect(seen[0].headers.get('authorization')).toBeNull();
  });
  it('forwards the resolved client address as X-Forwarded-For and never the caller-supplied chain', async () => {
    const { h, seen } = await withGateway({ trustProxyHops: 1 });
    await post(h, {}, undefined, { 'x-forwarded-for': '6.6.6.6, 198.51.100.7' });
    expect(seen[0].headers.get('x-forwarded-for')).toBe('198.51.100.7');
  });
  it('sends no X-Forwarded-For when no client address was resolved (hops 0: the header is not trusted)', async () => {
    const { h, seen } = await withGateway({ trustProxyHops: 0 });
    await post(h, {}, undefined, { 'x-forwarded-for': '6.6.6.6' });
    expect(seen[0].headers.get('x-forwarded-for')).toBeNull();
  });
  it('refreshes transparently when the access token is stale', async () => {
    const { h, seen } = await withGateway();
    const { jar } = await login(h);
    h.clock.advanceSec(290);
    expect((await post(h, jar)).status).toBe(200);
    expect(h.kc.stats.refreshCalls).toBe(1);
    expect(seen[0].headers.get('authorization')).toBeTruthy();
  });
  it('parallel requests with a stale token refresh exactly once', async () => {
    const { h } = await withGateway();
    const { jar } = await login(h);
    h.clock.advanceSec(290);
    const rs = await Promise.all(Array.from({ length: 20 }, () => post(h, jar)));
    expect(rs.every((r) => r.status === 200)).toBe(true);
    expect(h.kc.stats.refreshCalls).toBe(1);
    expect(h.kc.stats.reuseDetected).toBe(0);
  });
  it('invalid_grant -> 401 SESSION_ENDED with the cookie cleared; Keycloak 5xx -> 503, session kept', async () => {
    const { h } = await withGateway();
    const { jar } = await login(h);
    h.clock.advanceSec(290);
    h.kc.faults.refresh = ['5xx'];
    const busy = await post(h, jar);
    expect(busy.status).toBe(503);
    expect(Number(busy.headers.get('retry-after'))).toBeGreaterThan(0);
    h.kc.faults.refresh = ['invalid_grant'];
    const ended = await post(h, jar);
    expect(ended.status).toBe(401);
    expect(await ended.json()).toMatchObject({ errorCode: 'SESSION_ENDED' });
    expect(ended.headers.get('set-cookie')).toMatch(/__Host-pml_org=;.*Max-Age=0/);
  });
  it('gateway 401 + X-Token-Revoked destroys the session', async () => {
    const { h } = await withGateway({}, () => new Response('{}', { status: 401, headers: { 'x-token-revoked': 'true' } }));
    const { jar } = await login(h);
    const res = await post(h, jar);
    expect(res.status).toBe(401);
    expect(await res.json()).toMatchObject({ errorCode: 'SESSION_ENDED' });
    expect(await (await h.deps()).sessions.load(jar['__Host-pml_org'])).toBeNull();
  });
  it('a revoked session also ends the Keycloak SSO session, so the next sign-in is not issued the same revoked id', async () => {
    const { h } = await withGateway({}, () => new Response('{}', { status: 401, headers: { 'x-token-revoked': 'true' } }));
    const { jar } = await login(h);
    const deps = await h.deps();
    const idToken = (await deps.sessions.load(jar['__Host-pml_org']))!.record.idToken;
    const res = await post(h, jar);
    expect(res.status).toBe(401);
    expect(await deps.sessions.load(jar['__Host-pml_org'])).toBeNull();
    expect(h.kc.endedSessions).toEqual([idToken]);
  });
  it('a plain upstream 401/500 passes through without ending the session', async () => {
    const { h } = await withGateway({}, () => new Response('{"e":1}', { status: 500 }));
    const { jar } = await login(h);
    expect((await post(h, jar)).status).toBe(500);
    expect(await (await h.deps()).sessions.load(jar['__Host-pml_org'])).not.toBeNull();
  });
  it('enforces the body limit', async () => {
    const { h, seen } = await withGateway({ upstream: { graphql: 'http://gateway.test/graphql', maxBodyBytes: 100 } });
    const res = await post(h, {}, 'x'.repeat(500));
    expect(res.status).toBe(413);
    expect(seen).toHaveLength(0);
  });
  it('rate limits the session (429)', async () => {
    const { h } = await withGateway({ rateLimits: { api: [{ subject: 'session', limit: 2, windowSec: 60 }], api_anon: [{ subject: 'ip', limit: 2, windowSec: 60 }] } });
    const { jar } = await login(h);
    expect((await post(h, jar)).status).toBe(200);
    expect((await post(h, jar)).status).toBe(200);
    expect((await post(h, jar)).status).toBe(429);
  });
  it('502 when the gateway is unreachable', async () => {
    const { h } = await withGateway({}, () => { throw new Error('ECONNREFUSED'); });
    expect((await post(h, {})).status).toBe(502);
  });
});

describe('rest upstream', () => {
  it('forwards the path and query with the bearer', async () => {
    const { h, seen } = await withGateway();
    const { jar } = await login(h);
    const r = h.bff.upstream.rest();
    const res = await r.GET(h.req('/api/rest/events/42?x=1', { cookies: jar }), { params: Promise.resolve({ path: ['events', '42'] }) });
    expect(res.status).toBe(200);
    expect(seen[0].url).toBe('http://gateway.test/api/events/42?x=1');
    expect(seen[0].headers.get('authorization')).toBeTruthy();
  });
  it.each([[['..', 'admin']], [['a', '%2e%2e', 'b']], [['a%2Fb']], [['a\\b']], [['.']], [['a b']]])('rejects traversal %j', async (path) => {
    const { h, seen } = await withGateway();
    const res = await h.bff.upstream.rest().GET(h.req('/api/rest/x'), { params: { path } });
    expect(res.status).toBe(400);
    expect(seen).toHaveLength(0);
  });
  it('joinPath', () => {
    expect(joinPath('http://g/api/', ['a', 'b'])).toBe('http://g/api/a/b');
    expect(joinPath('http://g', [])).toBe('http://g/');
  });
});
