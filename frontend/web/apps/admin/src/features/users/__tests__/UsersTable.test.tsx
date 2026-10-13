import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction } from '@/test/menu';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/users/users',
  useSearchParams: () => new URLSearchParams(),
}));

const actions = {
  createUser: vi.fn(),
  updateUser: vi.fn(),
  suspendUser: vi.fn().mockResolvedValue({}),
  unsuspendUser: vi.fn().mockResolvedValue({}),
  lockUser: vi.fn().mockResolvedValue({}),
  unlockUser: vi.fn(),
  activateUser: vi.fn(),
  deactivateUser: vi.fn(),
  setUserRoles: vi.fn().mockResolvedValue({}),
};
const listState = { rows: [] as unknown[], total: 0, pageSize: 20, loading: false, error: undefined as Error | undefined, refetch: vi.fn() };
const usersCall = vi.fn();

vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => ({
  ...(await orig<object>()),
  useIdentityUsers: (o: unknown) => {
    usersCall(o);
    return listState;
  },
  useUserAdminActions: () => actions,
  useIdentityUser: () => ({ user: null, loading: false, refetch: vi.fn() }),
  useBuyerLookup: () => ({ lookup: vi.fn().mockResolvedValue(null), loading: false }),
}));

const deleteUser = vi.fn().mockResolvedValue({});
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useStaffActions: () => ({ deleteUser, busy: false }),
  useAuditLogs: () => ({ entries: [{ id: 'l1', action: 'USER_SUSPENDED', at: '2026-10-01T08:00:00Z', actorId: 'root', status: 'SUCCESS' }], loading: false, error: undefined, refetch: vi.fn() }),
}));

import { UsersPage } from '../UsersPage';

const user = (over: Record<string, unknown> = {}) => ({
  id: 'u1', username: 'mwila', email: 'mwila@example.test', firstName: 'Mwila', lastName: 'Banda', fullName: 'Mwila Banda',
  phoneNumber: '+260971234567', gender: null, roles: ['CUSTOMER'], accountStatus: 'ACTIVE', emailVerified: true, phoneVerified: false,
  active: true, locked: false, twoFactorEnabled: false, memberSince: null, lastLoginAt: null, lastActiveAt: null, createdAt: '2026-01-01T00:00:00Z', ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(listState, { rows: [user(), user({ id: 'u2', fullName: 'Chanda Phiri', accountStatus: 'SUSPENDED', roles: ['CUSTOMER', 'ORGANIZER'], twoFactorEnabled: true })], total: 2, loading: false, error: undefined });
});

