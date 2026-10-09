import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useTeamRoles } from './useTeamRoles';

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => {
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return fakeReferenceModule();
});

describe('useTeamRoles', () => {
  it('lists roles as the platform does and never offers OWNER as an invite role', () => {
    const { result } = renderHook(() => useTeamRoles());
    expect(result.current.orgRoles.map((r) => r.value)).toEqual(['OWNER', 'ADMIN', 'MANAGER', 'MARKETER', 'CONTRIBUTOR']);
    expect(result.current.invitableRoles.map((r) => r.value)).not.toContain('OWNER');
    expect(result.current.grantableEventRoles.map((r) => r.value)).not.toContain('EVENT_OWNER');
    expect(result.current.unavailable).toBe(false);
  });
  it('describes and names a role from its row, and falls back to the code for a role no longer listed', () => {
    const { result } = renderHook(() => useTeamRoles());
    expect(result.current.describe('MARKETER')).toBe('Analytics and promo codes.');
    expect(result.current.nameOf('CHECK_IN')).toBe('Check-in');
    expect(result.current.nameOf('GONE')).toBe('GONE');
    expect(result.current.describe(null)).toBe('');
  });
});
