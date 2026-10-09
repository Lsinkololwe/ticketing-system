import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BffAuthError } from '../require';
import { login, makeHarness } from './harness';

const state = vi.hoisted(() => ({ cookies: {} as Record<string, string>, headers: {} as Record<string, string> }));
vi.mock('next/headers', () => ({
  cookies: async () => ({ get: (n: string) => (state.cookies[n] ? { name: n, value: state.cookies[n] } : undefined) }),
  headers: async () => new Headers(state.headers),
}));
vi.mock('next/navigation', () => ({
  redirect: (url: string) => {
    throw Object.assign(new Error('NEXT_REDIRECT'), { redirectTo: url });
  },
}));

const redirectOf = async (p: Promise<unknown>) => p.then(() => null, (e: { redirectTo?: string }) => e.redirectTo ?? `ERR:${String(e)}`);

describe('requireSession', () => {
  beforeEach(() => {
    state.cookies = {};
    state.headers = { 'x-pml-path': '/dashboard/x?y=1' };
  });

  it('redirects anonymous visitors to login with the current path', async () => {
    const h = await makeHarness();
    expect(await redirectOf(h.bff.requireSession())).toBe('/login?next=%2Fdashboard%2Fx%3Fy%3D1');
  });
  it('returns the public session (no tokens) for a valid cookie', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    state.cookies = jar;
    const s = await h.bff.requireSession({ roles: ['ORGANIZER', 'ADMIN'] });
    expect(s).toMatchObject({ sub: 'kc-user-1', roles: ['ORGANIZER'], displayName: 'Org Admin' });
    expect(JSON.stringify(s)).not.toMatch(/eyJ|accessToken|refreshToken/);
    expect((await h.bff.getSession())?.sub).toBe('kc-user-1');
    expect(await h.bff.getAccessToken()).toMatch(/^eyJ/);
  });
  it('wrong role: redirect (page) or typed error (action)', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    state.cookies = jar;
    expect(await redirectOf(h.bff.requireRole(['SUPER_ADMIN']))).toBe('/unauthorized');
    await expect(h.bff.requireSession({ roles: ['SUPER_ADMIN'], kind: 'action' })).rejects.toMatchObject({ code: 'FORBIDDEN' });
  });
  it('actions throw UNAUTHENTICATED without a session', async () => {
    const h = await makeHarness();
    await expect(h.bff.requireSession({ kind: 'action' })).rejects.toBeInstanceOf(BffAuthError);
    expect(await h.bff.getSession()).toBeNull();
    expect(await h.bff.getAccessToken()).toBeNull();
  });
  it('freshAuthSec: a stale authentication sends the user through step-up, a fresh one passes', async () => {
    const h = await makeHarness();
    const { jar } = await login(h);
    state.cookies = jar;
    await expect(h.bff.requireSession({ freshAuthSec: 300 })).resolves.toBeTruthy();
    h.clock.advanceSec(600);
    state.cookies = jar;
    expect(await redirectOf(h.bff.requireSession({ freshAuthSec: 300 }))).toBe('/api/auth/stepup?next=%2Fdashboard%2Fx%3Fy%3D1&maxAge=300');
    await expect(h.bff.requireSession({ freshAuthSec: 300, kind: 'action' })).rejects.toMatchObject({ code: 'STEP_UP_REQUIRED', stepUpUrl: expect.stringContaining('/api/auth/stepup') });
  });
  it('a hostile x-pml-path can not become an open redirect', async () => {
    const h = await makeHarness();
    state.headers = { 'x-pml-path': '//evil.example' };
    expect(await redirectOf(h.bff.requireSession())).toBe('/login?next=%2F');
  });
});
