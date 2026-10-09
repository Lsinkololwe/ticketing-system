import type { KeyValueStore, SessionHash, WindowResult } from './types';

interface Entry<T> {
  value: T;
  expires: number;
}

/** In-memory KeyValueStore with the same semantics as the Redis one. Tests and local dev only. */
export class MemoryStore implements KeyValueStore {
  private strings = new Map<string, Entry<string>>();
  private hashes = new Map<string, Entry<SessionHash>>();
  private sets = new Map<string, Entry<Set<string>>>();
  private lists = new Map<string, Entry<string[]>>();
  private windows = new Map<string, Entry<{ at: number; m: string }[]>>();
  /** Fault injection for tests. */
  public failNext: ((op: string) => boolean) | null = null;

  constructor(private readonly clock: () => number = Date.now) {}

  private check(op: string) {
    if (this.failNext?.(op)) throw new Error(`injected store failure: ${op}`);
  }
  private live<T>(m: Map<string, Entry<T>>, key: string): Entry<T> | undefined {
    const e = m.get(key);
    if (!e) return undefined;
    if (e.expires <= this.clock()) {
      m.delete(key);
      return undefined;
    }
    return e;
  }

  async get(key: string) {
    this.check('get');
    return this.live(this.strings, key)?.value ?? null;
  }
  async set(key: string, value: string, ttlSec: number) {
    this.check('set');
    this.strings.set(key, { value, expires: this.clock() + ttlSec * 1000 });
  }
  async setNx(key: string, value: string, ttlMs: number) {
    this.check('setNx');
    if (this.live(this.strings, key)) return false;
    this.strings.set(key, { value, expires: this.clock() + ttlMs });
    return true;
  }
  async getDel(key: string) {
    this.check('getDel');
    const v = this.live(this.strings, key)?.value ?? null;
    this.strings.delete(key);
    return v;
  }
  async del(...keys: string[]) {
    this.check('del');
    for (const k of keys) {
      this.strings.delete(k);
      this.hashes.delete(k);
      this.sets.delete(k);
      this.lists.delete(k);
      this.windows.delete(k);
    }
  }
  async delIfEquals(key: string, expected: string) {
    this.check('delIfEquals');
    const e = this.live(this.strings, key);
    if (e && e.value === expected) {
      this.strings.delete(key);
      return true;
    }
    return false;
  }
  async putSession(key: string, rec: SessionHash, ttlSec: number) {
    this.check('putSession');
    this.hashes.set(key, { value: { ...rec }, expires: this.clock() + ttlSec * 1000 });
  }
  async getSession(key: string) {
    this.check('getSession');
    const e = this.live(this.hashes, key);
    return e ? { ...e.value } : null;
  }
  async casSession(key: string, expectedVer: number, data: string, ttlSec: number) {
    this.check('casSession');
    const e = this.live(this.hashes, key);
    if (!e || e.value.ver !== expectedVer) return false;
    e.value = { ...e.value, data, ver: expectedVer + 1 };
    e.expires = this.clock() + ttlSec * 1000;
    return true;
  }
  async touchSession(key: string, seen: number, ttlSec: number) {
    this.check('touchSession');
    const e = this.live(this.hashes, key);
    if (!e) return false;
    e.value.seen = seen;
    e.expires = this.clock() + ttlSec * 1000;
    return true;
  }
  async sadd(key: string, member: string, ttlSec: number) {
    this.check('sadd');
    const e = this.live(this.sets, key) ?? { value: new Set<string>(), expires: 0 };
    e.value.add(member);
    e.expires = Math.max(e.expires, this.clock() + ttlSec * 1000);
    this.sets.set(key, e);
  }
  async srem(key: string, member: string) {
    this.check('srem');
    this.live(this.sets, key)?.value.delete(member);
  }
  async smembers(key: string) {
    this.check('smembers');
    return [...(this.live(this.sets, key)?.value ?? [])];
  }
  async lpush(key: string, value: string, ttlSec: number) {
    this.check('lpush');
    const e = this.live(this.lists, key) ?? { value: [], expires: 0 };
    e.value.unshift(value);
    e.expires = this.clock() + ttlSec * 1000;
    this.lists.set(key, e);
  }
  async slidingWindow(key: string, nowMs: number, windowMs: number, limit: number, member: string): Promise<WindowResult> {
    this.check('slidingWindow');
    const e = this.live(this.windows, key) ?? { value: [], expires: 0 };
    e.value = e.value.filter((h) => h.at > nowMs - windowMs);
    if (e.value.length >= limit) {
      const oldest = e.value[0].at;
      this.windows.set(key, e);
      return { allowed: false, count: e.value.length, retryAfterMs: Math.max(1, oldest + windowMs - nowMs) };
    }
    e.value.push({ at: nowMs, m: member });
    e.expires = this.clock() + windowMs;
    this.windows.set(key, e);
    return { allowed: true, count: e.value.length, retryAfterMs: 0 };
  }
  async ping() {
    this.check('ping');
  }
  async quit() {}
  /** Test helper. */
  rawKeys(): string[] {
    return [...this.strings.keys(), ...this.hashes.keys(), ...this.sets.keys(), ...this.lists.keys(), ...this.windows.keys()];
  }
}
