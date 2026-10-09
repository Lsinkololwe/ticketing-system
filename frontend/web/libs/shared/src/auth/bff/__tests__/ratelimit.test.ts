import { describe, expect, it } from 'vitest';
import { silentLogger } from '../logger';
import { RateLimiter, defaultPolicies, resolveClientIp } from '../ratelimit';
import { MemoryStore } from '../store';
import { FakeClock } from '../testing';

function make(clock = new FakeClock(), overrides?: ConstructorParameters<typeof RateLimiter>[1]['overrides'], store = new MemoryStore(clock.now)) {
  return { clock, store, rl: new RateLimiter(store, { app: 'organizer', prefix: 'p:', secret: Buffer.alloc(32, 1), clock: clock.now, overrides, logger: silentLogger }) };
}

describe('resolveClientIp (trusted hops from the right)', () => {
  const h = (v?: string) => new Headers(v ? { 'x-forwarded-for': v } : {});
  it('ignores XFF with zero trusted hops', () => expect(resolveClientIp(h('1.1.1.1'), 0)).toBe('unknown'));
  it('takes the rightmost entry for one hop, ignoring a spoofed leftmost', () => {
    expect(resolveClientIp(h('6.6.6.6, 203.0.113.7'), 1)).toBe('203.0.113.7');
  });
  it('takes the entry N hops from the right', () => {
    expect(resolveClientIp(h('6.6.6.6, 203.0.113.7, 10.0.0.1'), 2)).toBe('203.0.113.7');
  });
  it('unknown when the chain is shorter than the hops or malformed', () => {
    expect(resolveClientIp(h('1.1.1.1'), 2)).toBe('unknown');
    expect(resolveClientIp(h(), 1)).toBe('unknown');
    expect(resolveClientIp(h('<script>'), 1)).toBe('unknown');
  });
});

describe('sliding window limiter', () => {
  const rules = { start: [{ subject: 'ip' as const, limit: 3, windowSec: 60 }] };
  it('allows up to the limit then refuses with Retry-After', async () => {
    const { rl, clock } = make(undefined, rules);
    for (let i = 0; i < 3; i++) expect((await rl.consume('start', { ip: '1.1.1.1' })).allowed).toBe(true);
    const d = await rl.consume('start', { ip: '1.1.1.1' });
    expect(d.allowed).toBe(false);
    expect(d.retryAfterSec).toBeGreaterThan(0);
    expect(d.retryAfterSec).toBeLessThanOrEqual(60);
    clock.advanceSec(61);
    expect((await rl.consume('start', { ip: '1.1.1.1' })).allowed).toBe(true);
  });
  it('has no 2x burst at the window boundary (a fixed window would allow 6)', async () => {
    const { rl, clock } = make(undefined, rules);
    clock.advanceSec(55);
    let allowed = 0;
    for (let i = 0; i < 3; i++) allowed += (await rl.consume('start', { ip: 'a' })).allowed ? 1 : 0;
    clock.advanceSec(10); // crosses a fixed-window boundary, still inside the sliding window
    for (let i = 0; i < 3; i++) allowed += (await rl.consume('start', { ip: 'a' })).allowed ? 1 : 0;
    expect(allowed).toBe(3);
  });
  it('keeps subjects independent and skips rules whose subject is absent', async () => {
    const { rl } = make(undefined, rules);
    for (let i = 0; i < 3; i++) await rl.consume('start', { ip: 'a' });
    expect((await rl.consume('start', { ip: 'b' })).allowed).toBe(true);
    expect((await rl.consume('start', {})).allowed).toBe(true);
  });
  it('applies a penalty block after a breach', async () => {
    const { rl, clock } = make(undefined, { start: [{ subject: 'ip', limit: 1, windowSec: 10, penaltySec: 300 }] });
    await rl.consume('start', { ip: 'x' });
    expect((await rl.consume('start', { ip: 'x' })).allowed).toBe(false);
    clock.advanceSec(20); // window is clear but the penalty holds
    const d = await rl.consume('start', { ip: 'x' });
    expect(d.allowed).toBe(false);
    expect(d.retryAfterSec).toBe(300);
  });
  it('blockAtLimit counts failures and blocks at the Nth', async () => {
    const { rl } = make();
    for (let i = 0; i < 5; i++) await rl.consume('callback_fail', { ip: 'z' });
    expect(await rl.isBlocked('callback_fail', { ip: 'z' })).toBe(900);
    expect(await rl.isBlocked('callback_fail', { ip: 'other' })).toBe(0);
  });
  it('multiple rules: the contact bucket trips before the ip bucket', async () => {
    const { rl } = make();
    const res = [];
    for (let i = 0; i < 6; i++) res.push(await rl.consume('challenge', { ip: 'i', contact: '+260971111111' }));
    expect(res.slice(0, 5).every((r) => r.allowed)).toBe(true);
    expect(res[5]).toMatchObject({ allowed: false, rule: 'contact' });
  });
  it('never stores the raw subject in a key', async () => {
    const { rl, store } = make();
    await rl.consume('challenge', { ip: '198.51.100.7', contact: '+260971234567' });
    expect(store.rawKeys().join('|')).not.toMatch(/198\.51|260971/);
  });
  it('unknown policy is a programming error', async () => {
    await expect(make().rl.consume('nope', { ip: 'a' })).rejects.toThrow(/unknown/);
  });
  it('admin start policy is tighter', () => {
    expect(defaultPolicies('admin').start.rules[0].limit).toBe(10);
    expect(defaultPolicies('buyer').start.rules[0].limit).toBe(20);
  });

  describe('store outage', () => {
    it('auth routes fail closed: 5 per minute from memory, then 503', async () => {
      const { rl, store } = make();
      store.failNext = () => true;
      const out = [];
      for (let i = 0; i < 7; i++) out.push(await rl.consume('start', { ip: 'a' }));
      expect(out.filter((d) => d.allowed)).toHaveLength(5);
      expect(out[6]).toMatchObject({ allowed: false, unavailable: true });
    });
    it('the API proxy fails open with a per-process limit', async () => {
      const { rl, store } = make(undefined, { api: [{ subject: 'session', limit: 3, windowSec: 60 }] });
      store.failNext = () => true;
      const out = [];
      for (let i = 0; i < 4; i++) out.push(await rl.consume('api', { session: 's' }));
      expect(out.map((d) => d.allowed)).toEqual([true, true, true, false]);
      expect(out[3].unavailable).toBeFalsy();
    });
  });
});
