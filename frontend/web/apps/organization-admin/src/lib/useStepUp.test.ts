import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const requireFreshAuth = vi.fn();
vi.mock('./stepup', () => ({ requireFreshAuth: (n: string) => requireFreshAuth(n) }));
import { useStepUp } from './useStepUp';

const assign = vi.fn();
beforeEach(() => {
  requireFreshAuth.mockReset();
  assign.mockReset();
  Object.defineProperty(window, 'location', { value: { pathname: '/finance/bank-accounts', search: '?a=1', assign }, writable: true });
});

describe('useStepUp', () => {
  it('lets a fresh session proceed', async () => {
    requireFreshAuth.mockResolvedValue({ ok: true });
    const { result } = renderHook(() => useStepUp());
    expect(await result.current()).toBe(true);
    expect(requireFreshAuth).toHaveBeenCalledWith('/finance/bank-accounts?a=1');
    expect(assign).not.toHaveBeenCalled();
  });
  it('sends a stale session through Keycloak step-up and blocks the action', async () => {
    requireFreshAuth.mockResolvedValue({ ok: false, reason: 'STEP_UP_REQUIRED', stepUpUrl: '/api/auth/stepup?next=%2Ffinance&maxAge=300' });
    const { result } = renderHook(() => useStepUp());
    expect(await result.current()).toBe(false);
    expect(assign).toHaveBeenCalledWith('/api/auth/stepup?next=%2Ffinance&maxAge=300');
  });
  it('an ended session goes to logout', async () => {
    requireFreshAuth.mockResolvedValue({ ok: false, reason: 'UNAUTHENTICATED' });
    const { result } = renderHook(() => useStepUp());
    expect(await result.current()).toBe(false);
    expect(assign).toHaveBeenCalledWith('/logout');
  });
});
