import { describe, expect, it } from 'vitest';
import { assertNotCrossSite, assertSameOrigin } from '../csrf';
import { buildCsp, newNonce, securityHeaders } from '../csp';
import { safeReturnTo } from '../guards';

const O = 'https://app.example.com';
const post = (h: Record<string, string>) => new Request(O + '/api/x', { method: 'POST', headers: h });

describe('csrf', () => {
  const ok = { origin: O, 'sec-fetch-site': 'same-origin', 'x-pml-csrf': '1' };
  it.each([
    ['all good', ok, null],
    ['missing origin', { ...ok, origin: '' }, 'METHOD_UNSAFE_ORIGIN_MISSING'],
    ['foreign origin', { ...ok, origin: 'https://evil.example' }, 'ORIGIN_MISMATCH'],
    ['same-site but not same-origin', { ...ok, 'sec-fetch-site': 'same-site' }, 'FETCH_SITE'],
    ['cross-site', { ...ok, 'sec-fetch-site': 'cross-site' }, 'FETCH_SITE'],
    ['no csrf header', { origin: O, 'sec-fetch-site': 'same-origin' }, 'CSRF_HEADER'],
    ['header not 1', { ...ok, 'x-pml-csrf': 'true' }, 'CSRF_HEADER'],
  ])('%s', (_n, h, expected) => {
    const headers = Object.fromEntries(Object.entries(h).filter(([, v]) => v !== ''));
    expect(assertSameOrigin(post(headers), O)).toBe(expected);
  });
  it('lets safe methods through and can skip the header for form posts', () => {
    expect(assertSameOrigin(new Request(O + '/x'), O)).toBeNull();
    expect(assertSameOrigin(post({ origin: O, 'sec-fetch-site': 'same-origin' }), O, { requireHeader: false })).toBeNull();
  });
  it('navigation guard refuses cross-site only', () => {
    expect(assertNotCrossSite(new Request(O, { headers: { 'sec-fetch-site': 'cross-site' } }))).toBe(false);
    expect(assertNotCrossSite(new Request(O, { headers: { 'sec-fetch-site': 'none' } }))).toBe(true);
    expect(assertNotCrossSite(new Request(O))).toBe(true);
  });
});

describe('csp', () => {
  it('has a nonce, strict-dynamic and the locked-down directives', () => {
    const csp = buildCsp('N0NCE');
    expect(csp).toContain(`script-src 'self' 'nonce-N0NCE' 'strict-dynamic'`);
    for (const d of [`object-src 'none'`, `base-uri 'self'`, `frame-ancestors 'none'`, `form-action 'self'`, `connect-src 'self'`, 'upgrade-insecure-requests']) expect(csp).toContain(d);
    expect(csp).not.toContain("'unsafe-eval'");
    expect(csp.match(/script-src[^;]*/)![0]).not.toContain('unsafe-inline');
  });
  it('dev adds eval + localhost, nonce styles when inline is disabled, extras', () => {
    const dev = buildCsp('n', { isDev: true });
    expect(dev).toContain("'unsafe-eval'");
    expect(dev).not.toContain('upgrade-insecure-requests');
    expect(buildCsp('n', { styleUnsafeInline: false })).toContain(`style-src 'self' 'nonce-n'`);
    expect(buildCsp('n', { connectSrc: ['https://kc.example'] })).toContain(`connect-src 'self' https://kc.example`);
  });
  it('nonces are unique and headers drop X-XSS-Protection', () => {
    expect(newNonce()).not.toBe(newNonce());
    const h = securityHeaders({ noStore: true });
    expect(h['x-xss-protection']).toBeUndefined();
    expect(h['strict-transport-security']).toBeDefined();
    expect(h['cache-control']).toBe('no-store');
    expect(securityHeaders({ isDev: true })['strict-transport-security']).toBeUndefined();
  });
});

describe('safeReturnTo (open redirect)', () => {
  it.each([
    ['/dashboard', '/dashboard'],
    ['/events?id=1#top', '/events?id=1#top'],
    ['/', '/'],
    ['//evil.com', '/'],
    ['/\\evil.com', '/'],
    ['\\\\evil.com', '/'],
    ['https://evil.com', '/'],
    ['javascript:alert(1)', '/'],
    ['/%2F%2Fevil.com', '/'],
    ['/%5Cevil.com', '/'],
    ['/a\r\nSet-Cookie: x=1', '/'],
    ['/a%0d%0aSet-Cookie:x', '/'],
    ['/api/auth/logout', '/'],
    ['/API/graphql', '/'],
    ['/%2561pi/x', '/%2561pi/x'],
    ['', '/'],
    [null, '/'],
    [undefined, '/'],
    ['dashboard', '/'],
    ['/\t/evil.com', '/'],
    ['/%', '/'],
  ])('%j -> %j', (input, expected) => {
    expect(safeReturnTo(input as string | null)).toBe(expected);
  });
  it('supports a custom fallback', () => {
    expect(safeReturnTo('//x', '/home')).toBe('/home');
  });

  it('lets development load pictures from the local media service, and production only https', () => {
    expect(buildCsp('n', { isDev: true })).toMatch(/img-src [^;]*http:\/\/localhost:\*/);
    expect(buildCsp('n', { isDev: false })).not.toMatch(/img-src [^;]*http:\/\/localhost/);
  });
});
