// @vitest-environment node

/**
 * Integration tests for the organizer app's revocation path against a real Redis container.
 *
 * ## What is real
 *
 * Redis runs in a container and is reached over a real ioredis connection, so the keys written
 * here are the keys a backend service would read. The identity-service side is a local HTTP
 * server implementing the same contract as `InternalRevocationController` — the paths, request
 * bodies and response shapes are pinned to that controller, so a change on either side that
 * breaks the wire format fails here.
 *
 * ## What is asserted
 *
 * The claims that are only observable when a store misbehaves: that a cache outage does not stop
 * enforcement, that a failed durable write is reported rather than reported as success, and that
 * a miss against a cache known to be incomplete is resolved durably.
 */

import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { GenericContainer, type StartedTestContainer } from 'testcontainers';
import Redis from 'ioredis';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';

import {
  FAIL_CLOSED_ORGANIZER_OPERATIONS,
  IdentityRevocationClient,
  REVOCATION_CACHE_COMPLETE_KEY,
  REVOCATION_KEY_PREFIX,
  RevocationService,
  RevocationUnavailableError,
  TokenRevokedError,
  applyDecision,
  assertNotRevoked,
  identifiersFromClaims,
  maskIdentifier,
  revocationCacheKey,
  type RevocationIdentifier,
} from '@pml.tickets/shared/auth/revocation';

// =============================================================================
// IDENTITY-SERVICE CONTRACT DOUBLE
// =============================================================================

type HttpResponse = Parameters<Parameters<typeof createServer>[0]>[1];

/**
 * Implements the endpoints of `InternalRevocationController` over an in-memory store.
 *
 * Deliberately literal about paths and payloads: this is where the frontend's assumptions about
 * the backend contract are recorded.
 */
class IdentityServiceDouble {
  private readonly revocations = new Set<string>();
  private server?: Server;

  /** Set to fail every subsequent call, modelling identity-service being unreachable. */
  public offline = false;

  /** Bearer tokens the client presented, so authorisation can be asserted. */
  public readonly seenAuthorization: (string | undefined)[] = [];

  async start(): Promise<string> {
    this.server = createServer((request, response) => {
      this.seenAuthorization.push(request.headers.authorization);

      if (this.offline) {
        response.writeHead(503).end();
        return;
      }

      const chunks: Buffer[] = [];
      request.on('data', (chunk) => chunks.push(chunk as Buffer));
      request.on('end', () => {
        const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {};

        switch (request.url) {
          case '/api/internal/revocations/logout':
            this.handleLogout(body, response);
            return;
          case '/api/internal/revocations/check':
            this.handleCheck(body, response);
            return;
          case '/api/internal/revocations/health':
            response.writeHead(204).end();
            return;
          default:
            response.writeHead(404).end();
        }
      });
    });

    await new Promise<void>((resolve) => this.server!.listen(0, '127.0.0.1', resolve));
    const { port } = this.server!.address() as AddressInfo;
    return `http://127.0.0.1:${port}`;
  }

  private handleLogout(
    body: { jti?: string; sid?: string; sub?: string },
    response: HttpResponse
  ): void {
    const records: { id: string }[] = [];
    if (body.jti) records.push({ id: this.record('TOKEN', body.jti) });
    if (body.sid) records.push({ id: this.record('SESSION', body.sid) });
    if (body.sub) records.push({ id: this.record('USER', body.sub) });

    response.writeHead(201, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ revoked: true, records }));
  }

  private handleCheck(
    body: { identifiers?: RevocationIdentifier[] },
    response: HttpResponse
  ): void {
    const revoked = (body.identifiers ?? []).some((identifier) =>
      this.revocations.has(`${identifier.type}:${identifier.value}`)
    );

    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ decision: revoked ? 'REVOKED' : 'ACTIVE' }));
  }

  private record(type: string, value: string): string {
    const id = `${type}:${value}`;
    this.revocations.add(id);
    return id;
  }

  has(type: string, value: string): boolean {
    return this.revocations.has(`${type}:${value}`);
  }

  reset(): void {
    this.revocations.clear();
    this.seenAuthorization.length = 0;
    this.offline = false;
  }

  async stop(): Promise<void> {
    await new Promise<void>((resolve) => this.server?.close(() => resolve()));
  }
}

// =============================================================================
// ENVIRONMENT
// =============================================================================

const CONTAINER_STARTUP_MS = 180_000;

