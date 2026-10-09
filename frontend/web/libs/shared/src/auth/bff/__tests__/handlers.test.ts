import { describe, expect, it } from 'vitest';
import { jarFrom, login, makeHarness, staffUser, APP, ISSUER } from './harness';

const loc = (r: Response) => new URL(r.headers.get('location')!, APP);

describe('start', () => {
  it('redirects to the authorize endpoint with PKCE S256, state, nonce, scopes and sets a flow cookie', async () => {
    const h = await makeHarness();
    const res = await h.bff.handlers.start(h.req('/api/auth/start?next=/dashboard'));
    expect(res.status).toBe(303);
    const u = loc(res);
    expect(u.origin + u.pathname).toBe(`${ISSUER}/protocol/openid-connect/auth`);
    expect(u.searchParams.get('code_challenge_method')).toBe('S256');
    expect(u.searchParams.get('code_challenge')).toHaveLength(43);
    expect(u.searchParams.get('state')).toBeTruthy();
    expect(u.searchParams.get('nonce')).toBeTruthy();
    expect(u.searchParams.get('client_id')).toBe('web');
    expect(u.searchParams.get('redirect_uri')).toBe(`${APP}/api/auth/callback`);
    expect(u.searchParams.get('scope')).toBe('openid profile');
    expect(u.searchParams.get('login_hint')).toBeNull();
    expect(res.headers.get('cache-control')).toBe('no-store');
    expect(res.headers.get('referrer-policy')).toBe('no-referrer');
    expect(res.headers.getSetCookie().join('\n')).toContain('pml_org_f=');
  });
  it('refuses cross-site navigations', async () => {
    const h = await makeHarness();
    const res = await h.bff.handlers.start(h.req('/api/auth/start', { headers: { 'sec-fetch-site': 'cross-site' } }));
    expect(loc(res).pathname).toBe('/login');
    expect(loc(res).searchParams.get('error')).toBe('LOGIN_FAILED');
  });
  it('rate limits per IP (429 + Retry-After) using the trusted hop, not a spoofed leftmost XFF', async () => {
    const h = await makeHarness({ rateLimits: { start: [{ subject: 'ip', limit: 2, windowSec: 60 }] } });
    const go = (xff: string) => h.bff.handlers.start(h.req('/api/auth/start', { headers: { 'x-forwarded-for': xff } }));
    expect((await go('9.9.9.1, 203.0.113.5')).status).toBe(303);
    expect((await go('9.9.9.2, 203.0.113.5')).status).toBe(303);
    const third = await go('9.9.9.3, 203.0.113.5'); // new leftmost, same real client
    expect(third.status).toBe(429);
    expect(Number(third.headers.get('retry-after'))).toBeGreaterThan(0);
    expect((await go('9.9.9.3, 203.0.113.6')).status).toBe(303);
  });
  it('sanitises next (open redirect)', async () => {
    const h = await makeHarness();
    const { jar, cb } = await login(h, staffUser, { next: '//evil.example/x' });
    void jar;
    expect(loc(cb).pathname).toBe('/');
    expect(loc(cb).origin).toBe(APP);
  });
  it('an already signed-in user skips Keycloak', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    const again = await h.bff.handlers.start(h.req('/api/auth/start?next=/events', { cookies: jar }));
    expect(loc(again).pathname).toBe('/events');
  });
});

