import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';
import { RolesTab } from '../RolesTab';
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
import { referenceFixtures, permissionFixtures } from '@/test/referenceMock';
vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => (await import('@/test/referenceMock')).identityAdminWithPermissions(await orig<object>()));
referenceFixtures.ORGANIZATION_ROLE = [
  { code: 'OWNER', name: 'Owner', description: 'Full control of the organization.' },
  { code: 'MANAGER', name: 'Manager', description: 'Creates and publishes events.' },
];
referenceFixtures.EVENT_ROLE = [{ code: 'CHECK_IN', name: 'Check-in', description: 'Scans tickets at the gate.' }];


describe('RolesTab', () => {
  it('lists the four staff roles and marks the ones the viewer holds', () => {
    renderConsole(<RolesTab />, { roles: ['ADMIN'] });
    for (const name of ['Super admin', 'Admin', 'Finance', 'Finance lead']) {
      expect(screen.getByLabelText(name, { selector: 'div' })).toBeTruthy();
    }
    expect(screen.getByText('Your role')).toBeInTheDocument();
  });

  it('renders the read-only permission matrix with granted and hidden marks', () => {
    renderConsole(<RolesTab />);
    const areas = screen.getByRole('table', { name: 'Areas in the navigation drawer' });
    const health = within(areas).getByRole('row', { name: /Health/ });
    expect(within(health).getAllByRole('img').map((i) => i.getAttribute('aria-label'))).toEqual(['Granted', 'Granted', 'Hidden', 'Granted']);
    const actions = screen.getByRole('table', { name: 'Actions inside pages' });
    expect(within(actions).getByRole('row', { name: /Delete users/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Preview as this role/ })).toBeNull();
  });

  it('shows organization and event roles', () => {
    renderConsole(<RolesTab />);
    // the names and descriptions are the platform's rows, not text in the console
    expect(screen.getByText('Check-in')).toBeInTheDocument();
    expect(screen.getByText('Scans tickets at the gate.')).toBeInTheDocument();
    expect(screen.getByText('Manager')).toBeInTheDocument();
  });

  it('searches and filters the permission catalogue and shows the empty state', () => {
    renderConsole(<RolesTab />);
    expect(screen.getByText(`Read only. ${permissionFixtures.length} permission codes. Which role holds which permission is fixed in the platform code, so it cannot be edited here.`)).toBeInTheDocument();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search permissions' }), { target: { value: 'payout:approve' } });
    const table = screen.getByRole('table', { name: 'Permission catalogue' });
    expect(within(table).getAllByRole('row')).toHaveLength(2);
    expect(within(table).getByText('Finance')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search permissions' }), { target: { value: 'zzzz' } });
    expect(screen.getByText('No permissions match.')).toBeInTheDocument();
  });
});
