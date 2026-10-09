import type { Redis } from 'ioredis';
import type { KeyValueStore, SessionHash, WindowResult } from './types';

const LUA_DEL_IF_EQUALS = `
if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end`;

const LUA_CAS_SESSION = `
local ver = redis.call('HGET', KEYS[1], 'ver')
if not ver or ver ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'data', ARGV[2], 'ver', tostring(tonumber(ver) + 1))
redis.call('EXPIRE', KEYS[1], ARGV[3])
return 1`;

const LUA_TOUCH_SESSION = `
if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
redis.call('HSET', KEYS[1], 'seen', ARGV[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
return 1`;

const LUA_SADD_EXPIRE = `
redis.call('SADD', KEYS[1], ARGV[1])
local cur = redis.call('TTL', KEYS[1])
if cur < tonumber(ARGV[2]) then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
return 1`;

const LUA_SLIDING = `
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count >= limit then
  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
  local retry = window
  if oldest[2] then retry = math.max(1, tonumber(oldest[2]) + window - now) end
  return {0, count, retry}
end
redis.call('ZADD', key, now, ARGV[4])
redis.call('PEXPIRE', key, window)
return {1, count + 1, 0}`;

/** Atomic Redis implementation. Every multi-step operation is a Lua script. */
export class RedisStore implements KeyValueStore {
  constructor(private readonly client: Redis) {}

  get(key: string) {
    return this.client.get(key);
  }
  async set(key: string, value: string, ttlSec: number) {
    await this.client.set(key, value, 'EX', Math.max(1, Math.floor(ttlSec)));
  }
  async setNx(key: string, value: string, ttlMs: number) {
    return (await this.client.set(key, value, 'PX', Math.max(1, Math.floor(ttlMs)), 'NX')) === 'OK';
  }
  getDel(key: string) {
    return this.client.getdel(key);
  }
  async del(...keys: string[]) {
    if (keys.length) await this.client.del(...keys);
  }
  async delIfEquals(key: string, expected: string) {
    return Number(await this.client.eval(LUA_DEL_IF_EQUALS, 1, key, expected)) === 1;
  }
  async putSession(key: string, rec: SessionHash, ttlSec: number) {
    await this.client
      .multi()
      .del(key)
      .hset(key, { data: rec.data, ver: String(rec.ver), created: String(rec.created), seen: String(rec.seen) })
      .expire(key, Math.max(1, Math.floor(ttlSec)))
      .exec();
  }
  async getSession(key: string): Promise<SessionHash | null> {
    const h = await this.client.hgetall(key);
    if (!h || !h.data) return null;
    return { data: h.data, ver: Number(h.ver), created: Number(h.created), seen: Number(h.seen) };
  }
  async casSession(key: string, expectedVer: number, data: string, ttlSec: number) {
    return (
      Number(await this.client.eval(LUA_CAS_SESSION, 1, key, String(expectedVer), data, String(Math.max(1, Math.floor(ttlSec))))) === 1
    );
  }
  async touchSession(key: string, seen: number, ttlSec: number) {
    return Number(await this.client.eval(LUA_TOUCH_SESSION, 1, key, String(seen), String(Math.max(1, Math.floor(ttlSec))))) === 1;
  }
  async sadd(key: string, member: string, ttlSec: number) {
    await this.client.eval(LUA_SADD_EXPIRE, 1, key, member, String(Math.max(1, Math.floor(ttlSec))));
  }
  async srem(key: string, member: string) {
    await this.client.srem(key, member);
  }
  smembers(key: string) {
    return this.client.smembers(key);
  }
  async lpush(key: string, value: string, ttlSec: number) {
    await this.client.multi().lpush(key, value).ltrim(key, 0, 999).expire(key, Math.max(1, Math.floor(ttlSec))).exec();
  }
  async slidingWindow(key: string, nowMs: number, windowMs: number, limit: number, member: string): Promise<WindowResult> {
    const r = (await this.client.eval(LUA_SLIDING, 1, key, String(nowMs), String(windowMs), String(limit), member)) as number[];
    return { allowed: Number(r[0]) === 1, count: Number(r[1]), retryAfterMs: Number(r[2]) };
  }
  async ping() {
    await this.client.ping();
  }
  async quit() {
    await this.client.quit();
  }
}

export async function createRedisStore(url: string): Promise<RedisStore> {
  const { default: IORedis } = await import('ioredis');
  const client = new IORedis(url, { maxRetriesPerRequest: 2 });
  client.on('error', () => undefined); // surfaced per command; avoids unhandled 'error' events
  return new RedisStore(client);
}
