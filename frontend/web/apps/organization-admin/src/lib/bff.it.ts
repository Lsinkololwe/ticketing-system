import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import path from 'node:path';
import IORedis from 'ioredis';
import { NextRequest } from 'next/server';
import { afterAll, afterEach, beforeAll, describe, expect, inject, it } from 'vitest';
import { createBff } from '@pml.tickets/shared/auth/bff';
import { composeFetch } from '@pml.tickets/shared/auth/bff/testing';
import { RedisStore } from '../../../../libs/shared/src/auth/bff/store';
import { identityStub, jarFrom } from '../../../../libs/shared/src/auth/bff/__tests__/harness';
import { CSP_OPTIONS, organizerBffConfig } from './bff.config';

/**
 * Organizer sign-in against REAL Keycloak 26.5.2 + Redis, using the organizer app's own config
 * (`organizerBffConfig`) and the real `docker-resources/keycloak/myticketzm-realm.json` client
 * (contact-otp flow and theme removed so a stock Keycloak can import it).
 *
 * Opt in: BFF_IT_KEYCLOAK=1 (Docker, image quay.io/keycloak/keycloak:26.5.2).
 */
const enabled = process.env.BFF_IT_KEYCLOAK === '1';
const REALM_FILE = path.resolve(import.meta.dirname, '../../../../../../../docker-resources/keycloak/myticketzm-realm.json');
const SECRET = 'it-organizer-secret';
const GATEWAY_AUD = 'myticketzm-api-gateway';
const CLIENT = 'myticketzm-organizer';
const REALM = 'myticketzm';

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

