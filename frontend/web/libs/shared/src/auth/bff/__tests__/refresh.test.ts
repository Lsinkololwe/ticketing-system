import { describe, expect, it } from 'vitest';
import { makeHarness, login, staffUser, type Harness } from './harness';

async function signedIn(over: Parameters<typeof makeHarness>[0] = {}) {
  const h = await makeHarness({ refresh: { pollMs: 5, waitMs: 800, lockMs: 2000 }, ...over });
  const { jar } = await login(h);
  const deps = await h.deps();
  const cookie = jar[deps.names.session];
  expect(cookie).toBeTruthy();
  return { h, jar, deps, cookie };
}
const expire = (h: Harness) => h.clock.advanceSec(290); // access token lives 300 s, skew 60 s

describe('TokenManager', () => {
  it('returns the cached token without touching Keycloak while fresh', async () => {
    const { deps, cookie, h } = await signedIn();
    const before = h.kc.stats.refreshCalls;
    const r = await deps.tokens.getAccessToken(cookie);
    expect(r.ok).toBe(true);
    expect(h.kc.stats.refreshCalls).toBe(before);
  });

  it('50 concurrent callers cause exactly one refresh and share the new token', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    const results = await Promise.all(Array.from({ length: 50 }, () => deps.tokens.getAccessToken(cookie)));
    expect(h.kc.stats.refreshCalls).toBe(1);
    expect(h.kc.stats.reuseDetected).toBe(0);
    const tokens = new Set(results.map((r) => (r.ok ? r.accessToken : 'x')));
    expect(tokens.size).toBe(1);
    expect([...tokens][0]).not.toBe('x');
  });

  it('rotation: the stored refresh token and rtVer advance; the next expiry rotates again without reuse', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    await deps.tokens.getAccessToken(cookie);
    const s1 = (await deps.sessions.load(cookie))!;
    expect(s1.record.rtVer).toBe(1);
    expire(h);
    await deps.tokens.getAccessToken(cookie);
    expect((await deps.sessions.load(cookie))!.record.rtVer).toBe(2);
    expect(h.kc.stats.reuseDetected).toBe(0);
  });

  it('invalid_grant destroys the session and reports SESSION_ENDED', async () => {
    const { deps, cookie, h } = await signedIn();
    h.kc.faults.refresh = ['invalid_grant'];
    expire(h);
    const r = await deps.tokens.getAccessToken(cookie);
    expect(r).toEqual({ ok: false, reason: 'SESSION_ENDED' });
    expect(await deps.sessions.load(cookie)).toBeNull();
  });

  it.each(['5xx', 'timeout'] as const)('%s keeps the session and reports UPSTREAM_UNAVAILABLE; the next call recovers', async (fault) => {
    const { deps, cookie, h } = await signedIn();
    h.kc.faults.refresh = [fault];
    expire(h);
    expect(await deps.tokens.getAccessToken(cookie)).toEqual({ ok: false, reason: 'UPSTREAM_UNAVAILABLE' });
    expect(await deps.sessions.load(cookie)).not.toBeNull();
    const again = await deps.tokens.getAccessToken(cookie);
    expect(again.ok).toBe(true);
    expect(h.kc.stats.reuseDetected).toBe(0);
  });

  it('a session logged out while the refresh was in flight is not resurrected', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    h.kc.faults.afterRefresh = () => void deps.sessions.destroyByCookie(cookie);
    // destroy is async; await it through a microtask turn inside the fake token endpoint
    const r = await deps.tokens.getAccessToken(cookie);
    await new Promise((res) => setTimeout(res, 20));
    expect(await deps.sessions.load(cookie)).toBeNull();
    expect(r.ok === false || r.ok === true).toBe(true);
  });

  it('CAS conflict from a concurrent touch re-applies the NEW refresh token on top', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    // between "reload" and "cas" another writer bumps the store version (e.g. ext update)
    h.kc.faults.afterRefresh = () => {
      void (async () => {
        const cur = (await deps.sessions.load(cookie, { touch: false }))!;
        await deps.sessions.cas(cur, { ...cur.record, ext: { cart: [1] } });
      })();
    };
    const r = await deps.tokens.getAccessToken(cookie);
    expect(r.ok).toBe(true);
    const after = (await deps.sessions.load(cookie))!;
    expect(after.record.rtVer).toBe(1);
    expect(after.record.accessToken).toBe(r.ok ? r.accessToken : '');
  });

  it('losing a required role on refresh ends the session', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    // the user is demoted at Keycloak: new tokens carry no ORGANIZER role
    (staffUser.roles as string[]).splice(0, 1, 'VIEWER');
    try {
      const r = await deps.tokens.getAccessToken(cookie);
      expect(r).toEqual({ ok: false, reason: 'SESSION_ENDED' });
      expect(await deps.sessions.load(cookie)).toBeNull();
    } finally {
      (staffUser.roles as string[]).splice(0, 1, 'ORGANIZER');
    }
  });
});

describe('crash window', () => {
  it('store failure while persisting: retries with jitter and keeps the session', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    let failures = 2;
    const store = h.store as import('../store').MemoryStore;
    store.failNext = (op) => op === 'casSession' && failures-- > 0;
    const r = await deps.tokens.getAccessToken(cookie);
    store.failNext = null;
    expect(r.ok).toBe(true);
    expect((await deps.sessions.load(cookie))!.record.rtVer).toBe(1);
  });

  it('process dies after Keycloak rotated but before the write: the spent token trips reuse and the session ends', async () => {
    const { deps, cookie, h } = await signedIn();
    expire(h);
    const store = h.store as import('../store').MemoryStore;
    store.failNext = (op) => op === 'casSession'; // never persisted (all retries fail)
    const first = await deps.tokens.getAccessToken(cookie);
    store.failNext = null;
    expect(first.ok).toBe(true); // this request still has a valid token
    expect(h.kc.stats.refreshCalls).toBe(1);
    expire(h);
    const second = await deps.tokens.getAccessToken(cookie);
    expect(second).toEqual({ ok: false, reason: 'SESSION_ENDED' });
    expect(h.kc.stats.reuseDetected).toBe(1);
    expect(await deps.sessions.load(cookie)).toBeNull();
  });
});
