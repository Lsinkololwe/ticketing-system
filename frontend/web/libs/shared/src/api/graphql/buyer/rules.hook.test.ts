import { beforeEach, describe, expect, it, vi } from 'vitest';
import { print } from 'graphql';

const useQuery = vi.fn();
vi.mock('@apollo/client/react', () => ({ useQuery: (...a: unknown[]) => useQuery(...a) }));

import { usePlatformRules } from './rules';

describe('usePlatformRules', () => {
  beforeEach(() => useQuery.mockReset());

  it('reads the signed-out readable publicPlatformRules, never the authenticated platformRules', () => {
    useQuery.mockReturnValue({ data: { publicPlatformRules: { version: 3, refundPolicies: [] } }, loading: false, error: undefined, refetch: vi.fn() });
    const r = usePlatformRules();
    const doc = print(useQuery.mock.calls[0][0] as never);
    expect(doc).toContain('publicPlatformRules');
    expect(doc).not.toMatch(/\bplatformRules\b/);
    for (const f of ['reservationHoldMinutes', 'maxTicketsPerBooking', 'refundCutoffHours', 'rescheduleLimit', 'refundPolicies']) expect(doc).toContain(f);
    expect(useQuery.mock.calls[0][1]).toMatchObject({ skip: false });
    expect(r.rules).toEqual({ version: 3, refundPolicies: [] });
  });

  it('stays null while there is no data', () => {
    useQuery.mockReturnValue({ data: undefined, loading: true, error: undefined, refetch: vi.fn() });
    expect(usePlatformRules().rules).toBeNull();
  });
});
