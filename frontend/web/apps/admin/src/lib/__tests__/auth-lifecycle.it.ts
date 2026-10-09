import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import path from 'node:path';
import IORedis from 'ioredis';
import { NextRequest } from 'next/server';
import { afterAll, beforeAll, describe, expect, inject, it, vi } from 'vitest';
import { BffAuthError, createBff } from '@pml.tickets/shared/auth/bff';
import { createLogger } from '@pml.tickets/shared/auth/bff/logger';
import { RedisStore } from '@pml.tickets/shared/auth/bff/store';
import { composeFetch } from '@pml.tickets/shared/auth/bff/testing';
import { Browser } from '@pml.tickets/shared/auth/bff/__integration__/browser';
import type {} from '@pml.tickets/shared/auth/bff/__integration__/global-setup';
import { identityStub, jarFrom } from '@pml.tickets/shared/auth/bff/__tests__/harness';
import { adminBffConfig, adminCsp, STAFF_ACCESS_ROLES, STEP_UP_SEC } from '@/lib/bffConfig';

/**
 * The admin app's REAL configuration (lib/bffConfig.ts) against a real Keycloak 26.5.2 (realm export
 * docker-resources/keycloak/myticketzm-admin-realm.json, placeholders substituted) and a real Redis:
 *
 *   login -> callback -> session -> step-up -> refresh -> back-channel logout -> logout
 *
 * Opt in: BFF_IT_KEYCLOAK=1 npx vitest run -c apps/admin/vitest.it.config.ts
 */
const enabled = process.env.BFF_IT_KEYCLOAK === '1';
const REALM_FILE = path.resolve(import.meta.dirname, '../../../../../../../../docker-resources/keycloak/myticketzm-admin-realm.json');
const GATEWAY_AUD = 'myticketzm-api-gateway';
const SECRET = 'it-client-secret';
/** The staff realm requires a second factor, so the test user is seeded with an authenticator of this secret. */
const TOTP_SECRET = 'AbCdEfGhIjKlMnOpQrSt';

/** RFC 6238 code (HMAC-SHA1, 6 digits, 30 s); Keycloak keys the HMAC with the secret string's own bytes. */
function totp(secret: string, atMs = Date.now()): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(atMs / 30_000)));
  const hash = createHmac('sha1', Buffer.from(secret, 'utf8')).update(counter).digest();
  const o = hash[hash.length - 1] & 0x0f;
  const bin = ((hash[o] & 0x7f) << 24) | (hash[o + 1] << 16) | (hash[o + 2] << 8) | hash[o + 3];
  return String(bin % 1_000_000).padStart(6, '0');
}

// requireSession() reads the request cookies through next/headers; this test is the "request".
const requestCookies = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({ get: (n: string) => (requestCookies.has(n) ? { name: n, value: requestCookies.get(n)! } : undefined) }),
  headers: async () => new Headers({ 'x-pml-path': '/finance/payouts' }),
}));
vi.mock('next/navigation', () => ({
  redirect: (url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  },
}));

