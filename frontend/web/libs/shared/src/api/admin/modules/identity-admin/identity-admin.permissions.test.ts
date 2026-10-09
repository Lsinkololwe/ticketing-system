import { describe, expect, it } from 'vitest';
import { print } from 'graphql';
import { ADMIN_PERMISSION_CATALOGUE, ADMIN_ROLE_PERMISSIONS, heldByText } from './identity-admin.permissions';

const roles = [
  { role: 'OWNER', label: 'Owner' },
  { role: 'ADMIN', label: 'Admin' },
  { role: 'MANAGER', label: 'Manager' },
];
const carried = new Map([
  ['OWNER', { always: new Set(['payout:request', 'event:create']), switchable: new Set<string>() }],
  ['ADMIN', { always: new Set(['event:create']), switchable: new Set(['payout:request']) }],
  ['MANAGER', { always: new Set<string>(), switchable: new Set<string>() }],
]);

describe('heldByText', () => {
  it('names the roles that always carry a permission', () => {
    expect(heldByText('event:create', carried, roles)).toBe('Owner, Admin');
  });
  it('marks roles that carry it only when the owner switches it on', () => {
    expect(heldByText('payout:request', carried, roles)).toBe('Owner, Admin (when enabled)');
  });
  it('says so when no asked role holds it (a platform-only permission)', () => {
    expect(heldByText('audit:read', carried, roles)).toBe('—');
  });
  it('shows nothing for a role identity did not answer for', () => {
    expect(heldByText('event:create', new Map(), roles)).toBe('—');
  });
});

describe('permission documents', () => {
  it('have unique operation names and no interpolation', () => {
    expect(print(ADMIN_PERMISSION_CATALOGUE)).toContain('query AdminPermissionCatalogue');
    expect(print(ADMIN_ROLE_PERMISSIONS)).toContain('query AdminRolePermissions($role: String!)');
  });
});
