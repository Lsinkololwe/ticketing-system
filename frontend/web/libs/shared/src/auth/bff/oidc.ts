import { createHash, randomBytes } from 'node:crypto';
import { createRemoteJWKSet, customFetch, decodeJwt, jwtVerify, type JWTPayload, type JWTVerifyGetKey } from 'jose';
import type { ResolvedConfig } from './config';

/**
 * Minimal OIDC client for one Keycloak confidential client: discovery cache, PKCE S256,
 * authorization-code and refresh grants (client_secret_post), refresh-token revocation,
 * id/access/logout token verification with jose, RP-initiated logout URL.
 */

export interface Discovery {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  jwks_uri: string;
  end_session_endpoint?: string;
  revocation_endpoint?: string;
}

export type OidcErrorKind =
  /** Token is spent/revoked/expired, or the SSO session ended. The session is over. */
  | 'invalid_grant'
  /** Network failure, timeout or 5xx. The session stays; the caller may retry. */
  | 'transient'
  /** Any other 4xx (misconfigured client, ...). */
  | 'rejected';

export class OidcError extends Error {
  constructor(
    public readonly kind: OidcErrorKind,
    message: string,
    public readonly status?: number
  ) {
    super(message);
    this.name = 'OidcError';
  }
}

export interface TokenSet {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  expiresIn: number;
}

const BCL_EVENT = 'http://schemas.openid.net/event/backchannel-logout';

export function generatePkce() {
  const verifier = randomBytes(48).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  return { verifier, challenge };
}

export interface OidcClient {
  discover(): Promise<Discovery>;
  redirectUri: string;
  buildAuthorizationUrl(p: {
    state: string;
    nonce: string;
    challenge: string;
    loginHint?: string;
    prompt?: string;
    maxAge?: number;
    extra?: Record<string, string>;
  }): Promise<string>;
  exchangeCode(code: string, verifier: string): Promise<TokenSet>;
  refresh(refreshToken: string): Promise<TokenSet>;
  revokeRefreshToken(refreshToken: string): Promise<void>;
  verifyIdToken(idToken: string, expect: { nonce: string }): Promise<JWTPayload>;
  /** Signature + issuer verified. Audience is checked separately via `hasAudience`. */
  verifyAccessToken(accessToken: string): Promise<JWTPayload>;
  verifyLogoutToken(token: string): Promise<JWTPayload>;
  endSessionUrl(p: { idTokenHint?: string | null; postLogoutRedirect: string; state?: string }): Promise<string | null>;
  /**
   * Ends the person's whole Keycloak SSO session from the server, using their ID token as the hint. Unlike the
   * browser redirect of {@link endSessionUrl} it does not depend on the browser following anything, so it also
   * works when the app has already lost its own session. Resolves true when Keycloak accepted the request.
   */
  endSessionServerSide(idToken: string): Promise<boolean>;
  resetCache(): void;
}

function swap(url: string, from: string, to: string): string {
  return url.startsWith(from) ? to + url.slice(from.length) : url;
}

export function rolesOf(payload: JWTPayload, clientId: string): string[] {
  const realm = (payload.realm_access as { roles?: unknown } | undefined)?.roles;
  const res = (payload.resource_access as Record<string, { roles?: unknown }> | undefined)?.[clientId]?.roles;
  const list = [...(Array.isArray(realm) ? realm : []), ...(Array.isArray(res) ? res : [])];
  return [...new Set(list.filter((r): r is string => typeof r === 'string'))];
}

export function hasAudience(payload: JWTPayload, audience: string): boolean {
  const aud = payload.aud;
  return Array.isArray(aud) ? aud.includes(audience) : aud === audience;
}

