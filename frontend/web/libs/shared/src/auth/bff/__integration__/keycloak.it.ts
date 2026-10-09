import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import path from 'node:path';
import IORedis from 'ioredis';
import { afterAll, afterEach, beforeAll, describe, expect, inject, it } from 'vitest';
import { createBff } from '../index';
import { createLogger } from '../logger';
import { RedisStore } from '../store';
import { composeFetch } from '../testing';
import { identityStub, jarFrom } from '../__tests__/harness';

/**
 * Real Keycloak 26.5.2 (testcontainers) with the platform-admin realm from docker-resources/keycloak
 * (placeholders substituted, theme removed, back-channel URL pointed at this process).
 *
 * Opt in: BFF_IT_KEYCLOAK=1. Needs Docker and the image quay.io/keycloak/keycloak:26.5.2.
 */
const enabled = process.env.BFF_IT_KEYCLOAK === '1';
const REALM_FILE = path.resolve(import.meta.dirname, '../../../../../../../../../docker-resources/keycloak/myticketzm-admin-realm.json');
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

class Browser {
  jar = new Map<string, string>();
  private store(res: Response) {
    for (const c of res.headers.getSetCookie()) {
      const [pair] = c.split(';');
      const i = pair.indexOf('=');
      if (/Max-Age=0|expires=Thu, 01 Jan 1970/i.test(c) && pair.slice(i + 1) === '') this.jar.delete(pair.slice(0, i));
      else this.jar.set(pair.slice(0, i), pair.slice(i + 1));
    }
  }
  get cookieHeader() {
    return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }
  /** Follows redirects by hand until `stopAt` matches the next Location (returned) or a non-redirect arrives. */
  async go(url: string, init: RequestInit = {}, stopAt?: (loc: string) => boolean): Promise<{ res: Response; location?: string; body: string }> {
    let current = url;
    let method = init.method ?? 'GET';
    let body = init.body;
    for (let hop = 0; hop < 15; hop++) {
      const res = await fetch(current, { method, body, redirect: 'manual', headers: { cookie: this.cookieHeader, ...(init.headers as Record<string, string>) } });
      this.store(res);
      const loc = res.headers.get('location');
      if (res.status >= 300 && res.status < 400 && loc) {
        const abs = new URL(loc, current).toString();
        if (stopAt?.(abs)) return { res, location: abs, body: '' };
        current = abs;
        method = 'GET';
        body = undefined;
        continue;
      }
      return { res, body: await res.text() };
    }
    throw new Error('too many redirects');
  }
}

