import { describe, expect, it, vi, beforeEach } from 'vitest';
import { BffAuthError } from '@pml.tickets/shared/auth/bff';

const requireSession = vi.fn();
vi.unmock('@/lib/stepUpAction');
vi.mock('@/lib/bff', () => ({ bff: { requireSession: (o: unknown) => requireSession(o) }, STAFF_ACCESS_ROLES: ['ADMIN'], STEP_UP_SEC: 300 }));

import { checkFreshAuth } from '@/lib/stepUpAction';

beforeEach(() => requireSession.mockReset());

describe('checkFreshAuth', () => {
  it('requires a session no older than the step-up window, as an action', async () => {
    requireSession.mockResolvedValue({});
    await expect(checkFreshAuth('/finance')).resolves.toEqual({ fresh: true });
    expect(requireSession).toHaveBeenCalledWith({ roles: ['ADMIN'], freshAuthSec: 300, kind: 'action', returnTo: '/finance' });
  });
  it('returns the step-up URL when the sign-in is stale', async () => {
    requireSession.mockRejectedValueOnce(new BffAuthError('STEP_UP_REQUIRED', '/api/auth/stepup?next=%2Ffinance&maxAge=300'));
    await expect(checkFreshAuth('/finance')).resolves.toEqual({ fresh: false, url: '/api/auth/stepup?next=%2Ffinance&maxAge=300' });
  });
  it('returns the login page when there is no session and rethrows forbidden', async () => {
    requireSession.mockRejectedValueOnce(new BffAuthError('UNAUTHENTICATED'));
    await expect(checkFreshAuth('/x')).resolves.toEqual({ fresh: false, url: '/login' });
    requireSession.mockRejectedValueOnce(new BffAuthError('FORBIDDEN'));
    await expect(checkFreshAuth('/x')).rejects.toThrow('FORBIDDEN');
  });
});
