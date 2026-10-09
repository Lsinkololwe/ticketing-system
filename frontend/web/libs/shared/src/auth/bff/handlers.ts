import type { JWTPayload } from 'jose';
import type { BffDeps } from './context';
import { clearCookie, serializeCookie } from './cookies';
import { assertNotCrossSite, assertSameOrigin } from './csrf';
import { randomId, sha256Hex } from './crypto';
import { FLOW_TTL_SEC, HINT_TTL_SEC, type FlowRecord, type OAuthFlow } from './flow';
import { safeReturnTo } from './guards';
import { gate, json, problem, reqCtx, see, type ReqCtx } from './http';
import { generatePkce, hasAudience, peekClaims, rolesOf } from './oidc';
import type { SessionRecord } from './session';
import { endSso } from './sso';
import { toPublic } from './session';

export type RouteHandler = (req: Request) => Promise<Response>;

function flowCookie(deps: BffDeps, id: string): string {
  return serializeCookie(deps.names.flow, id, { maxAge: FLOW_TTL_SEC, sameSite: 'lax', secure: deps.cfg.secure });
}
function clearFlowCookie(deps: BffDeps): string {
  return clearCookie(deps.names.flow, deps.cfg.secure);
}
function sessionCookie(deps: BffDeps, value: string, maxAge: number): string {
  return serializeCookie(deps.names.session, value, { maxAge, sameSite: deps.cfg.sameSite, secure: deps.cfg.secure });
}
function clearSessionCookie(deps: BffDeps): string {
  return clearCookie(deps.names.session, deps.cfg.secure, deps.cfg.sameSite);
}
function ssoCookie(deps: BffDeps): string {
  return serializeCookie(deps.names.sso, '1', { maxAge: HINT_TTL_SEC, sameSite: 'lax', secure: deps.cfg.secure });
}
function loginError(deps: BffDeps, code: string, cookies: string[] = []): Response {
  return see(deps, `${deps.cfg.loginPath}?error=${encodeURIComponent(code)}`, cookies);
}

/** Authorization requests: both `start` and `stepup` build theirs here. */
async function beginAuthorization(
  deps: BffDeps,
  flowId: string,
  rec: FlowRecord,
  oauth: Omit<OAuthFlow, 'state' | 'nonce' | 'verifier' | 'startedAt'>,
  extra: { loginHint?: string; prompt?: string; maxAge?: number; acrValues?: string }
): Promise<Response> {
  const pkce = generatePkce();
  const full: OAuthFlow = { ...oauth, state: randomId(24), nonce: randomId(24), verifier: pkce.verifier, startedAt: deps.cfg.clock() };
  await deps.flows.save(flowId, { ...rec, oauth: full });
  const url = await deps.oidc.buildAuthorizationUrl({
    state: full.state,
    nonce: full.nonce,
    challenge: pkce.challenge,
    loginHint: extra.loginHint,
    prompt: extra.prompt,
    maxAge: extra.maxAge,
    extra: extra.acrValues ? { acr_values: extra.acrValues } : undefined,
  });
  return see(deps, url, [flowCookie(deps, flowId)], { 'referrer-policy': 'no-referrer' });
}

