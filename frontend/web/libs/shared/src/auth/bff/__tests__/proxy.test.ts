import { NextRequest } from 'next/server';
import { describe, expect, it } from 'vitest';
import { APP, login, makeHarness } from './harness';

const nreq = (path: string, init: { method?: string; cookies?: Record<string, string>; headers?: Record<string, string> } = {}) => {
  const headers = new Headers(init.headers);
  if (init.cookies) headers.set('cookie', Object.entries(init.cookies).map(([k, v]) => `${k}=${v}`).join('; '));
  return new NextRequest(APP + path, { method: init.method ?? 'GET', headers });
};
const guarded = { guarded: [{ prefix: '/dashboard', roles: ['ORGANIZER'] }, { prefix: '/profile' }, { prefix: '/admin-only', roles: ['SUPER'] }], publicPaths: ['/dashboard/public'] };

describe('proxy', () => {
  it('sets a per-request nonce CSP on the request (for Next) and response, plus security headers', async () => {
    const h = await makeHarness({ production: true });
    const a = await h.bff.proxy(nreq('/'));
    const b = await h.bff.proxy(nreq('/'));
    const csp = a.headers.get('content-security-policy')!;
    const nonce = /'nonce-([^']+)'/.exec(csp)![1];
    expect(a.headers.get('x-middleware-request-x-nonce')).toBe(nonce);
    expect(a.headers.get('x-middleware-request-content-security-policy')).toBe(csp);
    expect(b.headers.get('content-security-policy')).not.toBe(csp);
    expect(a.headers.get('x-content-type-options')).toBe('nosniff');
    expect(a.headers.get('strict-transport-security')).toBeTruthy();
    expect(a.headers.get('x-xss-protection')).toBeNull();
  });
  it('anonymous visitors to a guarded prefix go to login with a safe next', async () => {
    const h = await makeHarness(guarded);
    const res = await h.bff.proxy(nreq('/dashboard/events?x=1'));
    expect(res.status).toBe(303);
    const u = new URL(res.headers.get('location')!);
    expect(u.pathname).toBe('/login');
    expect(u.searchParams.get('next')).toBe('/dashboard/events?x=1');
    expect(res.headers.get('cache-control')).toBe('no-store');
  });
  it('a forged / unknown cookie does not pass (the store is consulted, not mere presence)', async () => {
    const h = await makeHarness(guarded);
    const res = await h.bff.proxy(nreq('/dashboard', { cookies: { '__Host-pml_org': 'x'.repeat(43) } }));
    expect(res.status).toBe(303);
  });
  it('lets a valid session through and rejects the wrong role', async () => {
    const h = await makeHarness(guarded);
    const { jar } = await login(h);
    expect((await h.bff.proxy(nreq('/dashboard', { cookies: jar }))).status).toBe(200);
    expect((await h.bff.proxy(nreq('/profile', { cookies: jar }))).status).toBe(200);
    const wrong = await h.bff.proxy(nreq('/admin-only/x', { cookies: jar }));
    expect(wrong.status).toBe(303);
    expect(new URL(wrong.headers.get('location')!).pathname).toBe('/unauthorized');
  });
  it('prefix matching is segment based and public paths bypass the gate', async () => {
    const h = await makeHarness(guarded);
    expect((await h.bff.proxy(nreq('/dashboard-public'))).status).toBe(200);
    expect((await h.bff.proxy(nreq('/dashboard/public/x'))).status).toBe(200);
  });
  it('the idle clock is not slid by the proxy check', async () => {
    const h = await makeHarness(guarded);
    const { jar } = await login(h);
    h.clock.advanceSec(1500);
    expect((await h.bff.proxy(nreq('/dashboard', { cookies: jar }))).status).toBe(200);
    h.clock.advanceSec(400);
    expect((await h.bff.proxy(nreq('/dashboard', { cookies: jar }))).status).toBe(303);
  });
  it('unsafe page requests need a same-origin Origin (server actions)', async () => {
    const h = await makeHarness();
    expect((await h.bff.proxy(nreq('/x', { method: 'POST', headers: { origin: 'https://evil.example' } }))).status).toBe(403);
    expect((await h.bff.proxy(nreq('/x', { method: 'POST' }))).status).toBe(403);
    expect((await h.bff.proxy(nreq('/x', { method: 'POST', headers: { origin: APP } }))).status).toBe(200);
  });
  it('a session-store outage answers 503 rather than letting guarded pages through', async () => {
    const h = await makeHarness(guarded);
    const { jar } = await login(h);
    (h.store as import('../store').MemoryStore).failNext = () => true;
    expect((await h.bff.proxy(nreq('/dashboard', { cookies: jar }))).status).toBe(503);
  });
});
