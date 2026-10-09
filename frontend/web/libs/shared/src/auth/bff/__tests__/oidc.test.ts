import { describe, expect, it } from 'vitest';
import { createOidcClient, OidcError, hasAudience, rolesOf } from '../oidc';
import { createFakeKeycloak, FakeClock } from '../testing';

const ISS = 'http://kc.test/realms/r';
async function setup(over: Record<string, unknown> = {}) {
  const clock = new FakeClock();
  const kc = await createFakeKeycloak({ issuer: ISS, clientId: 'web', clientSecret: 'sec', clock });
  const oidc = createOidcClient({ oidc: { issuer: ISS, clientId: 'web', clientSecret: 'sec', ...over }, appUrl: 'https://app.test', scopes: ['openid', 'profile'], fetchImpl: kc.fetch, clock: clock.now }, { jwks: kc.jwks });
  return { kc, oidc, clock };
}

describe('oidc client', () => {
  it('caches discovery for 10 minutes', async () => {
    const { oidc, clock, kc } = await setup();
    let calls = 0;
    const counting = ((i: Parameters<typeof fetch>[0], n?: RequestInit) => (calls++, kc.fetch(i, n))) as typeof fetch;
    const c = createOidcClient({ oidc: { issuer: ISS, clientId: 'web', clientSecret: 'sec' }, appUrl: 'https://app.test', scopes: ['openid'], fetchImpl: counting, clock: clock.now });
    await c.discover();
    await c.discover();
    expect(calls).toBe(1);
    clock.advance(11 * 60_000);
    await c.discover();
    expect(calls).toBe(2);
    void oidc;
  });
  it('maps internal and public hosts when they differ', async () => {
    const clock = new FakeClock();
    const raw = { issuer: 'https://id.example/realms/r', authorization_endpoint: 'http://kc:8080/realms/r/auth', token_endpoint: 'https://id.example/realms/r/token', jwks_uri: 'https://id.example/realms/r/certs', end_session_endpoint: 'http://kc:8080/realms/r/logout' };
    const c = createOidcClient({ oidc: { issuer: 'https://id.example/realms/r', internalIssuer: 'http://kc:8080/realms/r', clientId: 'w', clientSecret: 's' }, appUrl: 'https://a', scopes: ['openid'], fetchImpl: (async () => new Response(JSON.stringify(raw))) as typeof fetch, clock: clock.now });
    const d = await c.discover();
    expect(d.authorization_endpoint).toBe('https://id.example/realms/r/auth');
    expect(d.end_session_endpoint).toBe('https://id.example/realms/r/logout');
    expect(d.token_endpoint).toBe('http://kc:8080/realms/r/token');
    expect(d.jwks_uri).toBe('http://kc:8080/realms/r/certs');
  });
  it('classifies token errors: invalid_grant / transient / rejected', async () => {
    const { oidc, kc } = await setup();
    await expect(oidc.refresh('unknown-rt')).rejects.toMatchObject({ kind: 'invalid_grant' });
    kc.faults.refresh = ['5xx'];
    await expect(oidc.refresh('x')).rejects.toMatchObject({ kind: 'transient' });
    kc.faults.refresh = ['timeout'];
    await expect(oidc.refresh('x')).rejects.toBeInstanceOf(OidcError);
    const bad = createOidcClient({ oidc: { issuer: ISS, clientId: 'web', clientSecret: 'WRONG' }, appUrl: 'https://app.test', scopes: ['openid'], fetchImpl: kc.fetch, clock: kc.clock.now });
    await expect(bad.refresh('x')).rejects.toMatchObject({ kind: 'rejected', status: 401 });
  });
  it('verifies id tokens: signature, issuer, audience, nonce, azp', async () => {
    const { oidc, kc } = await setup();
    const now = Math.floor(kc.clock.now() / 1000);
    const base = { iss: ISS, aud: 'web', azp: 'web', sub: 'u', iat: now, exp: now + 300, nonce: 'n1' };
    expect((await oidc.verifyIdToken(await kc.sign(base), { nonce: 'n1' })).sub).toBe('u');
    await expect(oidc.verifyIdToken(await kc.sign(base), { nonce: 'other' })).rejects.toThrow(/nonce/);
    await expect(oidc.verifyIdToken(await kc.sign({ ...base, aud: 'x' }), { nonce: 'n1' })).rejects.toThrow();
    await expect(oidc.verifyIdToken(await kc.sign({ ...base, iss: 'http://evil/realms/r' }), { nonce: 'n1' })).rejects.toThrow();
    await expect(oidc.verifyIdToken(await kc.sign({ ...base, azp: 'someone-else' }), { nonce: 'n1' })).rejects.toThrow(/azp/);
    await expect(oidc.verifyIdToken(await kc.sign({ ...base, exp: now - 1000, iat: now - 2000 }), { nonce: 'n1' })).rejects.toThrow();
    await expect(oidc.verifyIdToken(await kc.sign({ ...base, aud: ['web', 'other'], azp: undefined }), { nonce: 'n1' })).rejects.toThrow();
    await expect(oidc.verifyIdToken(await kc.sign(base, await kc.foreignKey()), { nonce: 'n1' })).rejects.toThrow();
  });
  it('builds the end-session URL with hint, client_id, post-logout redirect and state', async () => {
    const { oidc } = await setup();
    const u = new URL((await oidc.endSessionUrl({ idTokenHint: 'IDT', postLogoutRedirect: 'https://app.test/', state: 's' }))!);
    expect(Object.fromEntries(u.searchParams)).toEqual({ client_id: 'web', post_logout_redirect_uri: 'https://app.test/', id_token_hint: 'IDT', state: 's' });
  });
  it('authorize extras (acr_values) and max_age flow through', async () => {
    const { oidc } = await setup({ authorizeParams: { acr_values: 'loa2' } });
    const u = new URL(await oidc.buildAuthorizationUrl({ state: 's', nonce: 'n', challenge: 'c', prompt: 'login', maxAge: 0 }));
    expect(u.searchParams.get('acr_values')).toBe('loa2');
    expect(u.searchParams.get('max_age')).toBe('0');
  });
  it('role and audience helpers', () => {
    expect(rolesOf({ realm_access: { roles: ['A'] }, resource_access: { web: { roles: ['B', 'A'] } } }, 'web').sort()).toEqual(['A', 'B']);
    expect(rolesOf({}, 'web')).toEqual([]);
    expect(hasAudience({ aud: ['x', 'y'] }, 'y')).toBe(true);
    expect(hasAudience({ aud: 'y' }, 'y')).toBe(true);
    expect(hasAudience({}, 'y')).toBe(false);
  });
});
