import { createHash, randomUUID } from 'node:crypto';
import { SignJWT, createLocalJWKSet, exportJWK, generateKeyPair, type JWK, type JWTVerifyGetKey, type CryptoKey } from 'jose';

/** Test doubles for the BFF: a controllable clock and a Keycloak that signs real JWTs. Not for production. */
export class FakeClock {
  constructor(public t = Date.UTC(2026, 9, 4, 12, 0, 0)) {}
  now = () => this.t;
  advance(ms: number) {
    this.t += ms;
  }
  advanceSec(s: number) {
    this.t += s * 1000;
  }
}

export interface FakeUser {
  sub: string;
  accountId?: string;
  roles?: string[];
  name?: string;
}

interface CodeEntry {
  user: FakeUser;
  sid: string;
  nonce: string;
  challenge: string;
  authTime: number;
}
interface RtEntry {
  user: FakeUser;
  sid: string;
  used: boolean;
}

export interface FakeKeycloakOptions {
  issuer: string;
  clientId: string;
  clientSecret: string;
  audience?: string;
  accessTtlSec?: number;
  clock?: FakeClock;
}

export type FetchFn = typeof fetch;

export async function createFakeKeycloak(opts: FakeKeycloakOptions) {
  const clock = opts.clock ?? new FakeClock();
  const { publicKey, privateKey } = await generateKeyPair('RS256');
  const jwk: JWK = { ...(await exportJWK(publicKey)), kid: 'fake-1', alg: 'RS256', use: 'sig' };
  const jwks: JWTVerifyGetKey = createLocalJWKSet({ keys: [jwk] });
  const accessTtl = opts.accessTtlSec ?? 300;

  const codes = new Map<string, CodeEntry>();
  const refreshTokens = new Map<string, RtEntry>();
  const deadSids = new Set<string>();
  const stats = { tokenCalls: 0, refreshCalls: 0, revokeCalls: 0, reuseDetected: 0, codeCalls: 0, endSessionCalls: 0 };
  const endedSessions: string[] = [];
  const faults: { refresh?: Array<'5xx' | 'timeout' | 'invalid_grant' | null>; afterRefresh?: () => void; endSession?: boolean } = {};

  async function sign(claims: Record<string, unknown>, key: CryptoKey = privateKey, kid = 'fake-1'): Promise<string> {
    return new SignJWT(claims).setProtectedHeader({ alg: 'RS256', kid }).sign(key);
  }

  async function accessToken(user: FakeUser, sid: string, extra: Record<string, unknown> = {}) {
    const iat = Math.floor(clock.now() / 1000);
    return sign({
      iss: opts.issuer, sub: user.sub, aud: [opts.audience ?? 'myticketzm-api', 'account'], azp: opts.clientId, sid,
      jti: randomUUID(), iat, exp: iat + accessTtl,
      realm_access: { roles: user.roles ?? [] },
      ...(user.accountId ? { accountId: user.accountId } : {}),
      ...extra,
    });
  }
  async function idToken(user: FakeUser, sid: string, nonce: string | undefined, authTime: number, extra: Record<string, unknown> = {}) {
    const iat = Math.floor(clock.now() / 1000);
    return sign({
      iss: opts.issuer, sub: user.sub, aud: opts.clientId, azp: opts.clientId, sid, iat, exp: iat + accessTtl, auth_time: authTime,
      ...(nonce ? { nonce } : {}), name: user.name ?? 'Test User',
      ...(user.accountId ? { accountId: user.accountId } : {}),
      ...extra,
    });
  }
  async function bundle(user: FakeUser, sid: string, nonce: string | undefined, authTime: number, rt?: string) {
    const refresh = rt ?? `rt-${randomUUID()}`;
    refreshTokens.set(refresh, { user, sid, used: false });
    return {
      access_token: await accessToken(user, sid),
      refresh_token: refresh,
      id_token: await idToken(user, sid, nonce, authTime),
      expires_in: accessTtl,
      token_type: 'Bearer',
    };
  }

  const j = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

  const handler: FetchFn = async (input, init) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.toString() : (input as Request).url);
    const base = new URL(opts.issuer);
    if (url.origin !== base.origin) return j({ error: 'not_keycloak' }, 404);
    const path = url.pathname.slice(base.pathname.length);
    if (path === '/.well-known/openid-configuration') {
      return j({
        issuer: opts.issuer,
        authorization_endpoint: `${opts.issuer}/protocol/openid-connect/auth`,
        token_endpoint: `${opts.issuer}/protocol/openid-connect/token`,
        jwks_uri: `${opts.issuer}/protocol/openid-connect/certs`,
        end_session_endpoint: `${opts.issuer}/protocol/openid-connect/logout`,
        revocation_endpoint: `${opts.issuer}/protocol/openid-connect/revoke`,
      });
    }
    if (path === '/protocol/openid-connect/certs') return j({ keys: [jwk] });
    const body = new URLSearchParams(String(init?.body ?? ''));
    if (path === '/protocol/openid-connect/logout' && init?.method === 'POST') {
      stats.endSessionCalls++;
      if (faults.endSession) return new Response(null, { status: 503 });
      endedSessions.push(body.get('id_token_hint') ?? '');
      return new Response(null, { status: 204 });
    }
    if (path === '/protocol/openid-connect/revoke') {
      stats.revokeCalls++;
      refreshTokens.delete(body.get('token') ?? '');
      return new Response(null, { status: 200 });
    }
    if (path === '/protocol/openid-connect/token') {
      stats.tokenCalls++;
      if (body.get('client_id') !== opts.clientId || body.get('client_secret') !== opts.clientSecret) return j({ error: 'unauthorized_client' }, 401);
      const grant = body.get('grant_type');
      if (grant === 'authorization_code') {
        stats.codeCalls++;
        const entry = codes.get(body.get('code') ?? '');
        codes.delete(body.get('code') ?? '');
        if (!entry) return j({ error: 'invalid_grant' }, 400);
        const challenge = createHash('sha256').update(body.get('code_verifier') ?? '').digest('base64url');
        if (challenge !== entry.challenge) return j({ error: 'invalid_grant', error_description: 'PKCE' }, 400);
        return j(await bundle(entry.user, entry.sid, entry.nonce, entry.authTime));
      }
      if (grant === 'refresh_token') {
        stats.refreshCalls++;
        const injected = faults.refresh?.shift();
        if (injected === '5xx') return j({ error: 'server_error' }, 503);
        if (injected === 'timeout') throw Object.assign(new Error('timeout'), { name: 'TimeoutError' });
        if (injected === 'invalid_grant') return j({ error: 'invalid_grant' }, 400);
        const rt = body.get('refresh_token') ?? '';
        const entry = refreshTokens.get(rt);
        if (!entry || deadSids.has(entry.sid)) return j({ error: 'invalid_grant' }, 400);
        if (entry.used) {
          // refreshTokenMaxReuse=0: reuse detection kills the whole SSO session.
          stats.reuseDetected++;
          deadSids.add(entry.sid);
          return j({ error: 'invalid_grant', error_description: 'Maximum allowed refresh token reuse exceeded' }, 400);
        }
        entry.used = true;
        const out = await bundle(entry.user, entry.sid, undefined, Math.floor(clock.now() / 1000));
        faults.afterRefresh?.();
        return j(out);
      }
      return j({ error: 'unsupported_grant_type' }, 400);
    }
    return j({ error: 'not_found' }, 404);
  };

  return {
    clock,
    fetch: handler,
    jwks,
    jwk,
    stats,
    faults,
    deadSids,
    /** The ID tokens presented to the server-side end-session call, in order. */
    endedSessions,
    sign,
    /** Simulates a completed browser login; returns the `code` Keycloak would redirect back with. */
    issueCode(authorizeUrl: string, user: FakeUser, opts2: { sid?: string; authTime?: number } = {}) {
      const q = new URL(authorizeUrl).searchParams;
      const code = `code-${randomUUID()}`;
      codes.set(code, {
        user,
        sid: opts2.sid ?? `sid-${randomUUID()}`,
        nonce: q.get('nonce') ?? '',
        challenge: q.get('code_challenge') ?? '',
        authTime: opts2.authTime ?? Math.floor(clock.now() / 1000),
      });
      return { code, state: q.get('state') ?? '' };
    },
    /** A Keycloak back-channel logout token. `overrides` may break it on purpose. */
    async logoutToken(claims: { sid?: string; sub?: string } & Record<string, unknown>, o: { key?: CryptoKey; aud?: string } = {}) {
      const iat = Math.floor(clock.now() / 1000);
      return sign(
        {
          iss: opts.issuer, aud: o.aud ?? opts.clientId, iat, jti: randomUUID(),
          events: { 'http://schemas.openid.net/event/backchannel-logout': {} },
          ...claims,
        },
        o.key ?? privateKey
      );
    },
    async foreignKey() {
      return (await generateKeyPair('RS256')).privateKey;
    },
  };
}

export type FakeKeycloak = Awaited<ReturnType<typeof createFakeKeycloak>>;

/** Routes requests to the first handler that does not answer 404 `not_keycloak`; later ones can be stubs. */
export function composeFetch(...handlers: FetchFn[]): FetchFn {
  return async (input, init) => {
    let last: Response | null = null;
    for (const h of handlers) {
      const r = await h(input, init);
      if (!(r.status === 404 && r.headers.get('x-fallthrough') === '1') && !(r.status === 404 && (await r.clone().text()).includes('not_keycloak'))) return r;
      last = r;
    }
    return last ?? new Response(null, { status: 404 });
  };
}

export const TEST_ENC_KEYS = `k1:${Buffer.alloc(32, 7).toString('base64')}`;
