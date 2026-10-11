import IORedis from 'ioredis';
import { afterAll, afterEach, beforeAll, describe, expect, inject, it } from 'vitest';
import { REVOCATION_CACHE_COMPLETE_KEY, revocationCacheKey } from '../../revocation/keys';
import type { IdentityRevocationClient } from '../../revocation/IdentityRevocationClient';
import { RevocationService } from '../../revocation/RevocationService';
import type { RevocationDecision } from '../../revocation/types';

/**
 * The web side of revocation against a real Redis: what a cache hit, a complete cache's miss, a
 * flushed cache and an unreachable cache each lead to, and what happens when identity-service,
 * the durable store, cannot answer either.
 */
const url = inject('redisUrl');
let admin: IORedis;
const opened: IORedis[] = [];

const connect = (target = url) => {
  const client = new IORedis(target, { maxRetriesPerRequest: 1, retryStrategy: () => null });
  client.on('error', () => undefined);
  opened.push(client);
  return client;
};

/** Identity-service as the service sees it: answers what it is told to, and counts the questions. */
function identity(answer: RevocationDecision | 'DOWN') {
  const state = { asked: 0, signOuts: 0 };
  const client = {
    check: async () => {
      state.asked += 1;
      if (answer === 'DOWN') throw new Error('identity-service unavailable');
      return answer;
    },
    revokeSignOut: async () => {
      state.signOuts += 1;
      return 1;
    },
    ping: async () => undefined,
  } as unknown as IdentityRevocationClient;
  return { client, state };
}

const claims = () => ({ jti: `jti-${Math.random()}`, sid: `sid-${Math.random()}`, sub: `sub-${Math.random()}` });
const quiet = { warn: () => undefined, error: () => undefined, info: () => undefined };

beforeAll(() => {
  admin = connect();
});
afterEach(async () => {
  await admin.flushall();
});
afterAll(async () => {
  await Promise.all(opened.map((c) => c.quit().catch(() => c.disconnect())));
});

describe('RevocationService against a real Redis', () => {
  it('keys revocations by the platform-wide layout the backend reads', () => {
    expect(revocationCacheKey('SESSION', 'abc')).toBe('pml:session:abc');
    expect(revocationCacheKey('TOKEN', 'abc')).toBe('pml:blacklist:abc');
    expect(revocationCacheKey('USER', 'abc')).toBe('pml:revoked:abc');
    expect(REVOCATION_CACHE_COMPLETE_KEY).toBe('pml:revocation:cache-complete');
  });

  it('a sign-out is written durably first and cached for about one access-token lifespan, not an hour', async () => {
    const { client, state } = identity('ACTIVE');
    const service = new RevocationService({ redis: connect(), client, logger: quiet });
    const token = claims();

    const result = await service.revokeSignOut({ ...token, reason: 'user-logout', revokedBy: 'test' } as never);

    expect(result.persisted).toBe(true);
    expect(result.cachePrimed).toBe(true);
    expect(state.signOuts).toBe(1);
    const ttl = await admin.ttl(revocationCacheKey('SESSION', token.sid));
    expect(ttl).toBeGreaterThan(300);
    expect(ttl).toBeLessThanOrEqual(360);
  });

  it('a revoked key in the cache is conclusive: refused without asking identity-service', async () => {
    const { client, state } = identity('ACTIVE');
    const service = new RevocationService({ redis: connect(), client, logger: quiet });
    const token = claims();
    await admin.setex(revocationCacheKey('SESSION', token.sid), 60, 'revoked');

    expect(await service.check(token)).toBe('REVOKED');
    expect(state.asked).toBe(0);
  });

  it('a miss against a complete cache is trusted: active, without asking identity-service', async () => {
    const { client, state } = identity('REVOKED');
    const service = new RevocationService({ redis: connect(), client, logger: quiet });
    await admin.set(REVOCATION_CACHE_COMPLETE_KEY, '1');

    expect(await service.check(claims())).toBe('ACTIVE');
    expect(state.asked).toBe(0);
  });

  it('after a flush the missing marker sends the question to identity-service, so a revoked token is still refused', async () => {
    const { client, state } = identity('REVOKED');
    const service = new RevocationService({ redis: connect(), client, logger: quiet });

    expect(await service.check(claims())).toBe('REVOKED');
    expect(state.asked).toBe(1);
  });

  it('with Redis unreachable, identity-service decides', async () => {
    const dead = connect('redis://127.0.0.1:1');
    expect(await new RevocationService({ redis: dead, client: identity('REVOKED').client, logger: quiet }).check(claims())).toBe('REVOKED');
    expect(await new RevocationService({ redis: dead, client: identity('ACTIVE').client, logger: quiet }).check(claims())).toBe('ACTIVE');
  });

  it('with neither store reachable the answer is UNKNOWN, never ACTIVE', async () => {
    const dead = connect('redis://127.0.0.1:1');
    const service = new RevocationService({ redis: dead, client: identity('DOWN').client, logger: quiet });

    expect(await service.check(claims())).toBe('UNKNOWN');
  });

  it('a revocation that could not be cached withdraws the completeness marker, so other readers stop trusting misses', async () => {
    await admin.set(REVOCATION_CACHE_COMPLETE_KEY, '1');
    const { client } = identity('ACTIVE');
    const flaky = connect();
    // The cache write fails, the marker withdrawal then goes through.
    const pipeline = flaky.pipeline.bind(flaky);
    (flaky as unknown as { pipeline: () => unknown }).pipeline = () => {
      const p = pipeline();
      (p as unknown as { exec: () => Promise<unknown> }).exec = async () => [[new Error('write failed'), null]];
      return p;
    };
    const service = new RevocationService({ redis: flaky, client, logger: quiet });

    const result = await service.revokeSignOut({ ...claims(), reason: 'user-logout', revokedBy: 'test' } as never);

    expect(result.persisted).toBe(true);
    expect(result.cachePrimed).toBe(false);
    expect(await admin.exists(REVOCATION_CACHE_COMPLETE_KEY)).toBe(0);
  });
});