let redisContainer: StartedTestContainer;
let redis: Redis;
let identityService: IdentityServiceDouble;
let identityServiceUrl: string;
let service: RevocationService;

/**
 * Brings the connection back after a test disconnected it.
 *
 * Tests cut Redis deliberately, so each one re-establishes it rather than inheriting the previous
 * test's outage. A successful command is the readiness signal: with the offline queue disabled,
 * `status` can read ready a moment before the socket is writable.
 */
async function ensureRedisReady(): Promise<void> {
  const deadline = Date.now() + 15_000;

  while (Date.now() < deadline) {
    try {
      if (redis.status === 'end' || redis.status === 'close') {
        await redis.connect().catch(() => undefined);
      }
      await redis.ping();
      return;
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }

  throw new Error(`Redis did not become ready (status: ${redis.status})`);
}

beforeAll(async () => {
  redisContainer = await new GenericContainer('redis:7-alpine').withExposedPorts(6379).start();

  redis = new Redis({
    host: redisContainer.getHost(),
    port: redisContainer.getMappedPort(6379),
    maxRetriesPerRequest: 1,
    // Fail fast rather than queueing: a test that cuts Redis is asserting the fallback, not
    // ioredis's willingness to wait.
    enableOfflineQueue: false,
  });

  await ensureRedisReady();

  identityService = new IdentityServiceDouble();
  identityServiceUrl = await identityService.start();
}, CONTAINER_STARTUP_MS);

afterAll(async () => {
  redis?.disconnect();
  await identityService?.stop();
  await redisContainer?.stop();
});

beforeEach(async () => {
  identityService.reset();
  await ensureRedisReady();
  await redis.flushdb();

  // identity-service's warmer maintains this marker. An empty cache with no live revocations is
  // a complete one, which is the state a freshly warmed service is in.
  await redis.set(REVOCATION_CACHE_COMPLETE_KEY, '1', 'EX', 600);

  service = new RevocationService({
    redis,
    client: new IdentityRevocationClient({
      baseUrl: identityServiceUrl,
      getAccessToken: async () => 'test-service-token',
      timeoutMs: 2000,
    }),
    cacheTimeoutMs: 500,
    cacheTtlSeconds: 3660,
  });
});

// =============================================================================
// WRITES
// =============================================================================

describe('signing out of the organizer app', () => {
  it('records the revocation durably and mirrors it into the cache', async () => {
    const outcome = await service.revokeSignOut({
      jti: 'jti-1',
      sid: 'sid-1',
      sub: 'user-1',
      reason: 'user_logout',
      revokedBy: 'user-1',
    });

    expect(outcome.persisted).toBe(true);
    expect(outcome.identifiersRevoked).toBe(3);
    expect(outcome.cachePrimed).toBe(true);

    expect(identityService.has('TOKEN', 'jti-1')).toBe(true);
    expect(identityService.has('SESSION', 'sid-1')).toBe(true);
    expect(identityService.has('USER', 'user-1')).toBe(true);
  });

  it('writes the keys the backend services read', async () => {
    await service.revokeSignOut({ jti: 'jti-2', sub: 'user-2', reason: 'user_logout' });

    // The exact keys a Java service checks. An app-namespaced key would leave these unset and
    // the token authorized everywhere outside this app.
    expect(await redis.exists('pml:blacklist:jti-2')).toBe(1);
    expect(await redis.exists('pml:revoked:user-2')).toBe(1);
  });

  it('gives cache entries a TTL that outlives the access token', async () => {
    await service.revokeSignOut({ jti: 'jti-3', reason: 'user_logout' });

    const ttl = await redis.ttl(revocationCacheKey('TOKEN', 'jti-3'));
    expect(ttl).toBeGreaterThan(3600);
  });

  it('authorises the internal call with a service-account token', async () => {
    await service.revokeSignOut({ jti: 'jti-4', reason: 'user_logout' });

    expect(identityService.seenAuthorization).toContain('Bearer test-service-token');
  });

  it('reports failure when the revocation cannot be persisted', async () => {
    identityService.offline = true;

    const outcome = await service.revokeSignOut({ jti: 'jti-5', reason: 'user_logout' });

    // The caller has to be able to tell the user their token is still live.
    expect(outcome.persisted).toBe(false);
    expect(outcome.identifiersRevoked).toBe(0);
    expect(outcome.error).toBeTruthy();
    expect(await redis.exists(revocationCacheKey('TOKEN', 'jti-5'))).toBe(0);
  });

  it('rejects a sign-out that names no identifiers', async () => {
    const outcome = await service.revokeSignOut({ reason: 'user_logout' });

    expect(outcome.persisted).toBe(false);
    expect(outcome.error).toContain('nothing to revoke');
  });
});

// =============================================================================
// READS
// =============================================================================

describe('resolving a token', () => {
  it('reports an unrevoked token as active', async () => {
    await expect(service.check({ jti: 'fresh', sub: 'user-x' })).resolves.toBe('ACTIVE');
  });

  it('reports a revoked token from the cache', async () => {
    await service.revokeSignOut({ jti: 'jti-6', reason: 'user_logout' });

    await expect(service.check({ jti: 'jti-6' })).resolves.toBe('REVOKED');
  });

  it('resolves through identity-service when the cache is unreachable', async () => {
    await service.revokeSignOut({ jti: 'jti-7', reason: 'admin_revoke' });

    redis.disconnect();

    // The durable store still knows, so losing Redis costs latency, not enforcement.
    await expect(service.check({ jti: 'jti-7' })).resolves.toBe('REVOKED');
  });

  it('reports UNKNOWN, never ACTIVE, when neither store can answer', async () => {
    identityService.offline = true;
    redis.disconnect();

    await expect(service.check({ jti: 'jti-8' })).resolves.toBe('UNKNOWN');
  });

  it('reports UNKNOWN for a token carrying no identifiers', async () => {
    await expect(service.check({})).resolves.toBe('UNKNOWN');
  });

  it('does not let a cache that lost a key report the token as active', async () => {
    await service.revokeSignOut({ jti: 'jti-9', reason: 'admin_revoke' });

    // Models an eviction, flush or restart: the key is gone while Redis stays healthy, so the
    // cache would truthfully answer "absent" for a token that is in fact revoked.
    await redis.del(revocationCacheKey('TOKEN', 'jti-9'));
    await redis.del(REVOCATION_CACHE_COMPLETE_KEY);

    await expect(service.check({ jti: 'jti-9' })).resolves.toBe('REVOKED');
  });

  it('withdraws the completeness marker when a revocation misses the cache', async () => {
    redis.disconnect();
    const outcome = await service.revokeSignOut({ jti: 'jti-10', reason: 'user_logout' });

    expect(outcome.persisted).toBe(true);
    expect(outcome.cachePrimed).toBe(false);

    await ensureRedisReady();

    // The durable store still has it, so the check stays correct while the cache is incomplete.
    await expect(service.check({ jti: 'jti-10' })).resolves.toBe('REVOKED');

    // The withdrawal could not be written during the outage, so it is retried on the next check
    // and other readers stop trusting misses too.
    expect(await redis.exists(REVOCATION_CACHE_COMPLETE_KEY)).toBe(0);
  });
});

// =============================================================================
// THE GUARD
// =============================================================================

describe('guarding an organizer application submission', () => {
  it('lets an active token through', async () => {
    await expect(
      assertNotRevoked(service, { jti: 'ok', sub: 'user-1' }, 'organizer.submitForReview')
    ).resolves.toBeUndefined();
  });

  it('refuses a revoked token', async () => {
    await service.revokeSignOut({ sub: 'banned-user', reason: 'account_suspended' });

    await expect(
      assertNotRevoked(service, { sub: 'banned-user' }, 'organizer.submitForReview')
    ).rejects.toBeInstanceOf(TokenRevokedError);
  });

  it('refuses when the revocation state cannot be resolved', async () => {
    identityService.offline = true;
    redis.disconnect();

    await expect(
      assertNotRevoked(service, { jti: 'unknowable' }, 'admin.approveOrganization')
    ).rejects.toBeInstanceOf(RevocationUnavailableError);
  });

  it('does not block while one store is still answering', async () => {
    redis.disconnect();

    await expect(
      assertNotRevoked(service, { jti: 'fine', sub: 'user-2' }, 'organizer.apply')
    ).resolves.toBeUndefined();
  });
});

// =============================================================================
// HEALTH
// =============================================================================

describe('enforceability reporting', () => {
  it('is enforceable while identity-service answers', async () => {
    await expect(service.isEnforceable()).resolves.toBe(true);
  });

  it('is not enforceable once both stores are gone', async () => {
    identityService.offline = true;
    redis.disconnect();

    await expect(service.isEnforceable()).resolves.toBe(false);
  });
});

// =============================================================================
// KEY LAYOUT AND POLICY
// =============================================================================
//
// Decisions with no I/O of their own, kept in this suite so the whole revocation surface runs
// against the same container-backed environment rather than in a separate unit run.

describe('revocation key layout', () => {
  /**
   * Pinned to literals rather than derived from the constants, so a change has to be made
   * deliberately and in step with `TokenBlacklistConstants.java`. The backend reads these exact
   * keys, and a prefix only this app agreed with would revoke nothing.
   */
  it('matches the layout the backend services read', () => {
    expect(REVOCATION_KEY_PREFIX.TOKEN).toBe('pml:blacklist:');
    expect(REVOCATION_KEY_PREFIX.SESSION).toBe('pml:session:');
    expect(REVOCATION_KEY_PREFIX.USER).toBe('pml:revoked:');

    expect(revocationCacheKey('TOKEN', 'abc123')).toBe('pml:blacklist:abc123');
    expect(revocationCacheKey('SESSION', 'sid-9')).toBe('pml:session:sid-9');
    expect(revocationCacheKey('USER', 'user-7')).toBe('pml:revoked:user-7');
  });

  it('is not namespaced by application', () => {
    const key = revocationCacheKey('TOKEN', 'abc123');

    expect(key.startsWith('pml-organizer:')).toBe(false);
    expect(key.startsWith('organization-admin:')).toBe(false);
  });
});

describe('identifiersFromClaims', () => {
  it('covers all three identifiers when the token carries them', () => {
    expect(identifiersFromClaims({ jti: 'j', sid: 's', sub: 'u' })).toEqual([
      { type: 'TOKEN', value: 'j' },
      { type: 'SESSION', value: 's' },
      { type: 'USER', value: 'u' },
    ]);
  });

  it('skips claims the token does not carry', () => {
    expect(identifiersFromClaims({ sub: 'u' })).toEqual([{ type: 'USER', value: 'u' }]);
  });

  it('yields nothing for a token with no identifiers', () => {
    expect(identifiersFromClaims({})).toEqual([]);
  });
});

describe('maskIdentifier', () => {
  it('reveals only the ends of a long identifier', () => {
    expect(maskIdentifier('abcdefghijklmnop')).toBe('abcd...mnop');
  });

  it('fully masks a short identifier', () => {
    expect(maskIdentifier('short')).toBe('****');
    expect(maskIdentifier(undefined)).toBe('****');
  });
});

describe('fail-closed policy', () => {
  it('admits a token that resolved as active', () => {
    expect(() => applyDecision('ACTIVE', 'organizer.submitForReview')).not.toThrow();
  });

  it('refuses a revoked token with 401 so the user is told to sign in again', () => {
    try {
      applyDecision('REVOKED', 'organizer.submitForReview');
      expect.unreachable('a revoked token must not be admitted');
    } catch (error) {
      expect(error).toBeInstanceOf(TokenRevokedError);
      expect((error as TokenRevokedError).status).toBe(401);
      expect((error as TokenRevokedError).operation).toBe('organizer.submitForReview');
    }
  });

  it('refuses an unresolvable token with 503 rather than a permission error', () => {
    try {
      applyDecision('UNKNOWN', 'admin.approveOrganization');
      expect.unreachable('an unverified token must not be admitted to a guarded operation');
    } catch (error) {
      expect(error).toBeInstanceOf(RevocationUnavailableError);
      expect((error as RevocationUnavailableError).status).toBe(503);
    }
  });
});

describe('guarded operation list', () => {
  /**
   * Mirrors the `@FailClosedOnRevocation` annotations on `OrganizationMutationResolver`. The
   * backend has its own reflection test asserting no mutation is left unannotated; this side
   * pins the names so the two tiers can be compared.
   */
  it('covers the whole organizer application lifecycle', () => {
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('organizer.apply');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('organizer.updateApplication');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('organizer.submitForReview');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('organizer.getOrCreateOrganization');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('organizer.upgradeToBusiness');
  });

  it('covers the admin decisions on an application', () => {
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('admin.approveOrganization');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('admin.rejectOrganization');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('admin.requestOrganizationChanges');
    expect(FAIL_CLOSED_ORGANIZER_OPERATIONS).toContain('admin.suspendOrganization');
  });
});
