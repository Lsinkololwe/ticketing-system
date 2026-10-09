import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { checkFreshAuth } from '@/lib/stepUpAction';
import { useStepUp } from '@/lib/useStepUp';
import { signOut } from '@/components/auth/SignOutForm';

const check = vi.mocked(checkFreshAuth);
const assign = vi.fn();
const original = window.location;

beforeEach(() => {
  vi.clearAllMocks();
  Object.defineProperty(window, 'location', { configurable: true, value: { pathname: '/finance/payouts', search: '?q=1', assign } });
});
afterEach(() => Object.defineProperty(window, 'location', { configurable: true, value: original }));

describe('useStepUp', () => {
  it('runs the action when the sign-in is recent', async () => {
    check.mockResolvedValueOnce({ fresh: true });
    const { result } = renderHook(() => useStepUp());
    const fn = vi.fn().mockResolvedValue('done');
    await expect(result.current.guard(fn)).resolves.toBe('done');
    expect(check).toHaveBeenCalledWith('/finance/payouts?q=1');
    expect(assign).not.toHaveBeenCalled();
  });
  it('sends the browser to step-up and never runs the action when the sign-in is stale', async () => {
    check.mockResolvedValueOnce({ fresh: false, url: '/api/auth/stepup?next=%2Ffinance&maxAge=300' });
    const { result } = renderHook(() => useStepUp());
    const fn = vi.fn();
    const settled = vi.fn();
    void result.current.guard(fn).then(settled);
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('/api/auth/stepup?next=%2Ffinance&maxAge=300'));
    expect(fn).not.toHaveBeenCalled();
    expect(settled).not.toHaveBeenCalled();
  });
});

describe('signOut', () => {
  it('submits a POST form to the BFF logout route', () => {
    const submit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => undefined);
    signOut();
    const form = document.body.querySelector('form')!;
    expect(form.method).toBe('post');
    expect(form.getAttribute('action')).toBe('/api/auth/logout');
    expect(submit).toHaveBeenCalled();
    form.remove();
  });
});
