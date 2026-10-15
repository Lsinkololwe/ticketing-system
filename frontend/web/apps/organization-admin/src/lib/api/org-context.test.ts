import { describe, expect, it } from 'vitest';
import { capabilitiesFromPermissions } from './org-context';

describe('capabilitiesFromPermissions', () => {
  it('gives nothing without permissions', () => {
    expect(capabilitiesFromPermissions([])).toMatchObject({ isOwner: false, canWriteEvents: false, canRefund: false, canViewFinance: false });
    expect(capabilitiesFromPermissions(undefined).canViewTeam).toBe(false);
  });
  it('treats only the holder of organization:transfer as owner', () => {
    expect(capabilitiesFromPermissions(['organization:transfer']).isOwner).toBe(true);
    expect(capabilitiesFromPermissions(['organization:edit', 'team:role']).isOwner).toBe(false);
  });
  it('follows the permissions the platform reported, not the role name', () => {
    expect(capabilitiesFromPermissions(['event:view']).canViewFinance).toBe(false);
    expect(capabilitiesFromPermissions(['financial:view']).canViewFinance).toBe(true);
    expect(capabilitiesFromPermissions(['payout:request']).canRequestPayout).toBe(true);
  });
  it('limits a marketer-shaped set', () => {
    const marketer = ['event:view', 'attendee:view', 'analytics:view', 'promotion:manage', 'media:manage', 'team:view'];
    expect(capabilitiesFromPermissions(marketer)).toMatchObject({ canWriteEvents: false, canRefund: false, canManageMedia: true, canNotify: false, canManageTeam: false });
  });
});