export function createOidcClient(
  cfg: Pick<ResolvedConfig, 'oidc' | 'appUrl' | 'scopes' | 'fetchImpl' | 'clock'>,
  opts: { jwks?: JWTVerifyGetKey } = {}
): OidcClient {
  const { issuer, clientId, clientSecret } = cfg.oidc;
  const pub = issuer.replace(/\/+$/, '');
  const internal = (cfg.oidc.internalIssuer ?? issuer).replace(/\/+$/, '');
  const redirectUri = `${cfg.appUrl}/api/auth/callback`;
  let cache: { value: Discovery; at: number } | null = null;
  let jwksCache: { uri: string; fn: JWTVerifyGetKey } | null = null;

  async function discover(): Promise<Discovery> {
    if (cache && cfg.clock() - cache.at < 10 * 60_000) return cache.value;
    let res: Response;
    try {
      res = await cfg.fetchImpl(`${internal}/.well-known/openid-configuration`, {
        cache: 'no-store',
        signal: AbortSignal.timeout(5000),
      });
    } catch (e) {
      throw new OidcError('transient', `discovery unreachable: ${(e as Error).name}`);
    }
    if (!res.ok) throw new OidcError('transient', `discovery failed: ${res.status}`);
    const raw = (await res.json()) as Discovery;
    const value: Discovery = { ...raw };
    if (internal !== pub) {
      // Browser-facing endpoints keep the public host; server-to-server ones use the internal host.
      value.authorization_endpoint = swap(raw.authorization_endpoint, internal, pub);
      if (raw.end_session_endpoint) value.end_session_endpoint = swap(raw.end_session_endpoint, internal, pub);
      value.token_endpoint = swap(raw.token_endpoint, pub, internal);
      value.jwks_uri = swap(raw.jwks_uri, pub, internal);
      if (raw.revocation_endpoint) value.revocation_endpoint = swap(raw.revocation_endpoint, pub, internal);
    }
    cache = { value, at: cfg.clock() };
    return value;
  }

  async function keys(): Promise<JWTVerifyGetKey> {
    if (opts.jwks) return opts.jwks;
    const d = await discover();
    if (!jwksCache || jwksCache.uri !== d.jwks_uri) {
      jwksCache = { uri: d.jwks_uri, fn: createRemoteJWKSet(new URL(d.jwks_uri), {
          cooldownDuration: 30_000,
          timeoutDuration: 5000,
          [customFetch]: (url, init) => cfg.fetchImpl(url, init),
        }) };
    }
    return jwksCache.fn;
  }

  async function post(url: string, body: URLSearchParams, timeoutMs: number): Promise<Response> {
    body.set('client_id', clientId);
    body.set('client_secret', clientSecret);
    try {
      return await cfg.fetchImpl(url, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json' },
        body,
        cache: 'no-store',
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (e) {
      throw new OidcError('transient', `request failed: ${(e as Error).name}`);
    }
  }

  async function tokenRequest(body: URLSearchParams): Promise<TokenSet> {
    const d = await discover();
    const res = await post(d.token_endpoint, body, 8000);
    if (!res.ok) {
      let code = '';
      try {
        code = String(((await res.json()) as { error?: string }).error ?? '');
      } catch {
        /* non-JSON error body */
      }
      if (res.status >= 500 || res.status === 429) throw new OidcError('transient', `token endpoint ${res.status}`, res.status);
      if (code === 'invalid_grant') throw new OidcError('invalid_grant', 'invalid_grant', res.status);
      throw new OidcError('rejected', `token endpoint ${res.status} ${code}`.trim(), res.status);
    }
    const j = (await res.json()) as { access_token: string; refresh_token?: string; id_token?: string; expires_in: number };
    if (!j.access_token) throw new OidcError('rejected', 'token response without access_token');
    return { accessToken: j.access_token, refreshToken: j.refresh_token ?? null, idToken: j.id_token ?? null, expiresIn: j.expires_in };
  }

  return {
    discover,
    redirectUri,
    async buildAuthorizationUrl(p) {
      const d = await discover();
      const url = new URL(d.authorization_endpoint);
      const q = url.searchParams;
      q.set('client_id', clientId);
      q.set('response_type', 'code');
      q.set('scope', cfg.scopes.join(' '));
      q.set('redirect_uri', redirectUri);
      q.set('state', p.state);
      q.set('nonce', p.nonce);
      q.set('code_challenge', p.challenge);
      q.set('code_challenge_method', 'S256');
      for (const [k, v] of Object.entries(cfg.oidc.authorizeParams ?? {})) q.set(k, v);
      for (const [k, v] of Object.entries(p.extra ?? {})) q.set(k, v);
      if (p.loginHint) q.set('login_hint', p.loginHint);
      if (p.prompt) q.set('prompt', p.prompt);
      if (p.maxAge !== undefined) q.set('max_age', String(p.maxAge));
      return url.toString();
    },
    exchangeCode: (code, verifier) =>
      tokenRequest(new URLSearchParams({ grant_type: 'authorization_code', code, code_verifier: verifier, redirect_uri: redirectUri })),
    refresh: (refreshToken) => tokenRequest(new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken })),
    async revokeRefreshToken(refreshToken) {
      const d = await discover();
      const url = d.revocation_endpoint ?? `${internal}/protocol/openid-connect/revoke`;
      const res = await post(url, new URLSearchParams({ token: refreshToken, token_type_hint: 'refresh_token' }), 3000);
      if (!res.ok) throw new OidcError('transient', `revoke ${res.status}`, res.status);
    },
    async verifyIdToken(idToken, expect) {
      const { payload } = await jwtVerify(idToken, await keys(), {
        issuer: pub,
        audience: clientId,
        clockTolerance: 10,
        currentDate: new Date(cfg.clock()),
      });
      if (payload.nonce !== expect.nonce) throw new Error('nonce mismatch');
      if (payload.azp !== undefined && payload.azp !== clientId) throw new Error('azp mismatch');
      if (Array.isArray(payload.aud) && payload.aud.length > 1 && payload.azp !== clientId) throw new Error('azp required');
      return payload;
    },
    async verifyAccessToken(accessToken) {
      const { payload } = await jwtVerify(accessToken, await keys(), { issuer: pub, clockTolerance: 10, currentDate: new Date(cfg.clock()) });
      return payload;
    },
    async verifyLogoutToken(token) {
      const { payload } = await jwtVerify(token, await keys(), {
        issuer: pub,
        audience: clientId,
        clockTolerance: 10,
        currentDate: new Date(cfg.clock()),
      });
      const events = payload.events as Record<string, unknown> | undefined;
      if (!events || typeof events !== 'object' || !(BCL_EVENT in events)) throw new Error('not a logout token');
      if ('nonce' in payload) throw new Error('logout token must not carry nonce');
      if (!payload.sid && !payload.sub) throw new Error('logout token needs sid or sub');
      if (typeof payload.jti !== 'string' || !payload.jti) throw new Error('logout token needs jti');
      const iat = typeof payload.iat === 'number' ? payload.iat : 0;
      if (Math.abs(cfg.clock() / 1000 - iat) > 120) throw new Error('logout token iat out of range');
      return payload;
    },
    async endSessionUrl(p) {
      try {
        const d = await discover();
        if (!d.end_session_endpoint) return null;
        const url = new URL(d.end_session_endpoint);
        url.searchParams.set('client_id', clientId);
        url.searchParams.set('post_logout_redirect_uri', p.postLogoutRedirect);
        if (p.idTokenHint) url.searchParams.set('id_token_hint', p.idTokenHint);
        if (p.state) url.searchParams.set('state', p.state);
        return url.toString();
      } catch {
        return null;
      }
    },
    async endSessionServerSide(idToken) {
      try {
        const d = await discover();
        if (!d.end_session_endpoint) return false;
        const url = swap(d.end_session_endpoint, pub, internal);
        const res = await post(url, new URLSearchParams({ id_token_hint: idToken }), 3000);
        // Keycloak answers 200/204, or a redirect when it has a post-logout target; only an error is a failure.
        return res.status < 400;
      } catch {
        return false;
      }
    },
    resetCache() {
      cache = null;
      jwksCache = null;
    },
  };
}

/** Unverified decode of a token WE stored (never used for trust decisions on foreign input). */
export function peekClaims(token: string | null | undefined): JWTPayload {
  if (!token) return {};
  try {
    return decodeJwt(token);
  } catch {
    return {};
  }
}
