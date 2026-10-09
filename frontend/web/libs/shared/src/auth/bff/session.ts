import type { ResolvedConfig } from './config';
import { open, parseEncKeys, randomId, seal, sha256Hex, type EncKey } from './crypto';
import type { KeyValueStore } from './store';

export interface SessionRecord {
  v: 1;
  accountId: string | null;
  kcSid: string;
  sub: string;
  roles: string[];
  aud: string[];
  displayName: string | null;
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  /** access token expiry, epoch seconds */
  atExp: number;
  /** bumped by every successful refresh */
  rtVer: number;
  authTime: number | null;
  acr: string | null;
  /** epoch ms */
  createdAt: number;
  /** Opaque app bag (buyer cart). The shared module never reads it. */
  ext: Record<string, unknown>;
}

/** What server components and the browser may know. No tokens. */
export interface PublicSession {
  sessionId: string;
  accountId: string | null;
  sub: string;
  roles: string[];
  displayName: string | null;
  authTime: number | null;
  acr: string | null;
}

export interface LoadedSession {
  /** sha256 of the cookie secret; also the Redis key suffix. */
  hash: string;
  record: SessionRecord;
  ver: number;
  seen: number;
  created: number;
}

export const TOUCH_INTERVAL_MS = 60_000;

export function toPublic(s: LoadedSession): PublicSession {
  return {
    sessionId: s.hash.slice(0, 16),
    accountId: s.record.accountId,
    sub: s.record.sub,
    roles: s.record.roles,
    displayName: s.record.displayName,
    authTime: s.record.authTime,
    acr: s.record.acr,
  };
}

export class SessionService {
  readonly keys: EncKey[];
  readonly prefix: string;

  constructor(
    private readonly cfg: Pick<ResolvedConfig, 'app' | 'idleSec' | 'absoluteSec' | 'clock' | 'logger'>,
    readonly store: KeyValueStore,
    encKeys: string
  ) {
    this.keys = parseEncKeys(encKeys);
    this.prefix = `bff:${cfg.app}:`;
  }

  private sessKey = (h: string) => `${this.prefix}sess:${h}`;
  private sidKey = (sid: string) => `${this.prefix}sid:${sid}`;
  private subKey = (sub: string) => `${this.prefix}sub:${sub}`;
  hashOf = (cookieValue: string) => sha256Hex(cookieValue);

  private aad(h: string) {
    return `sess:${this.cfg.app}:${h}`;
  }

  /** Seconds until the earlier of the idle and absolute deadlines, or <= 0 when expired. */
  remainingSec(created: number, seen: number, now = this.cfg.clock()): number {
    const idle = this.cfg.idleSec - (now - seen) / 1000;
    const abs = this.cfg.absoluteSec - (now - created) / 1000;
    return Math.floor(Math.min(idle, abs));
  }

  /** Creates a session under a NEW secret (rotation) and deletes `replacing` if given. */
  async create(record: SessionRecord, replacing?: string | null): Promise<{ cookieValue: string; hash: string; maxAgeSec: number }> {
    const now = this.cfg.clock();
    const cookieValue = randomId(32);
    const hash = this.hashOf(cookieValue);
    const rec: SessionRecord = { ...record, createdAt: now };
    const ttl = this.remainingSec(now, now, now);
    await this.store.putSession(
      this.sessKey(hash),
      { data: seal(JSON.stringify(rec), this.keys, this.aad(hash)), ver: 1, created: now, seen: now },
      ttl
    );
    await this.store.sadd(this.sidKey(rec.kcSid), hash, this.cfg.absoluteSec);
    await this.store.sadd(this.subKey(rec.sub), hash, this.cfg.absoluteSec);
    if (replacing) await this.destroyByCookie(replacing).catch(() => undefined);
    return { cookieValue, hash, maxAgeSec: ttl };
  }

  /** Loads, enforces idle + absolute, and slides `seen` at most once a minute. */
  async load(cookieValue: string | null | undefined, opts: { touch?: boolean } = {}): Promise<LoadedSession | null> {
    if (!cookieValue || cookieValue.length < 20 || cookieValue.length > 128) return null;
    const hash = this.hashOf(cookieValue);
    const row = await this.store.getSession(this.sessKey(hash));
    if (!row) return null;
    const now = this.cfg.clock();
    const ttl = this.remainingSec(row.created, row.seen, now);
    const text = ttl > 0 ? open(row.data, this.keys, this.aad(hash)) : null;
    if (!text) {
      await this.destroyHash(hash).catch(() => undefined);
      return null;
    }
    const record = JSON.parse(text) as SessionRecord;
    let seen = row.seen;
    if (opts.touch !== false && now - row.seen >= TOUCH_INTERVAL_MS) {
      seen = now;
      const newTtl = this.remainingSec(row.created, seen, now);
      await this.store.touchSession(this.sessKey(hash), seen, newTtl).catch(() => undefined);
    }
    return { hash, record, ver: row.ver, seen, created: row.created };
  }

  /** Compare-and-set the record. Keeps the TTL derived from the deadlines. */
  async cas(s: LoadedSession, next: SessionRecord): Promise<boolean> {
    const ttl = Math.max(1, this.remainingSec(s.created, s.seen));
    return this.store.casSession(this.sessKey(s.hash), s.ver, seal(JSON.stringify(next), this.keys, this.aad(s.hash)), ttl);
  }

  async reload(hash: string): Promise<LoadedSession | null> {
    const row = await this.store.getSession(this.sessKey(hash));
    if (!row) return null;
    const text = open(row.data, this.keys, this.aad(hash));
    if (!text || this.remainingSec(row.created, row.seen) <= 0) return null;
    return { hash, record: JSON.parse(text) as SessionRecord, ver: row.ver, seen: row.seen, created: row.created };
  }

  async destroyByCookie(cookieValue: string): Promise<void> {
    await this.destroyHash(this.hashOf(cookieValue));
  }

  async destroyHash(hash: string): Promise<void> {
    const row = await this.store.getSession(this.sessKey(hash));
    await this.store.del(this.sessKey(hash));
    if (!row) return;
    const text = open(row.data, this.keys, this.aad(hash));
    if (!text) return;
    const rec = JSON.parse(text) as SessionRecord;
    await this.store.srem(this.sidKey(rec.kcSid), hash);
    await this.store.srem(this.subKey(rec.sub), hash);
  }

  /** Back-channel logout by Keycloak sid. Returns the number of sessions removed. */
  async destroyBySid(sid: string): Promise<number> {
    const members = await this.store.smembers(this.sidKey(sid));
    for (const h of members) await this.destroyHash(h);
    await this.store.del(this.sidKey(sid));
    return members.length;
  }

  async destroyBySub(sub: string): Promise<number> {
    const members = await this.store.smembers(this.subKey(sub));
    for (const h of members) await this.destroyHash(h);
    await this.store.del(this.subKey(sub));
    return members.length;
  }

  /** Cheap read used by the proxy; same checks, never slides the idle clock. */
  loadLight(cookieValue: string | null | undefined) {
    return this.load(cookieValue, { touch: false });
  }

  // ---- generic sealed values (flows, hints) -----------------------------------------------
  sealValue(purpose: string, id: string, value: unknown): string {
    return seal(JSON.stringify(value), this.keys, `${purpose}:${this.cfg.app}:${id}`);
  }
  openValue<T>(purpose: string, id: string, sealed: string | null): T | null {
    if (!sealed) return null;
    const t = open(sealed, this.keys, `${purpose}:${this.cfg.app}:${id}`);
    return t ? (JSON.parse(t) as T) : null;
  }
}