// ---------------------------------------------------------------------------------------------
// GET /api/auth/start
// ---------------------------------------------------------------------------------------------
export function startHandler(deps: BffDeps): RouteHandler {
  return async (req) => {
    const ctx = reqCtx(req, deps);
    const log = deps.cfg.logger;
    if (!assertNotCrossSite(req)) {
      log.warn('auth.start.cross_site', { cid: ctx.correlationId });
      return loginError(deps, 'LOGIN_FAILED');
    }
    const flowIdIn = ctx.cookies.get(deps.names.flow) ?? null;
    const limited = await gate(deps, 'start', { ip: ctx.ip, flow: flowIdIn });
    if (limited) return limited;

    const url = new URL(req.url);
    const resume = url.searchParams.get('resume') === '1';
    const flowId = flowIdIn && flowIdIn.length >= 20 ? flowIdIn : deps.flows.newId();
    const rec: FlowRecord = (flowIdIn ? await deps.flows.load(flowIdIn) : null) ?? {};
    let returnTo = safeReturnTo(url.searchParams.get('next'));
    if (resume && rec.pendingReturnTo) returnTo = safeReturnTo(rec.pendingReturnTo);

    const current = await deps.sessions.load(ctx.cookies.get(deps.names.session));

    if (deps.cfg.mode === 'handoff') {
      const handle = rec.handle;
      if (!handle || handle.expiresAt < deps.cfg.clock()) return loginError(deps, 'LOGIN_HANDLE_INVALID');

      // A previous Keycloak SSO session would swallow the handle (POC KC-01): end it first.
      const marker = ctx.cookies.has(deps.names.sso);
      if (!resume && !rec.hopped && (marker || current)) {
        const device = ctx.cookies.get(deps.names.device);
        const hint = current?.record.idToken ?? (await deps.flows.takeHint(device));
        const back = `${deps.cfg.appUrl}/api/auth/start?resume=1`;
        const end = await deps.oidc.endSessionUrl({ idTokenHint: hint, postLogoutRedirect: back, state: randomId(12) });
        if (end) {
          if (current) await deps.sessions.destroyHash(current.hash).catch(() => undefined);
          await deps.flows.save(flowId, { ...rec, hopped: true, pendingReturnTo: returnTo });
          log.info('auth.start.sso_hop', { cid: ctx.correlationId, hinted: Boolean(hint) });
          return see(deps, end, [flowCookie(deps, flowId), clearSessionCookie(deps), clearCookie(deps.names.sso, deps.cfg.secure)], {
            'referrer-policy': 'no-referrer',
          });
        }
      }
      // Handle is single-use: drop it from the flow before leaving.
      const { handle: _used, hopped: _h, pendingReturnTo: _p, ...rest } = rec;
      void _used; void _h; void _p;
      try {
        return await beginAuthorization(
          deps, flowId, rest,
          { kind: 'login', returnTo, expectHandleAccountId: handle.accountId },
          { loginHint: handle.value, prompt: 'login', maxAge: 0 }
        );
      } catch (err) {
        log.error('auth.start.discovery_failed', { cid: ctx.correlationId, err });
        return loginError(deps, 'SERVICE_UNAVAILABLE');
      }
    }

    if (current && !resume) return see(deps, returnTo);
    try {
      return await beginAuthorization(deps, flowId, rec, { kind: 'login', returnTo }, {});
    } catch (err) {
      log.error('auth.start.discovery_failed', { cid: ctx.correlationId, err });
      return loginError(deps, 'SERVICE_UNAVAILABLE');
    }
  };
}

// ---------------------------------------------------------------------------------------------
// GET /api/auth/stepup?next=&maxAge=
// ---------------------------------------------------------------------------------------------
export function stepUpHandler(deps: BffDeps): RouteHandler {
  return async (req) => {
    const ctx = reqCtx(req, deps);
    if (!assertNotCrossSite(req)) return problem(403, 'ORIGIN_REJECTED');
    if (req.method === 'POST') {
      const bad = assertSameOrigin(req, deps.cfg.appOrigin, { requireHeader: false });
      if (bad) return problem(403, 'ORIGIN_REJECTED');
    }
    const cur = await deps.sessions.load(ctx.cookies.get(deps.names.session));
    if (!cur) return see(deps, `${deps.cfg.loginPath}?next=${encodeURIComponent(safeReturnTo(new URL(req.url).searchParams.get('next')))}`);
    const limited = await gate(deps, 'stepup', { session: cur.hash });
    if (limited) return limited;
    const url = new URL(req.url);
    const maxAge = Math.min(3600, Math.max(0, Number(url.searchParams.get('maxAge') ?? 300) || 0));
    const flowId = deps.flows.newId();
    const acr = deps.cfg.oidc.authorizeParams?.acr_values;
    return beginAuthorization(
      deps, flowId, {},
      { kind: 'stepup', returnTo: safeReturnTo(url.searchParams.get('next')), maxAge, expectSub: cur.record.sub, expectAccountId: cur.record.accountId },
      { prompt: 'login', maxAge, acrValues: acr }
    );
  };
}

