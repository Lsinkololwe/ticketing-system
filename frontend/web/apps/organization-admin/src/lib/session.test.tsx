import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { signInUrl, useSession } from './session';

afterEach(() => vi.unstubAllGlobals());

describe('useSession (BFF session, no tokens)', () => {
  it('maps the public session', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ authenticated: true, accountId: 'u1', displayName: 'Olive', roles: ['ORGANIZER'] }))));
    const { result } = renderHook(() => useSession());
    expect(result.current.isPending).toBe(true);
    await waitFor(() => expect(result.current.isPending).toBe(false));
    expect(result.current.data?.user).toEqual({ id: 'u1', name: 'Olive', roles: ['ORGANIZER'] });
    expect(fetch).toHaveBeenCalledWith('/api/auth/session', expect.objectContaining({ credentials: 'same-origin' }));
  });
  it('signed out and failures resolve to null data', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ authenticated: false }))));
    const a = renderHook(() => useSession());
    await waitFor(() => expect(a.result.current.isPending).toBe(false));
    expect(a.result.current.data).toBeNull();
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline'); }));
    const b = renderHook(() => useSession());
    await waitFor(() => expect(b.result.current.isPending).toBe(false));
    expect(b.result.current.data).toBeNull();
  });
  it('builds the BFF start URL', () => {
    expect(signInUrl('/finance?x=1')).toBe('/api/auth/start?next=%2Ffinance%3Fx%3D1');
  });
});