describe.skipIf(!enabled)('organizer BFF: real Keycloak 26.5.2 + Redis', () => {
  let container: import('testcontainers').StartedTestContainer;
  let issuer = '';
  let appUrl = '';
  let bclServer: Server;
  let redis: IORedis;
  let bff: ReturnType<typeof createBff>;
  const identity = identityStub();
  let offsetMs = 0;
  let refreshCalls = 0;
  const countingFetch: typeof fetch = async (input, init) => {
    if (String(input).endsWith('/protocol/openid-connect/token') && String(init?.body).includes('grant_type=refresh_token')) refreshCalls++;
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
        .replaceAll('${TICKETING_REALM_NAME}', REALM)
        .replaceAll('${TICKETING_ORGANIZER_CLIENT_ID}', CLIENT)
        .replaceAll('${ORGANIZER_PORTAL_CLIENT_SECRET}', SECRET)
        .replaceAll('${TICKETING_ORGANIZER_APP_URL}', appUrl)
        .replaceAll('${TICKETING_API_GATEWAY_CLIENT_ID}', GATEWAY_AUD)
        .replaceAll('${TICKETING_BUYER_APP_URL}', 'http://localhost:1')
        .replaceAll(/\$\{[A-Z_]+\}/g, 'x')
    );
    delete realm.loginTheme;
    delete realm.authenticationFlows;
    delete realm.authenticatorConfig;
    delete realm.browserFlow;
    realm.sslRequired = 'none';
    const org = realm.clients.find((c: { clientId: string }) => c.clientId === CLIENT);
    delete org.attributes.login_theme;
    // The shipped export registers `<app>/` and `<app>/logged-out`; the BFF also returns to `/login?error=` after a refused sign-in.
    org.attributes['post.logout.redirect.uris'] = `${appUrl}/*`;
    org.attributes['backchannel.logout.url'] = `http://host.docker.internal:${appPort}/api/auth/backchannel-logout`;
    realm.clients = realm.clients.filter((c: { clientId: string }) => c.clientId === CLIENT);
    // A second app signed in through the same browser, as the buyer app is beside the organizer app in production:
    // it is what keeps the Keycloak session alive when the organizer app signs out.
    realm.clients.push({
      clientId: 'second-app', enabled: true, protocol: 'openid-connect', publicClient: true, standardFlowEnabled: true,
      directAccessGrantsEnabled: false, implicitFlowEnabled: false, redirectUris: [`${appUrl}/second/callback`],
      attributes: { 'pkce.code.challenge.method': 'S256' },
    });
    realm.users = [
      { username: 'organizer', enabled: true, emailVerified: true, firstName: 'Olive', lastName: 'Organizer', email: 'organizer@example.test', realmRoles: ['ORGANIZER'], credentials: [{ type: 'password', value: 'pw-organizer', temporary: false }] },
      { username: 'buyer', enabled: true, emailVerified: true, firstName: 'Bea', lastName: 'Buyer', email: 'buyer@example.test', realmRoles: ['CUSTOMER'], credentials: [{ type: 'password', value: 'pw-buyer', temporary: false }] },
    ];

    container = await new GenericContainer('quay.io/keycloak/keycloak:26.5.2')
      .withExposedPorts(8080)
      .withEnvironment({ KC_BOOTSTRAP_ADMIN_USERNAME: 'admin', KC_BOOTSTRAP_ADMIN_PASSWORD: 'admin', KC_HOSTNAME_STRICT: 'false', KC_HEALTH_ENABLED: 'false' })
      .withCommand(['start-dev', '--import-realm'])
      .withExtraHosts([{ host: 'host.docker.internal', ipAddress: 'host-gateway' }])
      .withCopyContentToContainer([{ content: JSON.stringify(realm), target: '/opt/keycloak/data/import/realm.json' }])
      .withWaitStrategy(Wait.forHttp(`/realms/${REALM}/.well-known/openid-configuration`, 8080).forStatusCode(200).withStartupTimeout(200_000))
      .start();
    issuer = `http://${container.getHost()}:${container.getMappedPort(8080)}/realms/${REALM}`;

    redis = new IORedis(inject('redisUrl'));
    const base = organizerBffConfig({
      APP_URL: appUrl,
      KEYCLOAK_ISSUER: issuer,
      KEYCLOAK_CLIENT_ID: CLIENT,
      KEYCLOAK_CLIENT_SECRET: SECRET,
      API_AUDIENCE: GATEWAY_AUD,
      ACCOUNT_CLAIM: '', // the stock realm users carry no accountId attribute
      BFF_ENC_KEYS: `k1:${Buffer.alloc(32, 5).toString('base64')}`,
      GRAPHQL_URL: 'http://upstream.test/graphql',
      TRUST_PROXY_HOPS: '1',
    });
    bff = createBff(
      {
        ...base,
        production: false,
        redis: { store: new RedisStore(redis) },
        clock: () => Date.now() + offsetMs,
        fetch: composeFetch(identity.fetch, countingFetch),
        identity: { baseUrl: 'http://identity.test', tokenUrl: 'http://identity.test/token', clientId: 'svc', clientSecret: 'x' },
        refresh: { pollMs: 20, waitMs: 8000, lockMs: 8000 },
      },
      { csp: CSP_OPTIONS }
    );
  });

  afterEach(() => {
    offsetMs = 0; // Keycloak signs real exp claims
  });

  afterAll(async () => {
    await new Promise((r) => bclServer?.close(r));
    await redis?.quit().catch(() => undefined);
    await container?.stop();
  });

  async function signIn(username: string, password: string, kcBrowser = new Browser(), next = '/dashboard') {
    const jar: Record<string, string> = {};
    const start = await bff.handlers.start(appReq(`/api/auth/start?next=${next}`, { jar, headers: { 'x-forwarded-for': '198.51.100.9' } }));
    expect(start.status).toBe(303);
    jarFrom(start, jar);
    const page = await kcBrowser.go(start.headers.get('location')!);
    const action = /action="([^"]+)"/.exec(page.body)?.[1]?.replaceAll('&amp;', '&');
    expect(action, 'login form').toBeTruthy();
    const posted = await kcBrowser.go(action!, { method: 'POST', body: new URLSearchParams({ username, password, credentialId: '' }), headers: { 'content-type': 'application/x-www-form-urlencoded' } }, (l) => l.startsWith(appUrl));
    expect(posted.location, 'redirect to the app').toBeTruthy();
    const cb = await bff.handlers.callback(appReq(posted.location!, { jar, headers: { 'x-forwarded-for': '198.51.100.9' } }));
    jarFrom(cb, jar);
    return { jar, kcBrowser, cb };
  }

  it('login -> callback -> session -> refresh -> logout', async () => {
    // login + callback
    const { jar, kcBrowser, cb } = await signIn('organizer', 'pw-organizer');
    expect(new URL(cb.headers.get('location')!).pathname).toBe('/dashboard');
    const cookieName = Object.keys(jar).find((k) => k === 'pml_org' || k === '__Host-pml_org')!;
    expect(cookieName).toBeTruthy();
    const setCookie = new Headers(cb.headers).getSetCookie().find((c) => c.startsWith(`${cookieName}=`))!;
    expect(setCookie).toMatch(/HttpOnly/);
    expect(setCookie).toMatch(/SameSite=Lax/);

    // session: roles from the real access token, account = Keycloak sub, tokens never exposed
    const d = await bff.internals();
    const s = (await d.sessions.load(jar[cookieName]))!;
    expect(s.record.roles).toContain('ORGANIZER');
    expect(s.record.aud).toContain(GATEWAY_AUD);
    expect(s.record.accountId).toBe(s.record.sub);
    expect(s.record.displayName).toBeTruthy();
    const pub = await (await bff.handlers.session(appReq('/api/auth/session', { jar }))).text();
    expect(JSON.parse(pub)).toMatchObject({ authenticated: true, accountId: s.record.sub, roles: expect.arrayContaining(['ORGANIZER']) });
    expect(pub).not.toMatch(/eyJ/);

    // coarse proxy gate: anonymous is sent to /login, the organizer passes
    const anon = await bff.proxy(new NextRequest(`${appUrl}/finance`));
    expect(anon.status).toBe(303);
    expect(new URL(anon.headers.get('location')!).pathname).toBe('/login');
    const ok = await bff.proxy(new NextRequest(`${appUrl}/finance`, { headers: { cookie: `${cookieName}=${jar[cookieName]}` } }));
    expect(ok.status).toBe(200);
    expect(ok.headers.get('content-security-policy')).toContain("'strict-dynamic'");

    // refresh: 20 parallel callers after expiry -> exactly one token-endpoint call, new rotated tokens
    const before = s.record;
    offsetMs += 290_000;
    refreshCalls = 0;
    const results = await Promise.all(Array.from({ length: 20 }, () => d.tokens.getAccessToken(jar[cookieName])));
    expect(results.every((r) => r.ok)).toBe(true);
    expect(refreshCalls).toBe(1);
    const after = (await d.sessions.load(jar[cookieName]))!.record;
    expect(after.rtVer).toBe(1);
    expect(after.refreshToken).not.toBe(before.refreshToken);
    expect(after.kcSid).toBe(before.kcSid);

    // logout: local session gone, the Keycloak SSO session ended from the server, refresh token dead, identity revocation requested
    identity.calls.length = 0;
    const out = await bff.handlers.logout(appReq('/api/auth/logout', { method: 'POST', jar, headers: { origin: appUrl, 'sec-fetch-site': 'same-origin' } }));
    expect(out.status).toBe(303);
    expect(out.headers.get('location')).toBe(`${appUrl}/`);
    expect(await d.sessions.load(jar[cookieName])).toBeNull();
    // Keycloak no longer signs this browser in silently: that is what let a revoked session id come back.
    const next = await kcBrowser.go((await bff.handlers.start(appReq('/api/auth/start'))).headers.get('location')!);
    expect(next.body).toContain('kc-form-login');
    await expect(d.oidc.refresh(after.refreshToken!)).rejects.toMatchObject({ kind: 'invalid_grant' });
    expect(identity.calls[0]).toMatchObject({ path: '/api/internal/revocations/logout', body: { sid: before.kcSid, reason: 'user_logout' } });
    const gone = await bff.proxy(new NextRequest(`${appUrl}/finance`, { headers: { cookie: `${cookieName}=${jar[cookieName]}` } }));
    expect(gone.status).toBe(303);
  });

  it('a Keycloak user without an organizer role signs in as an applicant: a session, but no organizer role', async () => {
    // Anyone signed in may apply to become an organizer; the role is granted on approval and enforced per operation.
    const { jar, cb } = await signIn('buyer', 'pw-buyer');
    expect(new URL(cb.headers.get('location')!, appUrl).pathname).toBe('/dashboard');
    const cookieName = Object.keys(jar).find((k) => k === 'pml_org' || k === '__Host-pml_org')!;
    const s = (await (await bff.internals()).sessions.load(jar[cookieName]))!;
    expect(s.record.roles).not.toContain('ORGANIZER');
  });

  it('signing out and straight back in works: the second sign-in is a new Keycloak session, not the revoked one', async () => {
    const first = await signIn('organizer', 'pw-organizer');
    const d = await bff.internals();
    const name = Object.keys(first.jar).find((k) => k === 'pml_org' || k === '__Host-pml_org')!;
    const sid1 = (await d.sessions.load(first.jar[name]))!.record.kcSid;
    // The same browser also signs in to a second app, so the Keycloak session outlives the organizer app's own client session.
    const challenge = createHash('sha256').update('v'.repeat(43)).digest('base64url');
    const authorize = new URL(`${issuer}/protocol/openid-connect/auth`);
    authorize.search = new URLSearchParams({
      client_id: 'second-app', redirect_uri: `${appUrl}/second/callback`, response_type: 'code', scope: 'openid',
      state: 's', code_challenge: challenge, code_challenge_method: 'S256',
    }).toString();
    const viaSso = await first.kcBrowser.go(authorize.toString(), {}, (l) => l.startsWith(appUrl));
    expect(viaSso.location, 'the second app is signed in silently through the same Keycloak session').toContain('/second/callback');
    await bff.handlers.logout(appReq('/api/auth/logout', { method: 'POST', jar: first.jar, headers: { origin: appUrl, 'sec-fetch-site': 'same-origin' } }));
    // Same browser, same Keycloak cookies: with the SSO session still alive this would come back with sid1, which identity just revoked.
    const second = await signIn('organizer', 'pw-organizer', first.kcBrowser);
    const name2 = Object.keys(second.jar).find((k) => k === 'pml_org' || k === '__Host-pml_org')!;
    expect((await d.sessions.load(second.jar[name2]))!.record.kcSid).not.toBe(sid1);
  });

  it('Keycloak back-channel logout deletes exactly that session by sid', async () => {
    const a = await signIn('organizer', 'pw-organizer');
    const b = await signIn('organizer', 'pw-organizer');
    const d = await bff.internals();
    const ca = a.jar.pml_org;
    const cbk = b.jar.pml_org;
    const rec = (await d.sessions.load(ca))!.record;
    const endUrl = new URL(`${issuer}/protocol/openid-connect/logout`);
    endUrl.searchParams.set('id_token_hint', rec.idToken!);
    endUrl.searchParams.set('post_logout_redirect_uri', `${appUrl}/`);
    await a.kcBrowser.go(endUrl.toString(), {}, (l) => l.startsWith(appUrl));
    const deadline = Date.now() + 15_000;
    while (Date.now() < deadline && (await d.sessions.load(ca))) await new Promise((r) => setTimeout(r, 200));
    expect(await d.sessions.load(ca)).toBeNull();
    expect(await d.sessions.load(cbk)).not.toBeNull();
  });
});
