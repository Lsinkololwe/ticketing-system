import { describe, expect, it } from 'vitest';
import { MODULES, canOpenModule, moduleOfPath, modulesFor, staffRolesOf, tabsFor } from '../navigation';
import { can, needText } from '@/lib/permissions';
import { permissionsOfRoles } from '@pml.tickets/shared/generated/permission-codes';

const held = (...roles: string[]) => permissionsOfRoles(roles);

describe('navigation roles', () => {
  it('lists the ten prototype modules in six groups', () => {
    expect(MODULES).toHaveLength(10);
    expect(new Set(MODULES.map((m) => m.group)).size).toBe(6);
  });
  it('gives finance dashboard, finance, ledger, analytics and the audit log under transactions', () => {
    expect(modulesFor(held('FINANCE')).map((m) => m.id)).toEqual(['dashboard', 'finance', 'ledger', 'transactions', 'analytics']);
    expect(tabsFor(held('FINANCE'), 'transactions').map((t) => t.id)).toEqual(['payments', 'tickets', 'reservations', 'recovery', 'audit']);
  });
  it('opens finance lead the tabs its permissions reach', () => {
    expect(tabsFor(held('FINANCE', 'FINANCE_LEAD'), 'transactions').map((t) => t.id)).toEqual(['payments', 'tickets', 'reservations', 'recovery', 'audit']);
    expect(canOpenModule(held('FINANCE', 'FINANCE_LEAD'), 'approvals')).toBe(false);
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
    expect(can(held('ADMIN'), 'cfgEdit')).toBe(false);
    expect(can(held('SUPER_ADMIN'), 'cfgEdit')).toBe(true);
    expect(needText('cfgEdit')).toBe('Only super admins can edit platform rules.');
    expect(can(held('FINANCE'), 'payoutDecide')).toBe(true);
    expect(can(held('FINANCE'), 'users')).toBe(false);
  });
});