describe.skipIf(!enabled)('real Keycloak 26.5.2 + Redis', () => {
  let container: import('testcontainers').StartedTestContainer;
  let issuer = '';
  let appUrl = '';
  let bclServer: Server;
  let redis: IORedis;
  let bff: ReturnType<typeof createBff>;
  const identity = identityStub();
  let offsetMs = 0;
  const kcCalls = { refresh: 0 };
  const logs: string[] = [];

  const countingFetch: typeof fetch = async (input, init) => {
    if (String(input).endsWith('/protocol/openid-connect/token') && String(init?.body).includes('grant_type=refresh_token')) kcCalls.refresh++;
    return fetch(input, init);
  };
  const appReq = (p: string, init: RequestInit & { jar?: Record<string, string> } = {}) => {
    const { jar, ...i } = init;
    const headers = new Headers(i.headers);
    if (jar) headers.set('cookie', Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '));
    return new Request(p.startsWith('http') ? p : appUrl + p, { ...i, headers });
  };

  beforeAll(async () => {
    const { GenericContainer, Wait } = await import('testcontainers');
    // The app-side HTTP server receives Keycloak's back-channel POSTs.
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
    // Tests sign in several times inside one 30-second step, so allow a code to be reused; the one-time-code policy
    // itself is covered against the real realm by the Keycloak extension's RealmConformanceIT.
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
    bff = createBff({
      app: 'admin',
      appUrl,
      production: false,
      oidc: { issuer, clientId: 'myticketzm-admin', clientSecret: SECRET },
      encKeys: `k1:${Buffer.alloc(32, 9).toString('base64')}`,
      redis: { store: new RedisStore(redis) },
      clock: () => Date.now() + offsetMs,
      fetch: composeFetch(identity.fetch, countingFetch),
      logger: createLogger({ app: 'admin', level: 'debug', sink: (l) => logs.push(l) }),
      identity: { baseUrl: 'http://identity.test', tokenUrl: 'http://identity.test/token', clientId: 'svc', clientSecret: 'x' },
      access: { roles: ['ADMIN'], audience: GATEWAY_AUD },
      refresh: { pollMs: 20, waitMs: 8000, lockMs: 8000 },
      trustProxyHops: 1,
    });
  });

  afterEach(() => {
    offsetMs = 0; // each test starts on real time (Keycloak signs real exp claims)
  });

  afterAll(async () => {
    await new Promise((r) => bclServer?.close(r));
    await redis?.quit().catch(() => undefined);
    await container?.stop();
  });

  /** start -> Keycloak login form (fetch) -> callback. Returns the app cookie jar and the browser holding the Keycloak SSO cookies. */
  async function signIn(kcBrowser = new Browser(), next = '/dashboard') {
    const jar: Record<string, string> = {};
    const start = await bff.handlers.start(appReq(`/api/auth/start?next=${next}`, { jar, headers: { 'x-forwarded-for': '198.51.100.4' } }));
    expect(start.status).toBe(303);
    jarFrom(start, jar);
    const authUrl = start.headers.get('location')!;
    const page = await kcBrowser.go(authUrl);
    const action = /action="([^"]+)"/.exec(page.body)?.[1]?.replaceAll('&amp;', '&');
    expect(action, 'login form').toBeTruthy();
    const posted = await kcBrowser.go(action!, { method: 'POST', body: new URLSearchParams({ username: 'admin', password: 'admin_password', credentialId: '' }), headers: { 'content-type': 'application/x-www-form-urlencoded' } }, (l) => l.startsWith(appUrl));
    let landed = posted;
    if (!landed.location) {
      // The password was accepted and Keycloak is asking for the second factor.
      const otpAction = /action="([^"]+)"/.exec(posted.body)?.[1]?.replaceAll('&amp;', '&');
      expect(otpAction, 'one-time code form').toBeTruthy();
      expect(posted.body).toContain('name="otp"');
      landed = await kcBrowser.go(otpAction!, { method: 'POST', body: new URLSearchParams({ otp: totp(TOTP_SECRET) }), headers: { 'content-type': 'application/x-www-form-urlencoded' } }, (l) => l.startsWith(appUrl));
    }
    expect(landed.location, 'redirect to the app').toBeTruthy();
    const cb = await bff.handlers.callback(appReq(landed.location!, { jar, headers: { 'x-forwarded-for': '198.51.100.4' } }));
    jarFrom(cb, jar);
    return { jar, kcBrowser, authUrl, cb };
  }

  it('standard authorization-code flow with PKCE creates a session from real tokens', async () => {
    const { jar, authUrl, cb } = await signIn();
    const u = new URL(authUrl);
    expect(u.searchParams.get('code_challenge_method')).toBe('S256');
    expect(new URL(cb.headers.get('location')!).pathname).toBe('/dashboard');
    const sessCookie = jar.pml_admin;
    expect(sessCookie).toBeTruthy();
    const d = await bff.internals();
    const s = (await d.sessions.load(sessCookie))!;
    expect(s.record.roles).toEqual(expect.arrayContaining(['ADMIN', 'SUPER_ADMIN']));
    expect(s.record.kcSid).toMatch(/^[A-Za-z0-9_-]{8,}$/);
    expect(s.record.aud).toContain(GATEWAY_AUD);
    expect(s.record.authTime).toBeGreaterThan(0);
    expect(s.record.refreshToken).toBeTruthy();
    const pub = await (await bff.handlers.session(appReq('/api/auth/session', { jar }))).text();
    expect(pub).not.toMatch(/eyJ/);
    // SameSite=Strict for the platform admin
    expect(new Headers(cb.headers).getSetCookie().find((c) => c.startsWith('pml_admin='))).toMatch(/SameSite=Strict/);
  });

  it('Keycloak itself enforces PKCE S256 for this client', async () => {
    const jar: Record<string, string> = {};
    const start = await bff.handlers.start(appReq('/api/auth/start', { jar }));
    const u = new URL(start.headers.get('location')!);
    u.searchParams.delete('code_challenge');
    u.searchParams.delete('code_challenge_method');
    const res = await new Browser().go(u.toString(), {}, (l) => l.startsWith(appUrl));
    const loc = res.location ?? '';
    expect(loc.includes('error=invalid_request') || res.res.status >= 400 || /code_challenge/i.test(res.body)).toBe(true);
  });

  it('refresh rotation: parallel refresh from two BFF instances hits Keycloak once; the next expiry rotates again without reuse', async () => {
    const { jar } = await signIn();
    const cookie = jar.pml_admin;
    const d = await bff.internals();
    const before = (await d.sessions.load(cookie))!.record;
    offsetMs += 290_000;
    kcCalls.refresh = 0;
    const results = await Promise.all(Array.from({ length: 30 }, () => d.tokens.getAccessToken(cookie)));
    expect(results.every((r) => r.ok)).toBe(true);
    expect(kcCalls.refresh).toBe(1);
    const after = (await d.sessions.load(cookie))!.record;
    expect(after.rtVer).toBe(1);
    expect(after.refreshToken).not.toBe(before.refreshToken);
    expect(after.accessToken).not.toBe(before.accessToken);
    expect(after.kcSid).toBe(before.kcSid);
    offsetMs += 290_000;
    const next = await d.tokens.getAccessToken(cookie);
    expect(next.ok).toBe(true);
    expect((await d.sessions.load(cookie))!.record.rtVer).toBe(2);
  });

  it('why single-flight matters: presenting a rotated refresh token again (maxReuse=0) kills the whole SSO session', async () => {
    const { jar } = await signIn();
    const cookie = jar.pml_admin;
    const d = await bff.internals();
    const r0 = (await d.sessions.load(cookie))!.record.refreshToken!;
    offsetMs += 290_000;
    expect((await d.tokens.getAccessToken(cookie)).ok).toBe(true); // R0 -> R1
    await expect(d.oidc.refresh(r0)).rejects.toMatchObject({ kind: 'invalid_grant' }); // a second consumer of R0
    offsetMs += 290_000;
    // R1 was the legitimate token, but reuse detection already ended the Keycloak session
    expect(await d.tokens.getAccessToken(cookie)).toEqual({ ok: false, reason: 'SESSION_ENDED' });
    expect(await d.sessions.load(cookie)).toBeNull();
  }, 60_000);

  it('sign-out ends the Keycloak SSO session from the server, revokes the refresh token and the session at identity-service', async () => {
    const { jar, kcBrowser } = await signIn();
    const cookie = jar.pml_admin;
    const d = await bff.internals();
    const rec = (await d.sessions.load(cookie))!.record;
    identity.calls.length = 0;
    const res = await bff.handlers.logout(appReq('/api/auth/logout', { method: 'POST', jar, headers: { origin: appUrl, 'sec-fetch-site': 'same-origin' } }));
    expect(res.status).toBe(303);
    // No hop through Keycloak: the server already ended the session, so the browser goes straight home.
    expect(res.headers.get('location')).toBe(`${appUrl}/`);
    expect(await d.sessions.load(cookie)).toBeNull();
    // the refresh token is dead and the SSO session is gone: Keycloak shows the login form, it does not sign the browser in silently
    await expect(d.oidc.refresh(rec.refreshToken!)).rejects.toMatchObject({ kind: 'invalid_grant' });
    const again = await kcBrowser.go((await bff.handlers.start(appReq('/api/auth/start'))).headers.get('location')!);
    expect(again.body).toContain('kc-form-login');
    expect(identity.calls[0]).toMatchObject({ path: '/api/internal/revocations/logout', body: { sid: rec.kcSid, reason: 'user_logout' } });
  });

  it('signing in again after a sign-out gets a new Keycloak session, not the revoked one', async () => {
    const first = await signIn();
    const d = await bff.internals();
    const sid1 = (await d.sessions.load(first.jar.pml_admin))!.record.kcSid;
    await bff.handlers.logout(appReq('/api/auth/logout', { method: 'POST', jar: first.jar, headers: { origin: appUrl, 'sec-fetch-site': 'same-origin' } }));
    // The same browser (same Keycloak cookies) signs in again.
    const second = await signIn(first.kcBrowser);
    const sid2 = (await d.sessions.load(second.jar.pml_admin))!.record.kcSid;
    expect(sid2).not.toBe(sid1);
  });

  it('Keycloak delivers a back-channel logout that deletes the session by sid', async () => {
    const { jar, kcBrowser } = await signIn();
    const other = await signIn(new Browser());
    const cookie = jar.pml_admin;
    const d = await bff.internals();
    const rec = (await d.sessions.load(cookie))!.record;
    identity.calls.length = 0;
    // The user logs out elsewhere (a different client / the account console): only Keycloak knows.
    const endUrl = new URL(`${issuer}/protocol/openid-connect/logout`);
    endUrl.searchParams.set('id_token_hint', rec.idToken!);
    endUrl.searchParams.set('post_logout_redirect_uri', `${appUrl}/`);
    await kcBrowser.go(endUrl.toString(), {}, (l) => l.startsWith(appUrl));
    const deadline = Date.now() + 15_000;
    while (Date.now() < deadline && (await d.sessions.load(cookie))) await new Promise((r) => setTimeout(r, 200));
    expect(await d.sessions.load(cookie)).toBeNull();
    expect(await d.sessions.load(other.jar.pml_admin)).not.toBeNull(); // a different sid survives
    expect(identity.calls.some((c) => c.body.sid === rec.kcSid && c.body.reason === 'backchannel_logout')).toBe(true);
    expect(await redis.exists(`bff:admin:tomb:sid:${rec.kcSid}`)).toBe(1);
  }, 60_000);

  it('no token, code or cookie value appears in the logs of the whole run', async () => {
    const all = logs.join('\n');
    expect(all.length).toBeGreaterThan(100);
    expect(all).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
    expect(all).not.toContain(SECRET);
  });
});
