import { randomId } from './crypto';
import type { ResolvedConfig } from './config';
import { OidcError, peekClaims, rolesOf, hasAudience, type OidcClient } from './oidc';
import type { LoadedSession, SessionRecord, SessionService } from './session';

export type TokenFailure = 'NO_SESSION' | 'SESSION_ENDED' | 'REFRESH_BUSY' | 'UPSTREAM_UNAVAILABLE';
export type TokenResult =
  | { ok: true; accessToken: string; session: LoadedSession }
  | { ok: false; reason: TokenFailure };

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
const jitter = (base: number) => base + Math.floor(Math.random() * base);

/**
 * Access-token supplier with single-flight refresh.
 *
 * Why: Keycloak realms run `revokeRefreshToken=true, refreshTokenMaxReuse=0`, so presenting one
 * refresh token twice (parallel requests, several tabs, several nodes) is reuse and kills the
 * whole SSO session. Layers:
 *   1. in-process promise map: concurrent callers in one Node process share one refresh;
 *   2. Redis `SET NX PX` lock: one refresher across processes, waiters poll the session;
 *   3. compare-and-set write: a session changed under us (logout, touch) is never clobbered, and
 *      a freshly rotated refresh token is never lost to a stale overwrite.
 */
export class TokenManager {
  private readonly inflight = new Map<string, Promise<TokenResult>>();
  private readonly lockMs: number;
  private readonly pollMs: number;
  private readonly waitMs: number;
  private readonly casRetries: number;

  constructor(
    private readonly cfg: Pick<ResolvedConfig, 'refresh' | 'refreshSkewSec' | 'clock' | 'logger' | 'access' | 'oidc'>,
    private readonly sessions: SessionService,
    private readonly oidc: OidcClient
  ) {
    this.lockMs = cfg.refresh?.lockMs ?? 10_000;
    this.pollMs = cfg.refresh?.pollMs ?? 75;
    this.waitMs = cfg.refresh?.waitMs ?? 5_000;
    this.casRetries = cfg.refresh?.casRetries ?? 3;
  }

  private fresh(rec: SessionRecord): boolean {
    return rec.atExp - this.cfg.refreshSkewSec > this.cfg.clock() / 1000;
  }

  async getAccessToken(cookieValue: string | null | undefined): Promise<TokenResult> {
    const s = await this.sessions.load(cookieValue);
    if (!s) return { ok: false, reason: 'NO_SESSION' };
    return this.forSession(s);
  }

  forSession(s: LoadedSession): Promise<TokenResult> {
    if (this.fresh(s.record)) return Promise.resolve({ ok: true, accessToken: s.record.accessToken, session: s });
    const existing = this.inflight.get(s.hash);
    if (existing) return existing;
    const p = this.refreshAcrossProcesses(s).finally(() => this.inflight.delete(s.hash));
    this.inflight.set(s.hash, p);
    return p;
  }

  private async refreshAcrossProcesses(s: LoadedSession): Promise<TokenResult> {
    const lockKey = `${this.sessions.prefix}lock:refresh:${s.hash}`;
    const deadline = Date.now() + this.waitMs;
    for (;;) {
      const owner = randomId(16);
      let got = false;
      try {
        got = await this.sessions.store.setNx(lockKey, owner, this.lockMs);
      } catch (err) {
        this.cfg.logger.error('refresh.lock_store_error', { err });
        return { ok: false, reason: 'UPSTREAM_UNAVAILABLE' };
      }
      if (got) {
        try {
          return await this.refreshLocked(s.hash);
        } finally {
          await this.sessions.store.delIfEquals(lockKey, owner).catch(() => undefined);
        }
      }
      await sleep(this.pollMs);
      const cur = await this.sessions.reload(s.hash).catch(() => null);
      if (!cur) return { ok: false, reason: 'SESSION_ENDED' };
      if (cur.record.rtVer !== s.record.rtVer || this.fresh(cur.record)) {
        return { ok: true, accessToken: cur.record.accessToken, session: cur };
      }
      if (Date.now() >= deadline) return { ok: false, reason: 'REFRESH_BUSY' };
    }
  }