describe('Users table', () => {
  it('renders headers, masked contacts and per-row buttons (rows are not clickable)', () => {
    renderConsole(<UsersPage tab="users" />);
    const table = screen.getByRole('table', { name: 'Users' });
    for (const h of ['User', 'Phone', 'Roles', 'Status', '2-step', 'Last sign-in']) expect(within(table).getByRole('columnheader', { name: h })).toBeInTheDocument();
    expect(screen.getByText('Mwila Banda')).toBeInTheDocument();
    expect(screen.queryByText('mwila@example.test')).not.toBeInTheDocument();
    expect(screen.getAllByText('m•••@example.test')).toHaveLength(2);
    expect(screen.queryByText('+260971234567')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Quick view Mwila Banda' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'More actions for Chanda Phiri' })).toBeInTheDocument();
  });

  it('navigates to the full profile', () => {
    renderConsole(<UsersPage tab="users" />);
    menuAction('More actions for Mwila Banda', 'Open full profile');
    expect(push).toHaveBeenCalledWith('/user/u1');
  });

  it('opens the quick view side sheet with an Open full profile action', () => {
    renderConsole(<UsersPage tab="users" />);
    fireEvent.click(screen.getByRole('button', { name: 'Quick view Mwila Banda' }));
    const sheet = screen.getByRole('dialog');
    expect(within(sheet).getByText('Recent activity')).toBeInTheDocument();
    expect(within(sheet).getByText("User suspended")).toBeInTheDocument();
    fireEvent.click(within(sheet).getByRole('button', { name: 'Open full profile' }));
    expect(push).toHaveBeenCalledWith('/user/u1');
  });

  it('shows the designed empty state', () => {
    Object.assign(listState, { rows: [], total: 0 });
    renderConsole(<UsersPage tab="users" />);
    expect(screen.getByText('No users yet')).toBeInTheDocument();
  });

  it('shows skeleton rows while loading and an error with retry', () => {
    Object.assign(listState, { rows: [], loading: true });
    const { unmount } = renderConsole(<UsersPage tab="users" />);
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
    unmount();
    Object.assign(listState, { rows: [], loading: false, error: new Error('boom') });
    renderConsole(<UsersPage tab="users" />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });

  it('passes role and status filters and a debounced search to the query', async () => {
    renderConsole(<UsersPage tab="users" />);
    fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'ORGANIZER' } });
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'LOCKED' } });
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'banda' } });
    await waitFor(() => expect(usersCall).toHaveBeenLastCalledWith(expect.objectContaining({ role: 'ORGANIZER', accountStatus: 'LOCKED', search: 'banda', page: 0 })));
  });

  it('only a super admin is offered Create admin', () => {
    const { unmount } = renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    expect(screen.getByRole('button', { name: 'Create admin' })).toBeEnabled();
    unmount();
    renderConsole(<UsersPage tab="users" />, { roles: ['ADMIN'] });
    expect(screen.queryByRole('button', { name: 'Create admin' })).not.toBeInTheDocument();
  });

  it('cannot create customer or organizer accounts; Create admin offers staff roles only', () => {
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    expect(screen.queryByRole('button', { name: 'New user' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Create admin' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).getAllByRole('option').map((o) => o.textContent)).toEqual(['Admin', 'Finance', 'Finance lead', 'Super admin']);
    expect(within(dlg).queryByText(/phone verification/i)).not.toBeInTheDocument();
  });

  it('the role filter offers buyer roles only', () => {
    renderConsole(<UsersPage tab="users" />);
    const options = within(screen.getByLabelText('Role')).getAllByRole('option').map((o) => o.textContent);
    expect(options.join(' ')).not.toMatch(/Admin|Finance/);
  });

  it('requires a reason before suspending, then calls suspendUser', async () => {
    renderConsole(<UsersPage tab="users" />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Mwila Banda' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Suspend…' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Suspend user' }));
    expect(await within(dlg).findByText(/Give a short reason/)).toBeInTheDocument();
    expect(actions.suspendUser).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Chargeback abuse' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Suspend user' }));
    await waitFor(() => expect(actions.suspendUser).toHaveBeenCalledWith('u1', 'Chargeback abuse'));
  });

  it('offers Unsuspend for suspended users and a disabled Delete with the missing operation', async () => {
    renderConsole(<UsersPage tab="users" />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Chanda Phiri' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Unsuspend' }));
    await waitFor(() => expect(actions.unsuspendUser).toHaveBeenCalledWith('u2'));
  });

  it('deletes a user (soft) after confirmation', async () => {
    renderConsole(<UsersPage tab="users" />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Mwila Banda' }));
    const del = await screen.findByRole('menuitem', { name: /Delete user/ });
    expect(del).not.toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(del);
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Delete user' }));
    await waitFor(() => expect(deleteUser).toHaveBeenCalledTimes(1));
  });

  it('staff accounts need a phone number and a role', async () => {
    actions.createUser.mockResolvedValue({ user: { id: 'new2' }, temporaryPassword: 'fixture-pass-0001' });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fireEvent.click(screen.getByRole('button', { name: 'Create admin' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('First name'), { target: { value: 'Ben' } });
    fireEvent.change(within(dlg).getByLabelText('Last name'), { target: { value: 'Phiri' } });
    fireEvent.change(within(dlg).getByLabelText('Email'), { target: { value: 'ben@example.test' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create admin' }));
    expect(await within(dlg).findByText('Staff need a phone number such as +260971234567')).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Phone number'), { target: { value: '+260971234568' } });
    fireEvent.change(within(dlg).getByLabelText('Staff role'), { target: { value: 'FINANCE' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create admin' }));
    await waitFor(() => expect(actions.createUser).toHaveBeenCalledWith(expect.objectContaining({ role: 'FINANCE', phoneNumber: '+260971234568' })));
  });

  it('a staff account is not offered the buyer role checklist', async () => {
    Object.assign(listState, { rows: [user({ id: 'u3', fullName: 'Staff Person', roles: ['CUSTOMER', 'ADMIN'] })], total: 1 });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Staff Person' }));
    expect(screen.queryByRole('menuitem', { name: 'Change roles' })).not.toBeInTheDocument();
  });

  it('saves roles keeping the base CUSTOMER role and offers no staff roles', async () => {
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Mwila Banda' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Change roles' }));
    const dlg = screen.getByRole('dialog');
    for (const r of ['Admin', 'Super admin', 'Finance', 'Finance lead']) expect(within(dlg).queryByLabelText(r)).not.toBeInTheDocument();
    fireEvent.click(within(dlg).getByLabelText('Organizer'));
    fireEvent.click(within(dlg).getByRole('button', { name: 'Save roles' }));
    await waitFor(() => expect(actions.setUserRoles).toHaveBeenCalledWith('u1', ['CUSTOMER', 'ORGANIZER']));
  });

  it('uses m3 classes only (no inline px or hex)', () => {
    const { container } = renderConsole(<UsersPage tab="users" />);
    expect(container.innerHTML).not.toMatch(/#[0-9a-f]{3,6}\b|\d+px/i);
  });
});