// ---------------------------------------------------------------------------------------------
// GET /api/auth/callback
// ---------------------------------------------------------------------------------------------
export function callbackHandler(deps: BffDeps): RouteHandler {
  return async (req) => {
    const ctx = reqCtx(req, deps);
    const log = deps.cfg.logger;
    const blocked = await deps.limiter.isBlocked('callback_fail', { ip: ctx.ip });
    if (blocked) return problem(429, 'RATE_LIMITED', { retryAfterSeconds: blocked }, { 'retry-after': String(blocked) });
    const limited = await gate(deps, 'callback', { ip: ctx.ip });
    if (limited) return limited;

    const flowId = ctx.cookies.get(deps.names.flow) ?? null;
    const fail = async (reason: string, code = 'SIGN_IN_FAILED', extraCookies: string[] = []) => {
      log.warn('auth.callback.failed', { cid: ctx.correlationId, reason });
      await deps.limiter.consume('callback_fail', { ip: ctx.ip });
      await deps.flows.delete(flowId).catch(() => undefined);
      return loginError(deps, code, [clearFlowCookie(deps), ...extraCookies]);
    };

    const url = new URL(req.url);
    if (url.searchParams.get('error')) return fail('provider_error');
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    const rec = flowId ? await deps.flows.load(flowId) : null;
    const oauth = rec?.oauth;
    if (!flowId || !rec || !oauth || !code || !state || state !== oauth.state) return fail('flow_or_state');

    let tokens;
    let idClaims: JWTPayload;
    let accessClaims: JWTPayload;
    try {
      tokens = await deps.oidc.exchangeCode(code, oauth.verifier);
      if (!tokens.idToken) return fail('no_id_token');
      idClaims = await deps.oidc.verifyIdToken(tokens.idToken, { nonce: oauth.nonce });
      accessClaims = await deps.oidc.verifyAccessToken(tokens.accessToken);
    } catch (err) {
      return fail(`token_${(err as Error).name}`);
    }
    const idToken = tokens.idToken;

    /** Ends the Keycloak session we just created so no SSO is left behind, then lands on the login page. */
    const rejectAndEndSso = async (reason: string, errorCode: string) => {
      log.warn('auth.callback.rejected', { cid: ctx.correlationId, reason });
      await deps.limiter.consume('callback_fail', { ip: ctx.ip });
      await deps.flows.delete(flowId).catch(() => undefined);
      const target = `${deps.cfg.appUrl}${deps.cfg.loginPath}?error=${encodeURIComponent(errorCode)}`;
      const end = await deps.oidc.endSessionUrl({ idTokenHint: idToken, postLogoutRedirect: target });
      if (tokens.refreshToken) await deps.oidc.revokeRefreshToken(tokens.refreshToken).catch(() => undefined);
      return end ? see(deps, end, [clearFlowCookie(deps)]) : loginError(deps, errorCode, [clearFlowCookie(deps)]);
    };

    const sid = typeof idClaims.sid === 'string' ? idClaims.sid : null;
    const sub = typeof idClaims.sub === 'string' ? idClaims.sub : null;
    if (!sid || !sub) return fail('no_sid_or_sub');
    if (sub !== accessClaims.sub) return fail('sub_mismatch');

    const audience = deps.cfg.access?.audience;
    if (audience && !hasAudience(accessClaims, audience)) return rejectAndEndSso('audience', 'FORBIDDEN');
    const roles = rolesOf(accessClaims, deps.cfg.oidc.clientId);
    const needed = deps.cfg.access?.roles;
    if (needed?.length && !needed.some((r) => roles.includes(r))) return rejectAndEndSso('role', 'FORBIDDEN');

    const claim = deps.cfg.access?.accountClaim;
    const rawAccount = claim ? (idClaims[claim] ?? accessClaims[claim]) : null;
    const accountId = typeof rawAccount === 'string' && rawAccount ? rawAccount : null;
    if (claim && !accountId) return rejectAndEndSso('account_claim_missing', 'SIGN_IN_FAILED');

    const authTime = typeof idClaims.auth_time === 'number' ? idClaims.auth_time : null;
    const startedS = oauth.startedAt / 1000;
    if (oauth.kind === 'stepup' || oauth.expectHandleAccountId !== undefined) {
      if (authTime === null || authTime < startedS - 5) return rejectAndEndSso('stale_auth_time', 'SIGN_IN_FAILED');
    }
    if (oauth.expectHandleAccountId !== undefined && oauth.expectHandleAccountId !== accountId) {
      return rejectAndEndSso('handle_account_mismatch', 'SIGN_IN_FAILED');
    }

    const currentCookie = ctx.cookies.get(deps.names.session) ?? null;
    const previous = currentCookie ? await deps.sessions.load(currentCookie, { touch: false }) : null;
    if (oauth.kind === 'stepup') {
      if (!previous || previous.record.sub !== sub || previous.record.accountId !== accountId || oauth.expectSub !== sub) {
        return rejectAndEndSso('stepup_identity_changed', 'SIGN_IN_FAILED');
      }
    }
    if (await deps.flows.isTombstoned(sid)) return fail('tombstoned_sid');

    let displayName = typeof idClaims.name === 'string' ? idClaims.name : null;
    let finalAccount = accountId;
    let ext: Record<string, unknown> = { ...(rec.ext ?? {}) };
    if (oauth.kind === 'stepup' && previous) ext = { ...previous.record.ext };
    if (deps.cfg.postLogin && oauth.kind === 'login') {
      let r;
      try {
        r = await deps.cfg.postLogin({ accountId, sub, roles, claims: idClaims, correlationId: ctx.correlationId });
      } catch (err) {
        log.error('auth.callback.post_login_error', { cid: ctx.correlationId, err });
        return rejectAndEndSso('post_login_error', 'SERVICE_UNAVAILABLE');
      }
      if (!r.ok) {
        return r.endSso ? rejectAndEndSso('post_login_denied', r.error) : fail('post_login_denied', r.error);
      }
      displayName = r.displayName ?? displayName;
      finalAccount = r.accountId ?? finalAccount;
      ext = { ...ext, ...(r.ext ?? {}) };
    }

    const aud = Array.isArray(accessClaims.aud) ? accessClaims.aud : accessClaims.aud ? [accessClaims.aud] : [];
    const record: SessionRecord = {
      v: 1,
      accountId: finalAccount,
      kcSid: sid,
      sub,
      roles,
      aud,
      displayName,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      idToken,
      atExp: typeof accessClaims.exp === 'number' ? accessClaims.exp : Math.floor(deps.cfg.clock() / 1000) + tokens.expiresIn,
      rtVer: 0,
      authTime,
      acr: typeof idClaims.acr === 'string' ? idClaims.acr : null,
      createdAt: deps.cfg.clock(),
      ext,
    };
    const created = await deps.sessions.create(record, currentCookie);
    const cookies = [sessionCookie(deps, created.cookieValue, created.maxAgeSec), clearFlowCookie(deps)];
    if (deps.cfg.mode === 'handoff') {
      cookies.push(ssoCookie(deps));
      let device = ctx.cookies.get(deps.names.device);
      if (!device || device.length < 20) {
        device = randomId(32);
        cookies.push(serializeCookie(deps.names.device, device, { maxAge: HINT_TTL_SEC, sameSite: 'lax', secure: deps.cfg.secure }));
      }
      await deps.flows.saveHint(device, idToken).catch(() => undefined);
    }
    await deps.flows.delete(flowId).catch(() => undefined);
    log.info('auth.callback.ok', { cid: ctx.correlationId, session: created.hash.slice(0, 8), account: finalAccount ?? sub, kind: oauth.kind });
    return see(deps, safeReturnTo(oauth.returnTo), cookies, { 'referrer-policy': 'no-referrer' });
  };
}