describe('callback', () => {
  it('creates a session with hardened cookie flags and lands on the sanitised returnTo', async () => {
    const h = await makeHarness();
    const { cb, jar } = await login(h, staffUser, { next: '/dashboard?tab=1' });
    expect(loc(cb).pathname + loc(cb).search).toBe('/dashboard?tab=1');
    const cookies = cb.headers.getSetCookie();
    const sess = cookies.find((c) => c.startsWith('__Host-pml_org='))!;
    expect(sess).toBeTruthy();
    expect(sess).toMatch(/HttpOnly/);
    expect(sess).toMatch(/Secure/);
    expect(sess).toMatch(/Path=\//);
    expect(sess).toMatch(/SameSite=Lax/);
    expect(sess).not.toMatch(/Domain/i);
    expect(cookies.find((c) => c.startsWith('__Host-pml_org_f=') && /Max-Age=0/.test(c))).toBeTruthy();
    expect(jar['__Host-pml_org']).toBeTruthy();
    expect(cb.headers.get('cache-control')).toBe('no-store');
  });
  it('platform admin cookies are SameSite=Strict', async () => {
    const h = await makeHarness({ app: 'admin' });
    const { cb } = await login(h);
    expect(cb.headers.getSetCookie().find((c) => c.startsWith('__Host-pml_admin='))).toMatch(/SameSite=Strict/);
  });
  it('plain http dev drops __Host- and Secure', async () => {
    const h = await makeHarness({ appUrl: 'http://localhost:3000' });
    const start = await h.bff.handlers.start(new Request('http://localhost:3000/api/auth/start'));
    const flow = start.headers.getSetCookie()[0];
    expect(flow).toMatch(/^pml_org_f=/);
    expect(flow).not.toMatch(/Secure/);
  });
  it('the session record carries sid, roles, audience; the browser gets none of it', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    const deps = await h.deps();
    const s = (await deps.sessions.load(jar['__Host-pml_org']))!;
    expect(s.record).toMatchObject({ kcSid: expect.stringMatching(/^sid-/), sub: 'kc-user-1', roles: ['ORGANIZER'], aud: expect.arrayContaining(['myticketzm-api']) });
    const pub = await h.bff.handlers.session(h.req('/api/auth/session', { cookies: jar }));
    const text = await pub.text();
    expect(text).not.toMatch(/eyJ|accessToken|refreshToken|idToken/);
    expect(JSON.parse(text)).toMatchObject({ authenticated: true, roles: ['ORGANIZER'] });
  });
  it.each([
    ['state mismatch', (q: URLSearchParams) => q.set('state', 'wrong')],
    ['missing code', (q: URLSearchParams) => q.delete('code')],
    ['provider error', (q: URLSearchParams) => { q.set('error', 'access_denied'); q.delete('code'); }],
  ])('rejects %s and creates no session', async (_n, mutate) => {
    const h = await makeHarness();
    const start = await h.bff.handlers.start(h.req('/api/auth/start'));
    const jar = jarFrom(start);
    const { code, state } = h.kc.issueCode(start.headers.get('location')!, staffUser);
    const q = new URLSearchParams({ code, state });
    mutate(q);
    const res = await h.bff.handlers.callback(h.req(`/api/auth/callback?${q}`, { cookies: jar }));
    expect(loc(res).pathname).toBe('/login');
    expect(loc(res).searchParams.get('error')).toBe('SIGN_IN_FAILED');
    expect(res.headers.getSetCookie().join()).not.toMatch(/__Host-pml_org=[^;]+;/);
  });
  it('rejects a nonce mismatch (id token minted for another authorization request)', async () => {
    const h = await makeHarness();
    const s1 = await h.bff.handlers.start(h.req('/api/auth/start'));
    const jar = jarFrom(s1);
    const other = await h.bff.handlers.start(h.req('/api/auth/start'));
    const { code } = h.kc.issueCode(other.headers.get('location')!, staffUser); // other nonce
    const state = new URL(s1.headers.get('location')!).searchParams.get('state')!;
    const res = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
    expect(loc(res).searchParams.get('error')).toBe('SIGN_IN_FAILED');
  });
  it('rejects a replayed code (second callback with the same flow)', async () => {
    const h = await makeHarness();
    const start = await h.bff.handlers.start(h.req('/api/auth/start'));
    const jar = jarFrom(start);
    const { code, state } = h.kc.issueCode(start.headers.get('location')!, staffUser);
    const first = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: { ...jar } }));
    expect(loc(first).searchParams.get('error')).toBeNull();
    const second = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: { ...jar } }));
    expect(loc(second).searchParams.get('error')).toBe('SIGN_IN_FAILED');
  });
  it('a user without an allowed role is refused and the Keycloak SSO is ended', async () => {
    const h = await makeHarness();
    const { cb, jar } = await login(h, { sub: 'u2', roles: ['BUYER'] });
    const u = loc(cb);
    expect(u.pathname).toContain('/protocol/openid-connect/logout');
    expect(u.searchParams.get('id_token_hint')).toBeTruthy();
    expect(u.searchParams.get('post_logout_redirect_uri')).toContain('/login?error=FORBIDDEN');
    expect(jar['__Host-pml_org']).toBeUndefined();
    expect(h.kc.stats.revokeCalls).toBe(1);
  });
  it('the audience must contain the configured resource', async () => {
    const h = await makeHarness({ access: { roles: ['ORGANIZER'], audience: 'some-other-api' } });
    const { cb } = await login(h);
    expect(loc(cb).searchParams.get('post_logout_redirect_uri')).toContain('error=FORBIDDEN');
  });
  it('requires the account claim when configured', async () => {
    const h = await makeHarness({ access: { roles: ['ORGANIZER'], accountClaim: 'accountId' } });
    const { jar: j1 } = await login(h, { ...staffUser, accountId: 'acc-77' });
    expect((await (await h.deps()).sessions.load(j1['__Host-pml_org']))!.record.accountId).toBe('acc-77');
    const h2 = await makeHarness({ access: { roles: ['ORGANIZER'], accountClaim: 'accountId' } });
    const { jar: j2 } = await login(h2, staffUser);
    expect(j2['__Host-pml_org']).toBeUndefined();
  });
  it('postLogin can veto (and end SSO) before any cookie is issued', async () => {
    const h = await makeHarness({ postLogin: async () => ({ ok: false, error: 'ACCOUNT_SUSPENDED', endSso: true }) });
    const { cb, jar } = await login(h);
    expect(loc(cb).searchParams.get('post_logout_redirect_uri')).toContain('ACCOUNT_SUSPENDED');
    expect(jar['__Host-pml_org']).toBeUndefined();
  });
  it('a backchannel logout that raced the login (tombstone) wins', async () => {
    const h = await makeHarness();
    const deps = await h.deps();
    await deps.flows.tombstone('sid-raced');
    const { jar, cb } = await login(h, staffUser, { sid: 'sid-raced' });
    expect(loc(cb).searchParams.get('error')).toBe('SIGN_IN_FAILED');
    expect(jar['__Host-pml_org']).toBeUndefined();
  });
  it('repeated failures from one IP are blocked (15 min)', async () => {
    const h = await makeHarness();
    for (let i = 0; i < 5; i++) await h.bff.handlers.callback(h.req('/api/auth/callback?code=x&state=y'));
    const res = await h.bff.handlers.callback(h.req('/api/auth/callback?code=x&state=y'));
    expect(res.status).toBe(429);
  });
});

