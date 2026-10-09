import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';
import { StaffProvider, type Staff } from '@/components/console/StaffContext';

export const staffFixture: Staff = {
  id: 'staff-1',
  name: 'Test Staff',
  email: 'staff@example.test',
  roles: ['SUPER_ADMIN'],
};

/** Render inside the providers every console page expects. Fixtures live in tests only. */
export function renderConsole(ui: ReactElement, staff: Partial<Staff> = {}) {
  return render(
    <SnackbarProvider>
      <StaffProvider staff={{ ...staffFixture, ...staff }}>{ui}</StaffProvider>
    </SnackbarProvider>
  );
}