// ---------------------------------------------------------------------------------------------
// POST /api/auth/logout
// ---------------------------------------------------------------------------------------------
export function logoutHandler(deps: BffDeps): RouteHandler {
  return async (req) => {
    const ctx = reqCtx(req, deps);
    const log = deps.cfg.logger;
    if (req.method !== 'POST') return problem(405, 'METHOD_NOT_ALLOWED', {}, { allow: 'POST' });
    if (assertSameOrigin(req, deps.cfg.appOrigin, { requireHeader: false })) return problem(403, 'ORIGIN_REJECTED');
    const cookie = ctx.cookies.get(deps.names.session) ?? null;
    const limited = await gate(deps, 'logout', { ip: ctx.ip, session: cookie ? sha256Hex(cookie) : null });
    if (limited) return limited;

    const clear = [clearSessionCookie(deps), clearFlowCookie(deps), clearCookie(deps.names.sso, deps.cfg.secure)];
    const s = await deps.sessions.load(cookie, { touch: false });
    if (!s) return see(deps, deps.cfg.postLogoutPath, clear);

    // 1-2: the local session goes first, so even a failure below cannot leave it usable.
    await deps.sessions.destroyHash(s.hash);
    const device = ctx.cookies.get(deps.names.device);
    if (deps.cfg.mode === 'handoff') await deps.flows.deleteHint(device).catch(() => undefined);

    // 3: identity-service revocation (sid + access-token jti). Failure is queued, never user-visible.
    const jti = peekClaims(s.record.accessToken).jti;
    if (deps.revocation) {
      try {
        // Token and session only. Revoking the SUBJECT would be "sign out everywhere" and would block the person's own
        // next sign-in for the whole revocation TTL: the gateway refuses every token whose sub is revoked.
        await deps.revocation.revoke({ jti: typeof jti === 'string' ? jti : undefined, sid: s.record.kcSid, reason: 'user_logout' });
      } catch (err) {
        log.error('auth.logout.revocation_failed', { cid: ctx.correlationId, err });
        await deps.sessions.store
          .lpush(`${deps.sessions.prefix}revq`, JSON.stringify({ jti, sid: s.record.kcSid, reason: 'user_logout', at: deps.cfg.clock() }), 86400)
          .catch(() => undefined);
      }
    }
    // 4: end the Keycloak SSO session from the server (then revoke the refresh token). The session id was just
    // revoked above, so leaving the SSO session alive would make the person's next sign-in come back already revoked.
    const ended = await endSso(deps, s.record, ctx.correlationId);
    if (ended) {
      log.info('auth.logout.ok', { cid: ctx.correlationId, session: s.hash.slice(0, 8), sso: 'ended' });
      return see(deps, deps.cfg.postLogoutPath, clear);
    }
    // 5: Keycloak could not be reached from here, so fall back to ending it from the browser (RP-initiated logout).
    const end = await deps.oidc.endSessionUrl({
      idTokenHint: s.record.idToken,
      postLogoutRedirect: `${deps.cfg.appUrl}${deps.cfg.postLogoutPath === '/' ? '/' : deps.cfg.postLogoutPath}`,
      state: randomId(12),
    });
    log.info('auth.logout.ok', { cid: ctx.correlationId, session: s.hash.slice(0, 8), sso: 'browser_hop' });
    return see(deps, end ?? deps.cfg.postLogoutPath, clear);
  };
}