describe('logout', () => {
  const post = (h: Awaited<ReturnType<typeof makeHarness>>, jar: Record<string, string>, headers: Record<string, string> = {}) =>
    h.bff.handlers.logout(h.req('/api/auth/logout', { method: 'POST', cookies: jar, headers: { origin: APP, 'sec-fetch-site': 'same-origin', ...headers } }));

  it('deletes the session, revokes sid+jti at identity-service, ends the Keycloak SSO session from the server and revokes the refresh token', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    const deps = await h.deps();
    const sess = (await deps.sessions.load(jar['__Host-pml_org']))!;
    const res = await post(h, jar);
    expect(res.status).toBe(303);
    // Keycloak was told to end the whole session, so the browser needs no hop through its end-session page.
    expect(res.headers.get('location')).toBe(`${APP}/`);
    expect(h.kc.stats.endSessionCalls).toBe(1);
    expect(h.kc.endedSessions).toEqual([sess.record.idToken]);
    expect(await deps.sessions.load(jar['__Host-pml_org'])).toBeNull();
    expect(h.identity.calls).toHaveLength(1);
    expect(h.identity.calls[0].path).toBe('/api/internal/revocations/logout');
    expect(h.identity.calls[0].body).toMatchObject({ sid: sess.record.kcSid, reason: 'user_logout', revokedBy: 'organizer-web' });
    // A sign-out must not revoke the subject: that would lock the person out of signing straight back in.
    expect(h.identity.calls[0].body).not.toHaveProperty('sub');
    expect(h.identity.calls[0].body.jti).toBeTruthy();
    expect(h.kc.stats.revokeCalls).toBe(1);
    expect(res.headers.getSetCookie().filter((c) => /Max-Age=0/.test(c)).length).toBeGreaterThanOrEqual(2);
  });
  it('when Keycloak cannot be reached from the server it falls back to ending the session from the browser', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    const sess = (await (await h.deps()).sessions.load(jar['__Host-pml_org']))!;
    h.kc.faults.endSession = true;
    const res = await post(h, jar);
    expect(res.status).toBe(303);
    const u = loc(res);
    expect(u.pathname).toContain('/protocol/openid-connect/logout');
    expect(u.searchParams.get('id_token_hint')).toBe(sess.record.idToken);
    expect(u.searchParams.get('post_logout_redirect_uri')).toBe(`${APP}/`);
  });
  it('identity-service failure never blocks the user: session gone, retry queued', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    h.identity.status = 503;
    const res = await post(h, jar);
    expect(res.status).toBe(303);
    expect(await (await h.deps()).sessions.load(jar['__Host-pml_org'])).toBeNull();
    expect(h.logs.join()).toContain('auth.logout.revocation_failed');
    expect(await h.store.get('x')).toBeNull();
  });
  it('is POST only and CSRF checked', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    expect((await h.bff.handlers.logout(h.req('/api/auth/logout', { cookies: jar }))).status).toBe(405);
    const evil = await post(h, jar, { origin: 'https://evil.example' });
    expect(evil.status).toBe(403);
    const crossSite = await post(h, jar, { 'sec-fetch-site': 'cross-site' });
    expect(crossSite.status).toBe(403);
    expect(await (await h.deps()).sessions.load(jar['__Host-pml_org'])).not.toBeNull();
  });
  it('without a session it just clears cookies and goes home', async () => {
    const h = await makeHarness();
    const res = await post(h, {});
    expect(loc(res).pathname).toBe('/');
    expect(h.identity.calls).toHaveLength(0);
  });
});

