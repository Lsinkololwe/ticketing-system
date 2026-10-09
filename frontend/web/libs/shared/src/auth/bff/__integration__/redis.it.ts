import IORedis from 'ioredis';
import { afterAll, afterEach, beforeAll, describe, expect, inject, it } from 'vitest';
import { createBff } from '../index';
import { createLogger } from '../logger';
import { RateLimiter } from '../ratelimit';
import { RedisStore } from '../store';
import { composeFetch, createFakeKeycloak, FakeClock, TEST_ENC_KEYS, type FakeKeycloak } from '../testing';
import { identityStub, ISSUER, APP, jarFrom, staffUser } from '../__tests__/harness';
import type { BffConfig } from '../config';

const url = inject('redisUrl');
let admin: IORedis;
const clients: IORedis[] = [];
const newStore = () => {
  const c = new IORedis(url);
  clients.push(c);
  return new RedisStore(c);
};

beforeAll(() => {
  admin = new IORedis(url);
});
afterEach(async () => {
  await admin.flushall();
});
afterAll(async () => {
  await Promise.all(clients.map((c) => c.quit().catch(() => undefined)));
  await admin.quit();
});

/** A "process": its own ioredis connection, its own BFF instance (own in-process maps), shared Redis + Keycloak. */
function process_(kc: FakeKeycloak, clock: FakeClock, store: RedisStore, over: Partial<BffConfig> = {}, logs: string[] = []) {
  const identity = identityStub();
  const bff = createBff({
    app: 'organizer', appUrl: APP, production: false,
    oidc: { issuer: ISSUER, clientId: 'web', clientSecret: 's3cret' },
    encKeys: TEST_ENC_KEYS, redis: { store }, clock: clock.now,
    fetch: composeFetch(kc.fetch, identity.fetch),
    logger: createLogger({ app: 'organizer', level: 'debug', sink: (l) => logs.push(l) }),
    identity: { baseUrl: 'http://identity.test', tokenUrl: 'http://identity.test/token', clientId: 'svc', clientSecret: 'x' },
    access: { roles: ['ORGANIZER'], audience: 'myticketzm-api' },
    refresh: { pollMs: 10, waitMs: 4000, lockMs: 5000 },
    trustProxyHops: 1,
    ...over,
  });
  const req = (path: string, init: RequestInit & { cookies?: Record<string, string> } = {}) => {
    const { cookies, ...i } = init;
    const headers = new Headers(i.headers);
    if (cookies) headers.set('cookie', Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join('; '));
    if (!headers.has('x-forwarded-for')) headers.set('x-forwarded-for', '203.0.113.9');
    return new Request(APP + path, { ...i, headers });
  };
  return { bff, identity, req };
}
type Proc = ReturnType<typeof process_>;