  private async refreshLocked(hash: string): Promise<TokenResult> {
    let cur = await this.sessions.reload(hash);
    if (!cur) return { ok: false, reason: 'SESSION_ENDED' };
    // Double check: another node may have refreshed between our read and the lock.
    if (this.fresh(cur.record)) return { ok: true, accessToken: cur.record.accessToken, session: cur };
    const rt = cur.record.refreshToken;
    if (!rt) {
      await this.sessions.destroyHash(hash).catch(() => undefined);
      return { ok: false, reason: 'SESSION_ENDED' };
    }

    let tokens;
    try {
      tokens = await this.oidc.refresh(rt);
    } catch (err) {
      if (err instanceof OidcError && err.kind === 'invalid_grant') {
        this.cfg.logger.info('refresh.invalid_grant', { session: hash.slice(0, 8) });
        await this.sessions.destroyHash(hash).catch(() => undefined);
        return { ok: false, reason: 'SESSION_ENDED' };
      }
      this.cfg.logger.warn('refresh.unavailable', { session: hash.slice(0, 8), kind: err instanceof OidcError ? err.kind : 'unknown' });
      return { ok: false, reason: 'UPSTREAM_UNAVAILABLE' };
    }

    const claims = peekClaims(tokens.accessToken);
    const roles = rolesOf(claims, this.cfg.oidc.clientId);
    const audience = this.cfg.access?.audience;
    if (audience && !hasAudience(claims, audience)) {
      this.cfg.logger.warn('refresh.audience_lost', { session: hash.slice(0, 8) });
      await this.sessions.destroyHash(hash).catch(() => undefined);
      return { ok: false, reason: 'SESSION_ENDED' };
    }
    const required = this.cfg.access?.roles;
    if (required?.length && !required.some((r) => roles.includes(r))) {
      this.cfg.logger.warn('refresh.roles_lost', { session: hash.slice(0, 8) });
      await this.sessions.destroyHash(hash).catch(() => undefined);
      return { ok: false, reason: 'SESSION_ENDED' };
    }
    const aud = Array.isArray(claims.aud) ? claims.aud : claims.aud ? [claims.aud] : [];
    const baseVer = cur.record.rtVer;
    const apply = (rec: SessionRecord): SessionRecord => ({
      ...rec,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken ?? rec.refreshToken,
      idToken: tokens.idToken ?? rec.idToken,
      atExp: typeof claims.exp === 'number' ? claims.exp : Math.floor(this.cfg.clock() / 1000) + tokens.expiresIn,
      roles: roles.length ? roles : rec.roles,
      aud: aud.length ? aud : rec.aud,
      rtVer: baseVer + 1,
    });

    // The refresh token is spent now: persist the new one with tiny, jittered retries.
    for (let attempt = 0; attempt < this.casRetries; attempt++) {
      try {
        const next = apply(cur.record);
        if (await this.sessions.cas(cur, next)) {
          const stored = await this.sessions.reload(hash);
          return { ok: true, accessToken: tokens.accessToken, session: stored ?? { ...cur, record: next, ver: cur.ver + 1 } };
        }
        const latest = await this.sessions.reload(hash);
        if (!latest) return { ok: false, reason: 'SESSION_ENDED' }; // logged out meanwhile: do not resurrect
        if (latest.record.rtVer !== baseVer) return { ok: true, accessToken: latest.record.accessToken, session: latest };
        cur = latest; // only a touch/ext change: re-apply our tokens on top
      } catch (err) {
        this.cfg.logger.warn('refresh.persist_retry', { attempt, err });
        await sleep(jitter(20 * 2 ** attempt));
      }
    }
    this.cfg.logger.error('refresh.persist_failed', { session: hash.slice(0, 8) });
    // We still hold a valid access token for THIS request; the next refresh will hit invalid_grant
    // (the stored refresh token is spent) and end the session. Accepted residual risk (design 6.5).
    return { ok: true, accessToken: tokens.accessToken, session: { ...cur, record: apply(cur.record) } };
  }
}