describe('back-channel logout', () => {
  const bcl = async (h: Awaited<ReturnType<typeof makeHarness>>, token: string, ct = 'application/x-www-form-urlencoded') =>
    h.bff.handlers['backchannel-logout'](h.req('/api/auth/backchannel-logout', { method: 'POST', headers: { 'content-type': ct }, body: new URLSearchParams({ logout_token: token }).toString() }));

  it('deletes exactly the sessions of that sid and calls the idempotent revocation', async () => {
    const h = await makeHarness();
    const a = await login(h, staffUser, { sid: 'sid-A' });
    const b = await login(h, { ...staffUser, sub: 'kc-user-2' }, { sid: 'sid-B' });
    const res = await bcl(h, await h.kc.logoutToken({ sid: 'sid-A', sub: 'kc-user-1' }));
    expect(res.status).toBe(200);
    expect(res.headers.get('cache-control')).toBe('no-store');
    const deps = await h.deps();
    expect(await deps.sessions.load(a.jar['__Host-pml_org'])).toBeNull();
    expect(await deps.sessions.load(b.jar['__Host-pml_org'])).not.toBeNull();
    expect(h.identity.calls[0].body).toMatchObject({ sid: 'sid-A', reason: 'backchannel_logout' });
    expect(await deps.flows.isTombstoned('sid-A')).toBe(true);
  });
  it('sub-only tokens remove all sessions of the user', async () => {
    const h = await makeHarness();
    const a = await login(h, staffUser, { sid: 's1' });
    const b = await login(h, staffUser, { sid: 's2' });
    expect((await bcl(h, await h.kc.logoutToken({ sub: 'kc-user-1' }))).status).toBe(200);
    const deps = await h.deps();
    expect(await deps.sessions.load(a.jar['__Host-pml_org'])).toBeNull();
    expect(await deps.sessions.load(b.jar['__Host-pml_org'])).toBeNull();
  });
  it('replayed jti is acknowledged without a second revocation call', async () => {
    const h = await makeHarness();
    await login(h, staffUser, { sid: 'sid-R' });
    const t = await h.kc.logoutToken({ sid: 'sid-R' });
    expect((await bcl(h, t)).status).toBe(200);
    expect((await bcl(h, t)).status).toBe(200);
    expect(h.identity.calls).toHaveLength(1);
  });
  it.each([
    ['bad signature', async (h: Awaited<ReturnType<typeof makeHarness>>) => h.kc.logoutToken({ sid: 's' }, { key: await h.kc.foreignKey() })],
    ['wrong audience', async (h: Awaited<ReturnType<typeof makeHarness>>) => h.kc.logoutToken({ sid: 's' }, { aud: 'other-client' })],
    ['carries a nonce', async (h: Awaited<ReturnType<typeof makeHarness>>) => h.kc.logoutToken({ sid: 's', nonce: 'n' })],
    ['no events claim', async (h: Awaited<ReturnType<typeof makeHarness>>) => h.kc.sign({ iss: ISSUER, aud: 'web', iat: Math.floor(h.clock.now() / 1000), jti: 'j', sid: 's' })],
    ['neither sid nor sub', async (h: Awaited<ReturnType<typeof makeHarness>>) => h.kc.logoutToken({})],
    ['too old', async (h: Awaited<ReturnType<typeof makeHarness>>) => h.kc.logoutToken({ sid: 's', iat: Math.floor(h.clock.now() / 1000) - 600 })],
    ['not a jwt', async () => 'nope'],
  ])('rejects %s with 400 and touches nothing', async (_n, make) => {
    const h = await makeHarness();
    const { jar } = await login(h, staffUser, { sid: 's' });
    const res = await bcl(h, await make(h));
    expect(res.status).toBe(400);
    expect(await res.text()).toBe('');
    expect(await (await h.deps()).sessions.load(jar['__Host-pml_org'])).not.toBeNull();
    expect(h.identity.calls).toHaveLength(0);
  });
  it('wrong content type -> 400; GET -> 405 with no information', async () => {
    const h = await makeHarness();
    expect((await bcl(h, 'x', 'application/json')).status).toBe(400);
    const get = await h.bff.handlers['backchannel-logout'](h.req('/api/auth/backchannel-logout'));
    expect(get.status).toBe(405);
    expect(await get.text()).toBe('');
  });
  it('infrastructure failure is 503 (Keycloak retries) and the retry then succeeds', async () => {
    const h = await makeHarness();
    const { jar } = await login(h, staffUser, { sid: 'sid-F' });
    h.identity.status = 500;
    const t = await h.kc.logoutToken({ sid: 'sid-F' });
    expect((await bcl(h, t)).status).toBe(503);
    h.identity.status = 200;
    expect((await bcl(h, t)).status).toBe(200);
    expect(await (await h.deps()).sessions.load(jar['__Host-pml_org'])).toBeNull();
  });
});

