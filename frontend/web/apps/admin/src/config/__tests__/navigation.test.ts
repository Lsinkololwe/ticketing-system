import { describe, expect, it } from 'vitest';
import { MODULES, canOpenModule, moduleOfPath, modulesFor, staffRolesOf, tabsFor } from '../navigation';
import { can, needText } from '@/lib/permissions';

describe('navigation roles', () => {
  it('lists the ten prototype modules in six groups', () => {
    expect(MODULES).toHaveLength(10);
    expect(new Set(MODULES.map((m) => m.group)).size).toBe(6);
  });
  it('gives finance only dashboard, finance, ledger and analytics', () => {
    expect(modulesFor(['FINANCE']).map((m) => m.id)).toEqual(['dashboard', 'finance', 'ledger', 'analytics']);
  });
  it('limits finance lead transaction tabs', () => {
    expect(tabsFor(['FINANCE_LEAD'], 'transactions').map((t) => t.id)).toEqual(['payments', 'reservations', 'recovery', 'audit']);
    expect(canOpenModule(['FINANCE_LEAD'], 'approvals')).toBe(false);
  });
  it('maps detail routes to their module', () => {
    expect(moduleOfPath('/user/abc')).toBe('users');
    expect(moduleOfPath('/event/x')).toBe('events');
    expect(moduleOfPath('/finance/payouts')).toBe('finance');
  });
  it('filters session roles to staff roles', () => {
    expect(staffRolesOf(['customer', 'finance_lead', 'ORGANIZER'])).toEqual(['FINANCE_LEAD']);
  });
  it('gates fine-grained actions', () => {
    expect(can(['ADMIN'], 'deleteUser')).toBe(false);
    expect(can(['SUPER_ADMIN'], 'deleteUser')).toBe(true);
    expect(needText('deleteUser')).toBe('Only super admins can delete users.');
  });
});