// ---------------------------------------------------------------------------------------------
// POST /api/auth/backchannel-logout (Keycloak -> app, server to server)
// ---------------------------------------------------------------------------------------------
export function backchannelLogoutHandler(deps: BffDeps): RouteHandler {
  return async (req) => {
    const ctx = reqCtx(req, deps);
    const log = deps.cfg.logger;
    const noStore = { 'cache-control': 'no-store' };
    if (req.method !== 'POST') return new Response(null, { status: 405, headers: { ...noStore, allow: 'POST' } });
    const limited = await gate(deps, 'backchannel', { ip: ctx.ip });
    if (limited) return limited;
    if (!(req.headers.get('content-type') ?? '').toLowerCase().startsWith('application/x-www-form-urlencoded')) {
      return new Response(null, { status: 400, headers: noStore });
    }
    let token: string | null;
    try {
      token = new URLSearchParams(await req.text()).get('logout_token');
    } catch {
      return new Response(null, { status: 400, headers: noStore });
    }
    if (!token || token.length > 8192) return new Response(null, { status: 400, headers: noStore });

    let claims: JWTPayload;
    try {
      claims = await deps.oidc.verifyLogoutToken(token);
    } catch (err) {
      log.warn('auth.bcl.invalid_token', { cid: ctx.correlationId, reason: (err as Error).name });
      return new Response(null, { status: 400, headers: noStore });
    }
    try {
      const fresh = await deps.sessions.store.setNx(`${deps.sessions.prefix}bcl:jti:${claims.jti}`, '1', 600_000);
      if (!fresh) return new Response(null, { status: 200, headers: noStore }); // replay: already handled
      const sid = typeof claims.sid === 'string' ? claims.sid : null;
      const sub = typeof claims.sub === 'string' ? claims.sub : null;
      let removed = 0;
      if (sid) {
        removed = await deps.sessions.destroyBySid(sid);
        await deps.flows.tombstone(sid);
      } else if (sub) {
        removed = await deps.sessions.destroyBySub(sub);
      }
      // Idempotent: identity-service may already have revoked from its own Keycloak listener (D4).
      if (deps.revocation) await deps.revocation.revoke({ sid: sid ?? undefined, sub: sid ? undefined : sub ?? undefined, reason: 'backchannel_logout' });
      log.info('auth.bcl.ok', { cid: ctx.correlationId, removed, by: sid ? 'sid' : 'sub' });
      return new Response(null, { status: 200, headers: noStore });
    } catch (err) {
      // Infrastructure failure: 5xx so Keycloak retries. Release the replay marker so the retry runs.
      log.error('auth.bcl.failed', { cid: ctx.correlationId, err });
      await deps.sessions.store.del(`${deps.sessions.prefix}bcl:jti:${claims.jti}`).catch(() => undefined);
      return new Response(null, { status: 503, headers: noStore });
    }
  };
}

