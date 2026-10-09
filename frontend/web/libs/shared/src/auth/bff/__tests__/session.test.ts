import { beforeEach, describe, expect, it } from 'vitest';
import { silentLogger } from '../logger';
import { SessionService, type SessionRecord } from '../session';
import { MemoryStore } from '../store';
import { FakeClock, TEST_ENC_KEYS } from '../testing';

const rec = (over: Partial<SessionRecord> = {}): SessionRecord => ({
  v: 1, accountId: 'acc-1', kcSid: 'sid-1', sub: 'sub-1', roles: ['ADMIN'], aud: ['api'], displayName: 'A',
  accessToken: 'plain-access-token-XYZ', refreshToken: 'plain-refresh-token-XYZ', idToken: 'plain-id-token-XYZ', atExp: 0, rtVer: 0, authTime: 1, acr: null, createdAt: 0, ext: {}, ...over,
});

describe('SessionService', () => {
  let clock: FakeClock;
  let store: MemoryStore;
  let s: SessionService;
  beforeEach(() => {
    clock = new FakeClock();
    store = new MemoryStore(clock.now);
    s = new SessionService({ app: 'admin', idleSec: 900, absoluteSec: 28800, clock: clock.now, logger: silentLogger }, store, TEST_ENC_KEYS);
  });

  it('stores only hashed keys and sealed values (no plaintext tokens, cookie not a key)', async () => {
    const { cookieValue } = await s.create(rec());
    const keys = store.rawKeys().join('\n');
    expect(keys).not.toContain(cookieValue);
    const row = (await store.getSession(store.rawKeys().find((k) => k.includes(':sess:'))!))!;
    expect(row.data).not.toContain('plain-');
    expect(row.data.startsWith('v1.k1.')).toBe(true);
  });

  it('round-trips and indexes by sid and sub', async () => {
    const { cookieValue, hash } = await s.create(rec());
    const loaded = await s.load(cookieValue);
    expect(loaded?.record.accessToken).toBe('plain-access-token-XYZ');
    expect(await store.smembers('bff:admin:sid:sid-1')).toEqual([hash]);
    expect(await store.smembers('bff:admin:sub:sub-1')).toEqual([hash]);
  });

  it('rejects garbage and unknown cookies', async () => {
    expect(await s.load(undefined)).toBeNull();
    expect(await s.load('short')).toBeNull();
    expect(await s.load('x'.repeat(43))).toBeNull();
  });

  it('idle timeout: 15 min of inactivity ends the session; activity slides it', async () => {
    const { cookieValue } = await s.create(rec());
    clock.advanceSec(14 * 60);
    expect(await s.load(cookieValue)).not.toBeNull(); // touches (>= 60 s since last)
    clock.advanceSec(14 * 60);
    expect(await s.load(cookieValue)).not.toBeNull(); // still alive 28 min after creation
    clock.advanceSec(15 * 60 + 1);
    expect(await s.load(cookieValue)).toBeNull();
  });

  it('absolute timeout: continuous activity cannot outlive 8 h', async () => {
    const { cookieValue } = await s.create(rec());
    for (let i = 0; i < 31; i++) {
      clock.advanceSec(14 * 60);
      expect(await s.load(cookieValue)).not.toBeNull();
    }
    // 31 * 14 min = 7 h 14 min; keep going to the cap
    for (let i = 0; i < 6; i++) clock.advanceSec(14 * 60);
    expect(await s.load(cookieValue)).toBeNull();
  });

  it('touch is throttled to once a minute', async () => {
    const { cookieValue } = await s.create(rec());
    const a = await s.load(cookieValue);
    clock.advanceSec(30);
    const b = await s.load(cookieValue);
    expect(b!.seen).toBe(a!.seen);
    clock.advanceSec(31);
    const c = await s.load(cookieValue);
    expect(c!.seen).toBeGreaterThan(a!.seen);
  });

  it('light load never slides the idle clock', async () => {
    const { cookieValue } = await s.create(rec());
    clock.advanceSec(10 * 60);
    await s.loadLight(cookieValue);
    clock.advanceSec(6 * 60);
    expect(await s.load(cookieValue)).toBeNull();
  });

  it('rotation: create(replacing) removes the old session and its index entries', async () => {
    const first = await s.create(rec());
    const second = await s.create(rec(), first.cookieValue);
    expect(await s.load(first.cookieValue)).toBeNull();
    expect(await s.load(second.cookieValue)).not.toBeNull();
    expect(await store.smembers('bff:admin:sid:sid-1')).toEqual([second.hash]);
  });

  it('destroyBySid removes every session of that sid and only those', async () => {
    const a = await s.create(rec());
    const b = await s.create(rec());
    const other = await s.create(rec({ kcSid: 'sid-2' }));
    expect(await s.destroyBySid('sid-1')).toBe(2);
    expect(await s.load(a.cookieValue)).toBeNull();
    expect(await s.load(b.cookieValue)).toBeNull();
    expect(await s.load(other.cookieValue)).not.toBeNull();
  });

  it('destroyBySub removes sessions of the user across sids', async () => {
    const a = await s.create(rec());
    const b = await s.create(rec({ kcSid: 'sid-9' }));
    expect(await s.destroyBySub('sub-1')).toBe(2);
    expect(await s.load(a.cookieValue)).toBeNull();
    expect(await s.load(b.cookieValue)).toBeNull();
  });

  it('CAS only succeeds against the version that was read', async () => {
    const { cookieValue } = await s.create(rec());
    const one = (await s.load(cookieValue))!;
    expect(await s.cas(one, { ...one.record, rtVer: 1 })).toBe(true);
    expect(await s.cas(one, { ...one.record, rtVer: 2 })).toBe(false); // stale ver
    expect((await s.load(cookieValue))!.record.rtVer).toBe(1);
  });

  it('a record sealed for another key cannot be loaded under a different hash (AAD)', async () => {
    const a = await s.create(rec({ accessToken: 'A-TOKEN' }));
    const b = await s.create(rec());
    const aKey = store.rawKeys().find((k) => k.endsWith(a.hash))!;
    const bKey = store.rawKeys().find((k) => k.endsWith(b.hash))!;
    const rowA = (await store.getSession(aKey))!;
    await store.putSession(bKey, { ...rowA }, 100);
    expect(await s.load(b.cookieValue)).toBeNull();
  });

  it('sealValue/openValue are purpose-bound', () => {
    const sealed = s.sealValue('flow', 'id1', { a: 1 });
    expect(s.openValue('flow', 'id1', sealed)).toEqual({ a: 1 });
    expect(s.openValue('hint', 'id1', sealed)).toBeNull();
    expect(s.openValue('flow', 'id2', sealed)).toBeNull();
  });
});
