import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';
import { permissionsOfRoles } from '@pml.tickets/shared/generated/permission-codes';
import { StaffProvider, type Staff } from '@/components/console/StaffContext';

export const staffFixture: Staff = {
  id: 'staff-1',
  name: 'Test Staff',
  email: 'staff@example.test',
  roles: ['SUPER_ADMIN'],
  permissions: permissionsOfRoles(['SUPER_ADMIN']),
};

/** Render inside the providers every console page expects. Fixtures live in tests only. */
export function renderConsole(ui: ReactElement, staff: Partial<Staff> = {}) {
  // The roles a test names decide the permissions, exactly as the layout derives them for a session.
  const merged = { ...staffFixture, ...staff };
  // The identity realm makes the finance lead a composite of finance, so a lead's token carries both.
  const roles = merged.roles.includes('FINANCE_LEAD') ? [...merged.roles, 'FINANCE'] : merged.roles;
  const permissions = staff.permissions ?? permissionsOfRoles(roles);
  return render(
    <SnackbarProvider>
      <StaffProvider staff={{ ...merged, permissions }}>{ui}</StaffProvider>
    </SnackbarProvider>
  );
}
