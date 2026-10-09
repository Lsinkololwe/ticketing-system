export interface SessionHash {
  data: string;
  ver: number;
  created: number;
  seen: number;
}

export interface WindowResult {
  allowed: boolean;
  count: number;
  /** ms until the oldest hit leaves the window (0 when allowed). */
  retryAfterMs: number;
}

/**
 * Everything the BFF needs from Redis. Two implementations: ioredis (Lua scripts, atomic) and an
 * in-memory double with identical semantics for unit tests.
 */
export interface KeyValueStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string, ttlSec: number): Promise<void>;
  /** SET NX PX. True when the key was created. */
  setNx(key: string, value: string, ttlMs: number): Promise<boolean>;
  /** Atomic read-and-delete. */
  getDel(key: string): Promise<string | null>;
  del(...keys: string[]): Promise<void>;
  /** Compare-and-delete (lock release). True when deleted. */
  delIfEquals(key: string, expected: string): Promise<boolean>;

  /** Sessions are hashes {data, ver, created, seen}; `ver` is bumped by every data write. */
  putSession(key: string, rec: SessionHash, ttlSec: number): Promise<void>;
  getSession(key: string): Promise<SessionHash | null>;
  /** Writes `data` and `ver+1` only if the stored `ver` equals `expectedVer`; keeps `created`/`seen`. */
  casSession(key: string, expectedVer: number, data: string, ttlSec: number): Promise<boolean>;
  /** Updates `seen` and the TTL without touching `data`/`ver`. False if the session is gone. */
  touchSession(key: string, seen: number, ttlSec: number): Promise<boolean>;

  sadd(key: string, member: string, ttlSec: number): Promise<void>;
  srem(key: string, member: string): Promise<void>;
  smembers(key: string): Promise<string[]>;
  lpush(key: string, value: string, ttlSec: number): Promise<void>;

  /** Sliding window log (sorted set). `member` must be unique per hit. */
  slidingWindow(key: string, nowMs: number, windowMs: number, limit: number, member: string): Promise<WindowResult>;
  ping(): Promise<void>;
  quit(): Promise<void>;
}
