import { describe, expect, it } from 'vitest';
import { clearCookie, cookieNames, parseCookies, serializeCookie } from '../cookies';

describe('cookies', () => {
  it('uses __Host- on https only', () => {
    expect(cookieNames({ secure: true, cookieBase: 'pml_org' }).session).toBe('__Host-pml_org');
    expect(cookieNames({ secure: true, cookieBase: 'pml_org' }).flow).toBe('__Host-pml_org_f');
    expect(cookieNames({ secure: false, cookieBase: 'pml_org' }).session).toBe('pml_org');
  });
  it('serialises HttpOnly Secure Path=/ without Domain, with the requested SameSite', () => {
    const c = serializeCookie('__Host-pml_admin', 'abc', { maxAge: 100, sameSite: 'strict', secure: true });
    expect(c).toContain('HttpOnly');
    expect(c).toContain('Secure');
    expect(c).toContain('Path=/');
    expect(c).toContain('SameSite=Strict');
    expect(c).toContain('Max-Age=100');
    expect(c).not.toMatch(/Domain/i);
    expect(serializeCookie('a', 'b', { maxAge: 1, sameSite: 'lax', secure: false })).not.toContain('Secure');
  });
  it('refuses values that could inject attributes', () => {
    expect(() => serializeCookie('a', 'x; Domain=evil.com', { maxAge: 1, sameSite: 'lax', secure: true })).toThrow();
  });
  it('clears with Max-Age=0 and parses the first occurrence only', () => {
    expect(clearCookie('a', true)).toContain('Max-Age=0');
    const m = parseCookies('a=1; b=2; a=3');
    expect(m.get('a')).toBe('1');
    expect(m.get('b')).toBe('2');
    expect(parseCookies(null).size).toBe(0);
  });
});
