import { describe, expect, it } from 'vitest';
import { APP, jarFrom, makeHarness, type Harness } from './harness';

const buyer = (accountId: string, sub = `kc-${accountId}`) => ({ sub, accountId, roles: [], name: 'Buyer' });
const loc = (r: Response) => new URL(r.headers.get('location')!, APP);

async function buyerHarness(extra: Parameters<typeof makeHarness>[0] = {}) {
  return makeHarness({
    app: 'buyer',
    login: { mode: 'handoff' },
    access: { accountClaim: 'accountId', audience: 'myticketzm-api' },
    postLogin: async ({ accountId }) => {
      const status = 'ACTIVE';
      return status === 'ACTIVE' ? { ok: true, accountId: accountId! } : { ok: false, error: 'X' };
    },
    ...extra,
  });
}
/** What the buyer app's /api/identity/ensure route does after identity-service answered. */
async function issueHandle(h: Harness, jar: Record<string, string>, accountId: string, handle = 'HANDLE-1') {
  const res = await h.bff.flow.attachHandle(h.req('/api/identity/ensure', { method: 'POST', cookies: jar }), { value: handle, accountId, isNew: false, ttlSec: 120 });
  for (const c of res.setCookie) jarFrom(new Response(null, { headers: { 'set-cookie': c } }), jar);
  return jar;
}