describe('step-up', () => {
  it('requires a session, forces re-authentication with max_age, and keeps the same identity', async () => {
    const h = await makeHarness();
    const anon = await h.bff.handlers.stepup(h.req('/api/auth/stepup?next=/billing'));
    expect(loc(anon).pathname).toBe('/login');
    const { jar } = await login(h);
    h.clock.advanceSec(600);
    const res = await h.bff.handlers.stepup(h.req('/api/auth/stepup?next=/billing&maxAge=120', { cookies: jar }));
    const u = loc(res);
    expect(u.searchParams.get('prompt')).toBe('login');
    expect(u.searchParams.get('max_age')).toBe('120');
    jarFrom(res, jar);
    const old = jar['__Host-pml_org'];
    const { code, state } = h.kc.issueCode(u.toString(), staffUser, { sid: 'sid-new' });
    const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
    jarFrom(cb, jar);
    expect(loc(cb).pathname).toBe('/billing');
    expect(jar['__Host-pml_org']).not.toBe(old); // rotated
    const deps = await h.deps();
    expect(await deps.sessions.load(old)).toBeNull();
    const s = (await deps.sessions.load(jar['__Host-pml_org']))!;
    expect(s.record.authTime).toBe(Math.floor(h.clock.now() / 1000));
  });
  it('a different user completing the step-up is refused', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    const res = await h.bff.handlers.stepup(h.req('/api/auth/stepup?next=/billing', { cookies: jar }));
    jarFrom(res, jar);
    const { code, state } = h.kc.issueCode(res.headers.get('location')!, { sub: 'someone-else', roles: ['ORGANIZER'] });
    const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
    expect(loc(cb).pathname).toContain('/logout');
  });
  it('an SSO-reused (stale auth_time) answer does not count as fresh', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    h.clock.advanceSec(1000);
    const res = await h.bff.handlers.stepup(h.req('/api/auth/stepup?next=/billing', { cookies: jar }));
    jarFrom(res, jar);
    const { code, state } = h.kc.issueCode(res.headers.get('location')!, staffUser, { authTime: Math.floor(h.clock.now() / 1000) - 900 });
    const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
    expect(loc(cb).pathname).toContain('/logout');
  });
});