async function loginOn(p: Proc, kc: FakeKeycloak, user = staffUser, sid?: string) {
  const jar: Record<string, string> = {};
  const start = await p.bff.handlers.start(p.req('/api/auth/start'));
  jarFrom(start, jar);
  const { code, state } = kc.issueCode(start.headers.get('location')!, user, { sid });
  const cb = await p.bff.handlers.callback(p.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
  jarFrom(cb, jar);
  return jar;
}

describe('Redis store primitives (real Lua)', () => {
  it('setNx / delIfEquals implement a safe lock', async () => {
    const s = newStore();
    expect(await s.setNx('lock', 'a', 5000)).toBe(true);
    expect(await s.setNx('lock', 'b', 5000)).toBe(false);
    expect(await s.delIfEquals('lock', 'b')).toBe(false);
    expect(await s.delIfEquals('lock', 'a')).toBe(true);
    expect(await s.setNx('lock', 'b', 50)).toBe(true);
    await new Promise((r) => setTimeout(r, 80));
    expect(await s.setNx('lock', 'c', 5000)).toBe(true); // PX expiry frees it
  });
  it('casSession: exactly one of many concurrent writers wins per version, TTL is re-applied', async () => {
    const s = newStore();
    await s.putSession('sess', { data: 'v0', ver: 1, created: 1, seen: 1 }, 100);
    const results = await Promise.all(Array.from({ length: 20 }, (_v, i) => newStore().casSession('sess', 1, `w${i}`, 200)));
    expect(results.filter(Boolean)).toHaveLength(1);
    const row = (await s.getSession('sess'))!;
    expect(row.ver).toBe(2);
    expect(row.created).toBe(1);
    expect(await admin.ttl('sess')).toBeGreaterThan(100);
    expect(await s.casSession('missing', 1, 'x', 10)).toBe(false);
  });
  it('touchSession keeps data/ver and refuses missing sessions', async () => {
    const s = newStore();
    await s.putSession('s', { data: 'd', ver: 4, created: 1, seen: 1 }, 100);
    expect(await s.touchSession('s', 99, 50)).toBe(true);
    expect(await s.getSession('s')).toEqual({ data: 'd', ver: 4, created: 1, seen: 99 });
    expect(await admin.ttl('s')).toBeLessThanOrEqual(50);
    expect(await s.touchSession('nope', 1, 5)).toBe(false);
  });
  it('index sets expire and getDel is atomic', async () => {
    const s = newStore();
    await s.sadd('set', 'a', 30);
    await s.sadd('set', 'b', 10); // must not shorten the TTL
    expect((await s.smembers('set')).sort()).toEqual(['a', 'b']);
    expect(await admin.ttl('set')).toBeGreaterThan(20);
    await s.set('once', 'x', 10);
    const got = await Promise.all([s.getDel('once'), newStore().getDel('once')]);
    expect(got.filter((v) => v === 'x')).toHaveLength(1);
  });
});

describe('sliding-window rate limiter on Redis', () => {
  const mk = (clock: FakeClock, overrides: ConstructorParameters<typeof RateLimiter>[1]['overrides']) =>
    new RateLimiter(newStore(), { app: 'organizer', prefix: 'it:', secret: Buffer.alloc(32, 3), clock: clock.now, overrides, logger: createLogger({ app: 'x', level: 'silent' }) });

  it('enforces the limit across two processes and slides (no boundary burst)', async () => {
    const clock = new FakeClock();
    const a = mk(clock, { start: [{ subject: 'ip', limit: 5, windowSec: 60 }] });
    const b = mk(clock, { start: [{ subject: 'ip', limit: 5, windowSec: 60 }] });
    const out = await Promise.all(Array.from({ length: 12 }, (_v, i) => (i % 2 ? a : b).consume('start', { ip: '198.51.100.1' })));
    expect(out.filter((d) => d.allowed)).toHaveLength(5);
    expect(out.find((d) => !d.allowed)!.retryAfterSec).toBeGreaterThan(0);
    clock.advanceSec(30);
    expect((await a.consume('start', { ip: '198.51.100.1' })).allowed).toBe(false); // still inside the window
    clock.advanceSec(31);
    expect((await a.consume('start', { ip: '198.51.100.1' })).allowed).toBe(true);
  });
  it('boundary: 5 hits at t=55s and 5 more at t=65s are NOT all allowed', async () => {
    const clock = new FakeClock();
    const rl = mk(clock, { start: [{ subject: 'ip', limit: 5, windowSec: 60 }] });
    clock.advanceSec(55);
    let ok = 0;
    for (let i = 0; i < 5; i++) ok += (await rl.consume('start', { ip: 'x' })).allowed ? 1 : 0;
    clock.advanceSec(10);
    for (let i = 0; i < 5; i++) ok += (await rl.consume('start', { ip: 'x' })).allowed ? 1 : 0;
    expect(ok).toBe(5);
  });
  it('penalty blocks persist in Redis; keys hold no raw identifiers; real PEXPIRE cleans up', async () => {
    const real = new FakeClock(Date.now());
    const rl = mk(real, { start: [{ subject: 'ip', limit: 1, windowSec: 1, penaltySec: 1 }] });
    await rl.consume('start', { ip: '198.51.100.77' });
    expect((await rl.consume('start', { ip: '198.51.100.77' })).allowed).toBe(false);
    const keys = await admin.keys('*');
    expect(keys.join()).not.toContain('198.51.100.77');
    expect(keys.some((k) => k.includes('rlblock'))).toBe(true);
    await new Promise((r) => setTimeout(r, 1300));
    expect(await admin.keys('it:rl*')).toEqual([]);
  });
});

describe('two-process BFF on one Redis', () => {
  async function setup(logs: string[] = []) {
    const clock = new FakeClock();
    const kc = await createFakeKeycloak({ issuer: ISSUER, clientId: 'web', clientSecret: 's3cret', clock });
    const p1 = process_(kc, clock, newStore(), {}, logs);
    const p2 = process_(kc, clock, newStore(), {}, logs);
    return { clock, kc, p1, p2 };
  }

  it('parallel refresh across two processes calls Keycloak exactly once (no reuse)', async () => {
    const { clock, kc, p1, p2 } = await setup();
    const jar = await loginOn(p1, kc);
    const cookie = jar['__Host-pml_org'];
    const d1 = await p1.bff.internals();
    const d2 = await p2.bff.internals();
    clock.advanceSec(290);
    const calls = Array.from({ length: 60 }, (_v, i) => (i % 2 ? d1 : d2).tokens.getAccessToken(cookie));
    const results = await Promise.all(calls);
    expect(results.every((r) => r.ok)).toBe(true);
    expect(kc.stats.refreshCalls).toBe(1);
    expect(kc.stats.reuseDetected).toBe(0);
    expect(new Set(results.map((r) => (r.ok ? r.accessToken : ''))).size).toBe(1);
    // and again after the next expiry: rotation continues cleanly from the stored token
    clock.advanceSec(290);
    await Promise.all(Array.from({ length: 20 }, (_v, i) => (i % 2 ? d1 : d2).tokens.getAccessToken(cookie)));
    expect(kc.stats.refreshCalls).toBe(2);
    expect(kc.stats.reuseDetected).toBe(0);
    expect((await d1.sessions.load(cookie))!.record.rtVer).toBe(2);
  });

  it('rotation CAS: the loser of a write race keeps the winner\'s tokens', async () => {
    const { clock, kc, p1 } = await setup();
    const jar = await loginOn(p1, kc);
    const cookie = jar['__Host-pml_org'];
    const d = await p1.bff.internals();
    const a = (await d.sessions.load(cookie))!;
    const b = (await d.sessions.load(cookie))!;
    expect(await d.sessions.cas(a, { ...a.record, rtVer: 1, refreshToken: 'WINNER' })).toBe(true);
    expect(await d.sessions.cas(b, { ...b.record, rtVer: 1, refreshToken: 'LOSER' })).toBe(false);
    expect((await d.sessions.load(cookie))!.record.refreshToken).toBe('WINNER');
    void clock;
  });

  it('crash window: Keycloak rotated, the process died before the write -> next refresh hits reuse and the session ends cleanly', async () => {
    const { clock, kc, p1, p2 } = await setup();
    const jar = await loginOn(p1, kc);
    const cookie = jar['__Host-pml_org'];
    const d1 = await p1.bff.internals();
    const d2 = await p2.bff.internals();
    clock.advanceSec(290);
    // simulate the crash: the CAS never reaches Redis
    const real = d1.sessions.cas.bind(d1.sessions);
    d1.sessions.cas = async () => {
      throw new Error('process died');
    };
    const first = await d1.tokens.getAccessToken(cookie);
    d1.sessions.cas = real;
    expect(first.ok).toBe(true);
    expect(await admin.exists('bff:organizer:lock:refresh:' + d1.sessions.hashOf(cookie))).toBe(0); // lock released
    clock.advanceSec(290);
    const second = await d2.tokens.getAccessToken(cookie); // another process picks up the spent token
    expect(second).toEqual({ ok: false, reason: 'SESSION_ENDED' });
    expect(kc.stats.reuseDetected).toBe(1);
    expect(await d2.sessions.load(cookie)).toBeNull();
    expect(await admin.keys('bff:organizer:sess:*')).toEqual([]);
  });

  it('a crashed lock holder does not block others beyond the lock TTL', async () => {
    const clock = new FakeClock();
    const kc = await createFakeKeycloak({ issuer: ISSUER, clientId: 'web', clientSecret: 's3cret', clock });
    const p = process_(kc, clock, newStore(), { refresh: { pollMs: 10, waitMs: 3000, lockMs: 300 } });
    const jar = await loginOn(p, kc);
    const cookie = jar['__Host-pml_org'];
    const d = await p.bff.internals();
    await admin.set('bff:organizer:lock:refresh:' + d.sessions.hashOf(cookie), 'dead-owner', 'PX', 300);
    clock.advanceSec(290);
    const r = await d.tokens.getAccessToken(cookie);
    expect(r.ok).toBe(true);
    expect(kc.stats.refreshCalls).toBe(1);
  });

  it('waiters give up with REFRESH_BUSY instead of refreshing twice', async () => {
    const clock = new FakeClock();
    const kc = await createFakeKeycloak({ issuer: ISSUER, clientId: 'web', clientSecret: 's3cret', clock });
    const p = process_(kc, clock, newStore(), { refresh: { pollMs: 10, waitMs: 150, lockMs: 60_000 } });
    const jar = await loginOn(p, kc);
    const cookie = jar['__Host-pml_org'];
    const d = await p.bff.internals();
    await admin.set('bff:organizer:lock:refresh:' + d.sessions.hashOf(cookie), 'other-process', 'PX', 60_000);
    clock.advanceSec(290);
    expect(await d.tokens.getAccessToken(cookie)).toEqual({ ok: false, reason: 'REFRESH_BUSY' });
    expect(kc.stats.refreshCalls).toBe(0);
  });

  it('back-channel logout by sid on process B deletes the session created on process A, and only that one', async () => {
    const { kc, p1, p2 } = await setup();
    const a = await loginOn(p1, kc, staffUser, 'sid-A');
    const b = await loginOn(p1, kc, { ...staffUser, sub: 'kc-user-2' }, 'sid-B');
    const token = await kc.logoutToken({ sid: 'sid-A', sub: 'kc-user-1' });
    const res = await p2.bff.handlers['backchannel-logout'](
      p2.req('/api/auth/backchannel-logout', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ logout_token: token }).toString() })
    );
    expect(res.status).toBe(200);
    const d = await p1.bff.internals();
    expect(await d.sessions.load(a['__Host-pml_org'])).toBeNull();
    expect(await d.sessions.load(b['__Host-pml_org'])).not.toBeNull();
    expect(p2.identity.calls.map((c) => c.body)).toEqual([{ sid: 'sid-A', reason: 'backchannel_logout', revokedBy: 'organizer-web' }]);
    expect(await admin.exists('bff:organizer:tomb:sid:sid-A')).toBe(1);
    expect(await admin.smembers('bff:organizer:sid:sid-A')).toEqual([]);
    // replay is idempotent
    const again = await p1.bff.handlers['backchannel-logout'](
      p1.req('/api/auth/backchannel-logout', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ logout_token: token }).toString() })
    );
    expect(again.status).toBe(200);
    expect(p1.identity.calls).toHaveLength(0);
  });

  it('idle and absolute expiry with fake time, and the Redis TTL tracks the nearest deadline', async () => {
    const { clock, kc, p1 } = await setup();
    const jar = await loginOn(p1, kc);
    const cookie = jar['__Host-pml_org'];
    const d = await p1.bff.internals();
    const key = (await admin.keys('bff:organizer:sess:*'))[0];
    expect(await admin.ttl(key)).toBeLessThanOrEqual(1800);
    expect(await admin.ttl(key)).toBeGreaterThan(1700);
    clock.advanceSec(1700);
    expect(await d.sessions.load(cookie)).not.toBeNull(); // slides: idle restarts
    clock.advanceSec(1801);
    expect(await d.sessions.load(cookie)).toBeNull(); // idle exceeded
    expect(await admin.exists(key)).toBe(0); // and removed from Redis
    // absolute: keep the session alive with activity until the cap
    const jar2 = await loginOn(p1, kc);
    const c2 = jar2['__Host-pml_org'];
    for (let i = 0; i < 16; i++) {
      clock.advanceSec(1700);
      expect(await d.sessions.load(c2)).not.toBeNull();
    }
    clock.advanceSec(1700); // > 8 h since creation
    expect(await d.sessions.load(c2)).toBeNull();
  });

  it('Redis holds no plaintext token or cookie value, and nothing sensitive reaches the logs', async () => {
    const logs: string[] = [];
    const { clock, kc, p1 } = await setup(logs);
    const jar = await loginOn(p1, kc);
    const cookie = jar['__Host-pml_org'];
    const d = await p1.bff.internals();
    const rec = (await d.sessions.load(cookie))!.record;
    clock.advanceSec(290);
    await d.tokens.getAccessToken(cookie);
    await p1.bff.handlers.logout(p1.req('/api/auth/logout', { method: 'POST', cookies: jar, headers: { origin: APP, 'sec-fetch-site': 'same-origin' } }));
    await loginOn(p1, kc); // leave a live session behind to inspect
    let dump = '';
    for (const k of await admin.keys('*')) {
      const type = await admin.type(k);
      dump += k + '\n';
      if (type === 'string') dump += (await admin.get(k)) + '\n';
      if (type === 'hash') dump += JSON.stringify(await admin.hgetall(k)) + '\n';
      if (type === 'set') dump += (await admin.smembers(k)).join() + '\n';
    }
    for (const secret of [cookie, rec.accessToken, rec.refreshToken!, rec.idToken!, 'eyJ']) expect(dump).not.toContain(secret);
    const all = logs.join('\n');
    expect(all.length).toBeGreaterThan(20);
    for (const secret of [cookie, rec.accessToken, rec.refreshToken!, rec.idToken!, 's3cret', 'eyJ']) expect(all).not.toContain(secret);
  });

  it('CSRF, cookie flags and open redirect hold end to end', async () => {
    const { kc, p1 } = await setup();
    const s = await p1.bff.handlers.start(p1.req('/api/auth/start?next=' + encodeURIComponent('//evil.example/x')));
    const flowCookie = s.headers.getSetCookie()[0];
    expect(flowCookie).toMatch(/^__Host-pml_org_f=/);
    for (const flag of ['HttpOnly', 'Secure', 'Path=/', 'SameSite=Lax']) expect(flowCookie).toContain(flag);
    const jar = await loginOn(p1, kc);
    const evil = await p1.bff.handlers.logout(p1.req('/api/auth/logout', { method: 'POST', cookies: jar, headers: { origin: 'https://evil.example', 'sec-fetch-site': 'cross-site' } }));
    expect(evil.status).toBe(403);
    const d = await p1.bff.internals();
    expect(await d.sessions.load(jar['__Host-pml_org'])).not.toBeNull();
  });
});