describe.skipIf(!enabled)('admin BFF: real Keycloak 26.5.2 + Redis', () => {
  let container: import('testcontainers').StartedTestContainer;
  let issuer = '';
  let appUrl = '';
  let bclServer: Server;
  let redis: IORedis;
  let bff: ReturnType<typeof createBff>;
  const identity = identityStub();
  let offsetMs = 0;
  let kcRefreshCalls = 0;
  const logs: string[] = [];
  const cookieName = 'pml_admin'; // plain name: the test app is http://localhost

  const appReq = (p: string, init: RequestInit & { jar?: Record<string, string> } = {}) => {
    const { jar, ...i } = init;
    const headers = new Headers(i.headers);
    if (jar) headers.set('cookie', Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '));
    if (!headers.has('x-forwarded-for')) headers.set('x-forwarded-for', '198.51.100.4');
    return new Request(p.startsWith('http') ? p : appUrl + p, { ...i, headers });
  };
  const asRequest = (jar: Record<string, string>) => {
    requestCookies.clear();
    for (const [k, v] of Object.entries(jar)) requestCookies.set(k, v);
  };

  beforeAll(async () => {
    const { GenericContainer, Wait } = await import('testcontainers');
    bclServer = createServer(async (req, res) => {
      const chunks: Buffer[] = [];
      for await (const c of req) chunks.push(c as Buffer);
      const r = await bff.handlers['backchannel-logout'](
        new Request(appUrl + req.url, { method: req.method, headers: { 'content-type': String(req.headers['content-type'] ?? '') }, body: req.method === 'POST' ? Buffer.concat(chunks) : undefined })
      );
      res.statusCode = r.status;
      res.end();
    });
    await new Promise<void>((r) => bclServer.listen(0, '0.0.0.0', r));
    const appPort = (bclServer.address() as AddressInfo).port;
    appUrl = `http://localhost:${appPort}`;

    const realm = JSON.parse(
      readFileSync(REALM_FILE, 'utf8')
        .replaceAll('${ADMIN_WEB_CLIENT_SECRET}', SECRET)
        .replaceAll('${TICKETING_ADMIN_APP_URL}', appUrl)
        .replaceAll('${TICKETING_API_GATEWAY_CLIENT_ID}', GATEWAY_AUD)
    );
    delete realm.loginTheme;
    // The lifecycle signs in several times inside one 30-second step, so a code may be reused here; the one-time-code
    // policy itself is covered against the real realm by the Keycloak extension's RealmConformanceIT.
    realm.otpPolicyCodeReusable = true;
    realm.users[0].credentials.push({
      type: 'otp',
      secretData: JSON.stringify({ value: TOTP_SECRET }),
      credentialData: JSON.stringify({ subType: 'totp', digits: 6, period: 30, algorithm: 'HmacSHA1', counter: 0 }),
    });
    realm.clients[0].attributes['backchannel.logout.url'] = `http://host.docker.internal:${appPort}/api/auth/backchannel-logout`;

    container = await new GenericContainer('quay.io/keycloak/keycloak:26.5.2')
      .withExposedPorts(8080)
      .withEnvironment({ KC_BOOTSTRAP_ADMIN_USERNAME: 'admin', KC_BOOTSTRAP_ADMIN_PASSWORD: 'admin', KC_HOSTNAME_STRICT: 'false', KC_HEALTH_ENABLED: 'false' })
      .withCommand(['start-dev', '--import-realm'])
      .withExtraHosts([{ host: 'host.docker.internal', ipAddress: 'host-gateway' }])
      .withCopyContentToContainer([{ content: JSON.stringify(realm), target: '/opt/keycloak/data/import/realm.json' }])
      .withWaitStrategy(Wait.forHttp('/realms/myticketzm-admin/.well-known/openid-configuration', 8080).forStatusCode(200).withStartupTimeout(200_000))
      .start();
    issuer = `http://${container.getHost()}:${container.getMappedPort(8080)}/realms/myticketzm-admin`;

    redis = new IORedis(inject('redisUrl'));
    const env = {
      APP_URL: appUrl,
      KEYCLOAK_ISSUER: issuer,
      KEYCLOAK_CLIENT_ID: 'myticketzm-admin',
      KEYCLOAK_CLIENT_SECRET: SECRET,
      BFF_ENC_KEYS: `k1:${Buffer.alloc(32, 9).toString('base64')}`,
      API_AUDIENCE: GATEWAY_AUD,
      TRUST_PROXY_HOPS: '1',
      GRAPHQL_URL: 'http://gateway.test/graphql',
    };
    bff = createBff(
      adminBffConfig(env, (n) => env[n as keyof typeof env] as string, {
        production: false,
        redis: { store: new RedisStore(redis) },
        clock: () => Date.now() + offsetMs,
        fetch: composeFetch(identity.fetch, async (input, init) => {
          if (String(input).endsWith('/protocol/openid-connect/token') && String(init?.body).includes('grant_type=refresh_token')) kcRefreshCalls++;
          return fetch(input, init);
        }),
        logger: createLogger({ app: 'admin', level: 'debug', sink: (l) => logs.push(l) }),
        identity: { baseUrl: 'http://identity.test', tokenUrl: 'http://identity.test/token', clientId: 'svc', clientSecret: 'x' },
        refresh: { pollMs: 20, waitMs: 8000, lockMs: 8000 },
      }),
      { csp: adminCsp(issuer) }
    );
  });

  afterAll(async () => {
    await new Promise((r) => bclServer?.close(r));
    await redis?.quit().catch(() => undefined);
    await container?.stop();
  });

  /** Keycloak's login form, driven over HTTP. Returns the callback URL Keycloak redirects to. */
  async function submitLogin(kc: Browser, authUrl: string): Promise<string> {
    const page = await kc.go(authUrl);
    const action = /action="([^"]+)"/.exec(page.body)?.[1]?.replaceAll('&amp;', '&');
    expect(action, 'Keycloak login form').toBeTruthy();
    const posted = await kc.go(
      action!,
      { method: 'POST', body: new URLSearchParams({ username: 'admin', password: 'admin_password', credentialId: '' }), headers: { 'content-type': 'application/x-www-form-urlencoded' } },
      (l) => l.startsWith(appUrl)
    );
    let landed = posted;
    if (!landed.location) {
      // The password was accepted and Keycloak is asking for the second factor.
      const otpAction = /action="([^"]+)"/.exec(posted.body)?.[1]?.replaceAll('&amp;', '&');
      expect(otpAction, 'one-time code form').toBeTruthy();
      landed = await kc.go(
        otpAction!,
        { method: 'POST', body: new URLSearchParams({ otp: totp(TOTP_SECRET) }), headers: { 'content-type': 'application/x-www-form-urlencoded' } },
        (l) => l.startsWith(appUrl)
      );
    }
    expect(landed.location, 'redirect back to the app').toBeTruthy();
    return landed.location!;
  }

  async function signIn(kc = new Browser(), next = '/finance/payouts') {
    const jar: Record<string, string> = {};
    const start = await bff.handlers.start(appReq(`/api/auth/start?next=${encodeURIComponent(next)}`, { jar }));
    expect(start.status).toBe(303);
    jarFrom(start, jar);
    const authUrl = start.headers.get('location')!;
    const cb = await bff.handlers.callback(appReq(await submitLogin(kc, authUrl), { jar }));
    jarFrom(cb, jar);
    return { jar, kc, authUrl, cb };
  }

  // State shared by the ordered steps of one staff member's session.
  const s: { jar: Record<string, string>; kc: Browser; sid: string; authTime: number } = { jar: {}, kc: new Browser(), sid: '', authTime: 0 };

  it('login and callback: PKCE, Strict cookie, roles and audience from the real tokens, return path honoured', async () => {
    const { jar, kc, authUrl, cb } = await signIn();
    Object.assign(s, { jar, kc });
    const u = new URL(authUrl);
    expect(u.searchParams.get('code_challenge_method')).toBe('S256');
    expect(u.searchParams.get('redirect_uri')).toBe(`${appUrl}/api/auth/callback`);
    expect(new URL(cb.headers.get('location')!).pathname).toBe('/finance/payouts');
    const setCookie = cb.headers.getSetCookie().find((c) => c.startsWith(`${cookieName}=`))!;
    expect(setCookie).toMatch(/HttpOnly/i);
    expect(setCookie).toMatch(/SameSite=Strict/);
    const rec = (await (await bff.internals()).sessions.load(jar[cookieName]))!.record;
    expect(rec.roles).toEqual(expect.arrayContaining(['ADMIN', 'SUPER_ADMIN']));
    expect(rec.aud).toContain(GATEWAY_AUD);
    expect(rec.kcSid).toBeTruthy();
    s.sid = rec.kcSid!;
    s.authTime = rec.authTime!;
  });

  it('session: the public view carries no token, requireSession admits staff and refuses an anonymous visitor', async () => {
    const body = await (await bff.handlers.session(appReq('/api/auth/session', { jar: s.jar }))).text();
    expect(body).not.toMatch(/eyJ/);
    expect(JSON.parse(body)).toMatchObject({ authenticated: true, roles: expect.arrayContaining(['ADMIN']) });
    asRequest({ [cookieName]: s.jar[cookieName] });
    const pub = await bff.requireSession({ roles: STAFF_ACCESS_ROLES });
    expect(pub.roles).toContain('SUPER_ADMIN');
    expect(JSON.stringify(pub)).not.toMatch(/eyJ/);
    await expect(bff.requireSession({ roles: ['FINANCE_LEAD'] })).rejects.toThrow('NEXT_REDIRECT:/unauthorized');
    asRequest({});
    await expect(bff.requireSession({ roles: STAFF_ACCESS_ROLES })).rejects.toThrow('NEXT_REDIRECT:/login?next=%2Ffinance%2Fpayouts');
    // the proxy gates every page and sets the CSP + no-store on guarded responses
    const open = await bff.proxy(new NextRequest(`${appUrl}/finance`, { headers: { cookie: `${cookieName}=${s.jar[cookieName]}` } }));
    expect(open.status).toBe(200);
    expect(open.headers.get('content-security-policy')).toContain(`form-action 'self' ${new URL(issuer).origin}`);
    expect(open.headers.get('cache-control')).toBe('no-store');
    const closed = await bff.proxy(new NextRequest(`${appUrl}/finance`));
    expect(closed.status).toBe(303);
    expect(new URL(closed.headers.get('location')!).pathname).toBe('/login');
    const login = await bff.proxy(new NextRequest(`${appUrl}/login`));
    expect(login.status).toBe(200);
  });

  it('step-up: a stale sign-in is refused for sensitive actions; /api/auth/stepup re-authenticates at Keycloak and renews auth_time', async () => {
    asRequest({ [cookieName]: s.jar[cookieName] });
    await expect(bff.requireSession({ roles: STAFF_ACCESS_ROLES, freshAuthSec: STEP_UP_SEC, kind: 'action' })).resolves.toBeTruthy();

    offsetMs = (STEP_UP_SEC + 1) * 1000;
    const stale = await bff
      .requireSession({ roles: STAFF_ACCESS_ROLES, freshAuthSec: STEP_UP_SEC, kind: 'action', returnTo: '/finance/payouts' })
      .catch((e) => e);
    expect(stale).toBeInstanceOf(BffAuthError);
    expect(stale.code).toBe('STEP_UP_REQUIRED');
    expect(stale.stepUpUrl).toBe(`/api/auth/stepup?next=${encodeURIComponent('/finance/payouts')}&maxAge=${STEP_UP_SEC}`);
    offsetMs = 0;

    await new Promise((r) => setTimeout(r, 1500)); // a new auth_time is visibly newer
    const step = await bff.handlers.stepup(appReq(stale.stepUpUrl, { jar: s.jar }));
    expect(step.status).toBe(303);
    jarFrom(step, s.jar);
    const authUrl = new URL(step.headers.get('location')!);
    expect(authUrl.searchParams.get('prompt')).toBe('login');
    expect(authUrl.searchParams.get('max_age')).toBe(String(STEP_UP_SEC));
    const loginForm = await s.kc.go(authUrl.toString());
    expect(loginForm.body, 'Keycloak asks for the password again').toContain('kc-form-login');
    const cb = await bff.handlers.callback(appReq(await submitLogin(s.kc, authUrl.toString()), { jar: s.jar }));
    expect(new URL(cb.headers.get('location')!).pathname).toBe('/finance/payouts');
    jarFrom(cb, s.jar);
    const rec = (await (await bff.internals()).sessions.load(s.jar[cookieName]))!.record;
    expect(rec.authTime!).toBeGreaterThan(s.authTime);
    expect(rec.kcSid).toBeTruthy();
    s.authTime = rec.authTime!;
    s.sid = rec.kcSid!;
    asRequest({ [cookieName]: s.jar[cookieName] });
    offsetMs = 60_000;
    await expect(bff.requireSession({ roles: STAFF_ACCESS_ROLES, freshAuthSec: STEP_UP_SEC, kind: 'action' })).resolves.toBeTruthy();
    offsetMs = 0;
  });

  it('refresh: 30 parallel callers produce exactly one Keycloak refresh and a rotated token pair', async () => {
    const d = await bff.internals();
    const cookie = s.jar[cookieName];
    const before = (await d.sessions.load(cookie))!.record;
    kcRefreshCalls = 0;
    try {
      offsetMs = 290_000;
      const results = await Promise.all(Array.from({ length: 30 }, () => d.tokens.getAccessToken(cookie)));
      expect(results.every((r) => r.ok)).toBe(true);
    } finally {
      offsetMs = 0;
    }
    expect(kcRefreshCalls).toBe(1);
    const after = (await d.sessions.load(cookie))!.record;
    expect(after.rtVer).toBe(before.rtVer + 1);
    expect(after.refreshToken).not.toBe(before.refreshToken);
    expect(after.kcSid).toBe(s.sid);
    expect(after.roles).toEqual(expect.arrayContaining(['ADMIN']));
  });

  it('back-channel logout: Keycloak ends the SSO session elsewhere and the BFF drops exactly that session and revokes the sid', async () => {
    const other = await signIn(new Browser());
    const d = await bff.internals();
    identity.calls.length = 0;
    const rec = (await d.sessions.load(s.jar[cookieName]))!.record;
    const endUrl = new URL(`${issuer}/protocol/openid-connect/logout`);
    endUrl.searchParams.set('id_token_hint', rec.idToken!);
    endUrl.searchParams.set('post_logout_redirect_uri', `${appUrl}/`);
    await s.kc.go(endUrl.toString(), {}, (l) => l.startsWith(appUrl));
    const deadline = Date.now() + 20_000;
    while (Date.now() < deadline && (await d.sessions.load(s.jar[cookieName]))) await new Promise((r) => setTimeout(r, 200));
    expect(await d.sessions.load(s.jar[cookieName])).toBeNull();
    expect(await d.sessions.load(other.jar[cookieName])).not.toBeNull();
    expect(identity.calls.some((c) => c.body.sid === s.sid && c.body.reason === 'backchannel_logout')).toBe(true);
    expect(await redis.exists(`bff:admin:tomb:sid:${s.sid}`)).toBe(1);
    // the dead session is refused everywhere
    asRequest({ [cookieName]: s.jar[cookieName] });
    await expect(bff.requireSession({ kind: 'action' })).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
    Object.assign(s, { jar: other.jar, kc: other.kc });
  });

  it('logout: POST ends the session, revokes it, and ends the Keycloak SSO session from the server', async () => {
    const d = await bff.internals();
    const rec = (await d.sessions.load(s.jar[cookieName]))!.record;
    identity.calls.length = 0;
    const refused = await bff.handlers.logout(appReq('/api/auth/logout', { method: 'POST', jar: s.jar, headers: { origin: 'https://evil.example', 'sec-fetch-site': 'cross-site' } }));
    expect(refused.status).toBe(403);
    expect(await d.sessions.load(s.jar[cookieName])).not.toBeNull();

    const res = await bff.handlers.logout(appReq('/api/auth/logout', { method: 'POST', jar: s.jar, headers: { origin: appUrl, 'sec-fetch-site': 'same-origin' } }));
    expect(res.status).toBe(303);
    // The server already ended the Keycloak session, so the browser goes straight home with no hop through Keycloak.
    expect(res.headers.get('location')).toBe(`${appUrl}/`);
    expect(await d.sessions.load(s.jar[cookieName])).toBeNull();
    await expect(d.oidc.refresh(rec.refreshToken!)).rejects.toMatchObject({ kind: 'invalid_grant' });
    expect(identity.calls[0]).toMatchObject({ path: '/api/internal/revocations/logout', body: { sid: rec.kcSid, reason: 'user_logout' } });
    const again = await s.kc.go((await bff.handlers.start(appReq('/api/auth/start'))).headers.get('location')!);
    expect(again.body, 'the Keycloak SSO session is gone').toContain('kc-form-login');
  });

  it('no token, code or secret appears in the logs of the whole run', () => {
    const all = logs.join('\n');
    expect(all.length).toBeGreaterThan(100);
    expect(all).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
    expect(all).not.toContain(SECRET);
  });
});