describe('buyer handoff', () => {
  it('start sends the server-held handle as login_hint with prompt=login and max_age=0; the handle is single use and never read from the URL', async () => {
    const h = await buyerHarness();
    const jar = await issueHandle(h, {}, 'acc-1');
    const spoof = await h.bff.handlers.start(h.req('/api/auth/start?login_hint=ATTACKER&handle=ATTACKER', { cookies: { ...jar } }));
    const u = loc(spoof);
    expect(u.searchParams.get('login_hint')).toBe('HANDLE-1');
    expect(u.searchParams.get('prompt')).toBe('login');
    expect(u.searchParams.get('max_age')).toBe('0');
    expect(spoof.headers.get('referrer-policy')).toBe('no-referrer');
    jarFrom(spoof, jar);
    const again = await h.bff.handlers.start(h.req('/api/auth/start', { cookies: jar }));
    expect(loc(again).searchParams.get('error')).toBe('LOGIN_HANDLE_INVALID');
  });
  it('start without a handle (or with an expired one) is refused', async () => {
    const h = await buyerHarness();
    expect(loc(await h.bff.handlers.start(h.req('/api/auth/start'))).searchParams.get('error')).toBe('LOGIN_HANDLE_INVALID');
    const jar = await issueHandle(h, {}, 'acc-1');
    h.clock.advanceSec(121);
    expect(loc(await h.bff.handlers.start(h.req('/api/auth/start', { cookies: jar }))).searchParams.get('error')).toBe('LOGIN_HANDLE_INVALID');
  });
  async function run(h: Harness, issuedFor: string, loggedInAs: string, jar: Record<string, string> = {}) {
    await issueHandle(h, jar, issuedFor);
    const start = await h.bff.handlers.start(h.req('/api/auth/start?next=/my-tickets', { cookies: jar }));
    jarFrom(start, jar);
    const { code, state } = h.kc.issueCode(start.headers.get('location')!, buyer(loggedInAs));
    const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
    jarFrom(cb, jar);
    return { cb, jar };
  }
  it('accountId equality: matching account gets a session, SSO marker and device hint', async () => {
    const h = await buyerHarness();
    const { cb, jar } = await run(h, 'acc-1', 'acc-1');
    expect(loc(cb).pathname).toBe('/my-tickets');
    expect(jar['__Host-pml_buyer']).toBeTruthy();
    expect(jar['__Host-pml_buyer_sso']).toBe('1');
    expect(jar['__Host-pml_buyer_dev']).toBeTruthy();
    const deps = await h.deps();
    expect((await deps.sessions.load(jar['__Host-pml_buyer']))!.record.accountId).toBe('acc-1');
  });
  it('accountId mismatch (someone else authenticated at Keycloak): no session, Keycloak session ended, generic error', async () => {
    const h = await buyerHarness();
    const { cb, jar } = await run(h, 'acc-1', 'acc-2');
    const u = loc(cb);
    expect(u.pathname).toContain('/protocol/openid-connect/logout');
    expect(u.searchParams.get('post_logout_redirect_uri')).toContain('error=SIGN_IN_FAILED');
    expect(u.searchParams.get('id_token_hint')).toBeTruthy();
    expect(jar['__Host-pml_buyer']).toBeUndefined();
  });
  it('a stale auth_time (SSO reuse) is refused even for the right account', async () => {
    const h = await buyerHarness();
    const jar = await issueHandle(h, {}, 'acc-1');
    const start = await h.bff.handlers.start(h.req('/api/auth/start', { cookies: jar }));
    jarFrom(start, jar);
    const { code, state } = h.kc.issueCode(start.headers.get('location')!, buyer('acc-1'), { authTime: Math.floor(h.clock.now() / 1000) - 3600 });
    const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
    expect(loc(cb).pathname).toContain('/logout');
  });
  it('postLogin veto (account not ACTIVE) ends the Keycloak SSO and issues no cookie', async () => {
    const h = await buyerHarness({ postLogin: async () => ({ ok: false, error: 'ACCOUNT_SUSPENDED', endSso: true }) });
    const { cb, jar } = await run(h, 'acc-1', 'acc-1');
    expect(loc(cb).searchParams.get('post_logout_redirect_uri')).toContain('ACCOUNT_SUSPENDED');
    expect(jar['__Host-pml_buyer']).toBeUndefined();
  });
  it('the flow ext (cart) is merged into the session ext', async () => {
    const h = await buyerHarness();
    const jar: Record<string, string> = {};
    const r = await h.bff.flow.update(h.req('/x', { cookies: jar }), (rec) => ({ ...rec, ext: { cart: { eventId: 'e1' } } }));
    for (const c of r.setCookie) jarFrom(new Response(null, { headers: { 'set-cookie': c } }), jar);
    const { jar: out } = await run(h, 'acc-1', 'acc-1', jar);
    const deps = await h.deps();
    expect((await deps.sessions.load(out['__Host-pml_buyer']))!.record.ext).toEqual({ cart: { eventId: 'e1' } });
  });

  describe('RP-logout hop when a Keycloak SSO session may exist', () => {
    it('second login on the same device goes through end_session with the stored id_token_hint, then resumes', async () => {
      const h = await buyerHarness();
      const first = await run(h, 'acc-1', 'acc-1');
      const jar = first.jar;
      const deps = await h.deps();
      const idToken = (await deps.sessions.load(jar['__Host-pml_buyer']))!.record.idToken!;
      // user B arrives on the same device (our session was removed, the SSO marker remains)
      await deps.sessions.destroyByCookie(jar['__Host-pml_buyer']);
      delete jar['__Host-pml_buyer'];
      await issueHandle(h, jar, 'acc-2', 'HANDLE-B');
      const hop = await h.bff.handlers.start(h.req('/api/auth/start?next=/profile', { cookies: jar }));
      const u = loc(hop);
      expect(u.pathname).toContain('/protocol/openid-connect/logout');
      expect(u.searchParams.get('id_token_hint')).toBe(idToken);
      expect(u.searchParams.get('post_logout_redirect_uri')).toBe(`${APP}/api/auth/start?resume=1`);
      expect(u.searchParams.get('login_hint')).toBeNull();
      jarFrom(hop, jar);
      expect(jar['__Host-pml_buyer_sso']).toBeUndefined();
      const resume = await h.bff.handlers.start(h.req('/api/auth/start?resume=1', { cookies: jar }));
      const a = loc(resume);
      expect(a.searchParams.get('login_hint')).toBe('HANDLE-B');
      jarFrom(resume, jar);
      const { code, state } = h.kc.issueCode(resume.headers.get('location')!, buyer('acc-2'));
      const cb = await h.bff.handlers.callback(h.req(`/api/auth/callback?code=${code}&state=${state}`, { cookies: jar }));
      expect(loc(cb).pathname).toBe('/profile'); // returnTo survived the hop
    });
    it('a signed-in buyer starting another handoff has the old session removed locally', async () => {
      const h = await buyerHarness();
      const { jar } = await run(h, 'acc-1', 'acc-1');
      const old = jar['__Host-pml_buyer'];
      await issueHandle(h, jar, 'acc-2', 'HANDLE-B');
      const hop = await h.bff.handlers.start(h.req('/api/auth/start', { cookies: jar }));
      expect(loc(hop).pathname).toContain('/logout');
      expect(await (await h.deps()).sessions.load(old)).toBeNull();
    });
    it('no marker, no session: straight to authorize', async () => {
      const h = await buyerHarness();
      const jar = await issueHandle(h, {}, 'acc-1');
      expect(loc(await h.bff.handlers.start(h.req('/api/auth/start', { cookies: jar }))).pathname).toContain('/auth');
    });
  });
});
