import { describe, expect, it } from 'vitest';
import { capabilitiesFor } from './org-context';

describe('capabilitiesFor', () => {
  it('gives nothing without a membership', () => {
    expect(capabilitiesFor(null)).toMatchObject({ isOwner: false, canWriteEvents: false, canRefund: false, canViewFinance: false });
  });
  it('treats only the owner as owner', () => {
    expect(capabilitiesFor('OWNER').isOwner).toBe(true);
    for (const r of ['ADMIN', 'MANAGER', 'MARKETER', 'CONTRIBUTOR'] as const) expect(capabilitiesFor(r).isOwner).toBe(false);
  });
  it('lets managers see finance and admins request payouts only when the organization allows it', () => {
    expect(capabilitiesFor('MANAGER').canViewFinance).toBe(false);
    expect(capabilitiesFor('MANAGER', { managersCanViewFinancials: true }).canViewFinance).toBe(true);
    expect(capabilitiesFor('ADMIN').canRequestPayout).toBe(false);
    expect(capabilitiesFor('ADMIN', { adminsCanRequestPayouts: true }).canRequestPayout).toBe(true);
    expect(capabilitiesFor('OWNER').canRequestPayout).toBe(true);
  });
  it('limits marketers and contributors', () => {
    expect(capabilitiesFor('MARKETER')).toMatchObject({ canWriteEvents: false, canRefund: false, canManageMedia: true, canNotify: false });
    expect(capabilitiesFor('CONTRIBUTOR')).toMatchObject({ canManageMedia: false, canManageTeam: false });
  });
});
