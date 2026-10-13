import { describe, expect, it } from 'vitest';
import { resolveConfig } from '../config';
import { parseTrustProxyHops } from '../trust-proxy';
import { createLogger } from '../logger';
import { createRevocationAdapter } from '../revocation';
import { TEST_ENC_KEYS } from '../testing';
import { identityStub, makeHarness } from './harness';

const base = { app: 'admin' as const, appUrl: 'https://admin.example.com', oidc: { issuer: 'https://id.example/realms/a', clientId: 'c', clientSecret: 's' }, encKeys: TEST_ENC_KEYS };
const log = () => createLogger({ app: 'x', level: 'silent' });

describe('config', () => {
  it('applies per-app defaults (admin: Strict, 15 min idle, 8 h absolute)', () => {
    const c = resolveConfig(base, log);
    expect(c).toMatchObject({ sameSite: 'strict', idleSec: 900, absoluteSec: 28800, cookieBase: 'pml_admin', secure: true, refreshSkewSec: 60 });
    expect(resolveConfig({ ...base, app: 'organizer' }, log)).toMatchObject({ sameSite: 'lax', idleSec: 1800 });
    expect(resolveConfig({ ...base, app: 'buyer' }, log)).toMatchObject({ loginPath: '/auth', cookieBase: 'pml_buyer' });
  });
  it('rejects bad input', () => {
    expect(() => resolveConfig({ ...base, appUrl: 'nope' }, log)).toThrow();
    expect(() => resolveConfig({ ...base, appUrl: 'http://admin.example.com', production: true }, log)).toThrow(/https/);
    expect(() => resolveConfig({ ...base, lifetimes: { idleSec: 100, absoluteSec: 50 } }, log)).toThrow(/lifetimes/);
    expect(() => resolveConfig({ ...base, oidc: { ...base.oidc, clientSecret: '' } }, log)).toThrow();
  });
  it('refuses to start in production when TRUST_PROXY_HOPS is unset, and accepts an explicit 0', () => {
    const prod = { ...base, production: true };
    expect(() => resolveConfig(prod, log)).toThrow(/TRUST_PROXY_HOPS/);
    expect(resolveConfig({ ...prod, trustProxyHops: 0 }, log).trustProxyHops).toBe(0);
    expect(resolveConfig({ ...prod, trustProxyHops: 2 }, log).trustProxyHops).toBe(2);
  });
  it('defaults to 0 hops outside production', () => {
    expect(resolveConfig({ ...base, production: false }, log).trustProxyHops).toBe(0);
  });
  it('parses the TRUST_PROXY_HOPS value strictly', () => {
    expect(parseTrustProxyHops(undefined)).toBeUndefined();
    expect(parseTrustProxyHops('  ')).toBeUndefined();
    expect(parseTrustProxyHops('0')).toBe(0);
    expect(parseTrustProxyHops('1')).toBe(1);
    expect(() => parseTrustProxyHops('abc')).toThrow(/TRUST_PROXY_HOPS/);
    expect(() => parseTrustProxyHops('-1')).toThrow();
    expect(() => parseTrustProxyHops('1.5')).toThrow();
  });
  it('strips a trailing slash from appUrl', () => {
    expect(resolveConfig({ ...base, appUrl: 'https://a.example.com/' }, log).appUrl).toBe('https://a.example.com');
  });
});

describe('revocation adapter', () => {
  it('is null without an identity config and otherwise calls the existing IdentityRevocationClient', async () => {
    expect(createRevocationAdapter({ app: 'admin', fetchImpl: fetch })).toBeNull();
    const stub = identityStub();
    const adapter = createRevocationAdapter({ app: 'admin', fetchImpl: stub.fetch, identity: { baseUrl: 'http://identity.test', tokenUrl: 'http://identity.test/token', clientId: 'c', clientSecret: 's' } })!;
    await adapter.revoke({ sid: 'S', jti: 'J', reason: 'user_logout' });
    expect(stub.calls[0]).toEqual({ path: '/api/internal/revocations/logout', body: { jti: 'J', sid: 'S', reason: 'user_logout', revokedBy: 'admin-web' } });
    stub.status = 500;
    await expect(adapter.revoke({ sid: 'S', reason: 'user_logout' })).rejects.toThrow(/NOT been revoked/);
  });
});

describe('createBff', () => {
  it('fails fast in production without Redis and builds lazily otherwise', async () => {
    const { createBff } = await import('../index');
    const bff = createBff({ ...base, production: true, trustProxyHops: 1, appUrl: 'https://a.example.com' });
    await expect(bff.internals()).rejects.toThrow(/REDIS_URL/);
    const h = await makeHarness();
    expect(h.bff.config.cookieBase).toBe('pml_org');
  });
});
