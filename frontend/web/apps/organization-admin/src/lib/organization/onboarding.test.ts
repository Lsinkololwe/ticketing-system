import { describe, expect, it, vi } from 'vitest';
import { resolveOnboardingState, toAuthStatus } from './onboarding';

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const run = (fetchImpl: typeof fetch, token: string | null = 'tok') =>
  resolveOnboardingState({ getAccessToken: async () => token, graphqlUrl: 'http://up.test/graphql', fetchImpl });

describe('resolveOnboardingState', () => {
  it('sends the server-side bearer and maps a known status', async () => {
    const f = vi.fn(async () => json({ data: { myOwnedOrganization: { id: 'o1', name: 'Fixture Org', status: 'PENDING_REVIEW' } } }));
    const s = await run(f as unknown as typeof fetch);
    expect(s).toMatchObject({ kind: 'org', status: 'PENDING_REVIEW', id: 'o1' });
    const init = (f.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tok');
  });
  it('a non-owner staff member resolves the organization through their membership', async () => {
    const s = await run((async () => json({ data: { myOwnedOrganization: null, myOrganization: { id: 'o9', name: 'Member Org', status: 'ACTIVE' } } })) as typeof fetch);
    expect(s).toMatchObject({ kind: 'org', status: 'ACTIVE', id: 'o9' });
  });
  it('null organization is the only genuine "none"', async () => {
    expect((await run((async () => json({ data: { myOwnedOrganization: null } })) as typeof fetch)).kind).toBe('none');
  });
  it.each([
    ['no token', async () => json({}), null],
    ['http error', async () => json({}, 503), 'tok'],
    ['graphql errors', async () => json({ errors: [{ message: 'denied' }] }), 'tok'],
    ['missing data key', async () => json({}), 'tok'],
    ['unknown status', async () => json({ data: { myOwnedOrganization: { id: 'o', name: 'n', status: 'WAT' } } }), 'tok'],
    ['network failure', async () => { throw new Error('down'); }, 'tok'],
  ])('%s is unknown, never none', async (_n, impl, token) => {
    expect((await run(impl as unknown as typeof fetch, token)).kind).toBe('unknown');
  });
  it('maps lifecycle booleans', () => {
    expect(toAuthStatus({ kind: 'org', status: 'ACTIVE', id: 'o', name: null } as never)).toMatchObject({ hasOrganization: true, isApproved: true });
    expect(toAuthStatus({ kind: 'unknown', reason: 'x' } as never).hasOrganization).toBe(false);
  });
});