// ---------------------------------------------------------------------------------------------
// GET /api/auth/session
// ---------------------------------------------------------------------------------------------
export function sessionHandler(deps: BffDeps): RouteHandler {
  return async (req) => {
    const ctx = reqCtx(req, deps);
    const s = await deps.sessions.load(ctx.cookies.get(deps.names.session));
    if (!s) return json({ authenticated: false });
    const p = toPublic(s);
    return json({ authenticated: true, accountId: p.accountId, displayName: p.displayName, roles: p.roles, authTime: p.authTime });
  };
}

export type AuthAction = 'start' | 'callback' | 'logout' | 'backchannel-logout' | 'stepup' | 'session';

export function buildHandlers(deps: BffDeps) {
  const table: Record<AuthAction, RouteHandler> = {
    start: startHandler(deps),
    callback: callbackHandler(deps),
    logout: logoutHandler(deps),
    'backchannel-logout': backchannelLogoutHandler(deps),
    stepup: stepUpHandler(deps),
    session: sessionHandler(deps),
  };
  const methods: Record<AuthAction, string[]> = {
    start: ['GET'],
    callback: ['GET'],
    logout: ['POST'],
    'backchannel-logout': ['POST'],
    stepup: ['GET', 'POST'],
    session: ['GET'],
  };
  /** Dispatcher for `app/api/auth/[action]/route.ts`: `export const GET = bff.handlers.GET` etc. */
  const dispatch = async (req: Request, routeCtx: { params: Promise<{ action: string }> | { action: string } }): Promise<Response> => {
    const { action } = await routeCtx.params;
    const handler = (table as Record<string, RouteHandler | undefined>)[action];
    if (!handler) return new Response(null, { status: 404, headers: { 'cache-control': 'no-store' } });
    if (!methods[action as AuthAction].includes(req.method)) {
      return new Response(null, { status: 405, headers: { 'cache-control': 'no-store', allow: methods[action as AuthAction].join(', ') } });
    }
    return handler(req);
  };
  return { ...table, GET: dispatch, POST: dispatch };
}

export type { ReqCtx };