describe('dispatcher', () => {
  it('routes by [action], 404s unknown actions and 405s wrong methods', async () => {
    const h = await makeHarness();
    const r = (a: string, m = 'GET') => h.bff.handlers.GET(h.req(`/api/auth/${a}`, { method: m }), { params: Promise.resolve({ action: a }) });
    expect((await r('session')).status).toBe(200);
    expect((await r('nope')).status).toBe(404);
    expect((await r('logout')).status).toBe(405);
  });
});

describe('logging', () => {
  it('a whole login/refresh/logout lifecycle logs no token, code, state or cookie value', async () => {
    const h = await makeHarness();
    const { jar, authUrl } = await login(h);
    const deps = await h.deps();
    const sess = (await deps.sessions.load(jar['__Host-pml_org']))!;
    h.clock.advanceSec(290);
    await deps.tokens.getAccessToken(jar['__Host-pml_org']);
    h.kc.faults.refresh = ['5xx'];
    h.clock.advanceSec(290);
    await deps.tokens.getAccessToken(jar['__Host-pml_org']);
    await h.bff.handlers.callback(h.req('/api/auth/callback?code=SECRETCODE&state=SECRETSTATE', { cookies: jar }));
    await h.bff.handlers.logout(h.req('/api/auth/logout', { method: 'POST', cookies: jar, headers: { origin: APP, 'sec-fetch-site': 'same-origin' } }));
    const all = h.logs.join('\n');
    expect(all.length).toBeGreaterThan(50);
    for (const secret of [jar['__Host-pml_org'], sess.record.accessToken, sess.record.refreshToken!, sess.record.idToken!, 'SECRETCODE', 'SECRETSTATE', new URL(authUrl).searchParams.get('state')!, 's3cret']) {
      expect(all).not.toContain(secret);
    }
    expect(all).not.toMatch(/eyJ/);
  });
});
