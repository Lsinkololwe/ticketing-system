import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction } from '@/test/menu';
import { renderConsole } from '@/test/render';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/users/users',
  useSearchParams: () => new URLSearchParams(),
}));

const FIXTURE_PASSWORD = 'fixture-pass-0001';
const RESET_PASSWORD = 'fixture-pass-0002';

const actions = {
  createUser: vi.fn(),
  resetStaffPassword: vi.fn(),
  updateUser: vi.fn(),
  suspendUser: vi.fn(),
  unsuspendUser: vi.fn(),
  lockUser: vi.fn(),
  unlockUser: vi.fn(),
  activateUser: vi.fn(),
  deactivateUser: vi.fn(),
  setUserRoles: vi.fn().mockResolvedValue({}),
};
const listState = { rows: [] as unknown[], total: 0, pageSize: 20, loading: false, error: undefined as Error | undefined, refetch: vi.fn() };

vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => ({
  ...(await orig<object>()),
  useIdentityUsers: () => listState,
  useUserAdminActions: () => actions,
  useIdentityUser: () => ({ user: null, loading: false, refetch: vi.fn() }),
  useBuyerLookup: () => ({ lookup: vi.fn(), loading: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useStaffActions: () => ({ deleteUser: vi.fn(), busy: false }),
  useAuditLogs: () => ({ entries: [], loading: false, error: undefined, refetch: vi.fn() }),
}));

import { UsersPage } from '../UsersPage';

const user = (over: Record<string, unknown> = {}) => ({
  id: 'u1', username: 'mwila', email: 'mwila@example.test', firstName: 'Mwila', lastName: 'Banda', fullName: 'Mwila Banda',
  phoneNumber: '+260971234567', gender: null, roles: ['CUSTOMER'], accountStatus: 'ACTIVE', emailVerified: true, phoneVerified: false,
  active: true, locked: false, twoFactorEnabled: false, memberSince: null, lastLoginAt: null, lastActiveAt: null, createdAt: '2026-01-01T00:00:00Z', ...over,
});

const writeText = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  writeText.mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  Object.assign(listState, {
    rows: [user(), user({ id: 'u3', fullName: 'Staff Person', roles: ['CUSTOMER', 'ADMIN'] })],
    total: 2,
  });
});

afterEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
});

function fillAndSubmitCreate() {
  fireEvent.click(screen.getByRole('button', { name: 'Create admin' }));
  const dlg = screen.getByRole('dialog');
  fireEvent.change(within(dlg).getByLabelText('First name'), { target: { value: 'Ben' } });
  fireEvent.change(within(dlg).getByLabelText('Last name'), { target: { value: 'Phiri' } });
  fireEvent.change(within(dlg).getByLabelText('Email'), { target: { value: 'ben@example.test' } });
  fireEvent.change(within(dlg).getByLabelText('Phone number'), { target: { value: '+260971234568' } });
  fireEvent.click(within(dlg).getByRole('button', { name: 'Create admin' }));
}

const storageText = () => JSON.stringify({ ...window.localStorage }) + JSON.stringify({ ...window.sessionStorage });

describe('Create admin: one-time temporary password', () => {
  it('sends no password, shows the generated one once, and clears it when the panel is closed', async () => {
    actions.createUser.mockResolvedValue({ user: { id: 'new1' }, temporaryPassword: FIXTURE_PASSWORD });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fillAndSubmitCreate();

    const panel = await screen.findByTestId('temporary-password-panel');
    expect(actions.createUser).toHaveBeenCalledWith(expect.not.objectContaining({ password: expect.anything() }));
    expect(within(panel).getByTestId('temporary-password-value')).toHaveValue(FIXTURE_PASSWORD);
    expect(within(panel).getByTestId('temporary-password-value')).toHaveAttribute('type', 'password');
    expect(within(panel).getByTestId('temporary-password-value')).toHaveAttribute('autocomplete', 'off');
    expect(panel).toHaveTextContent(/shown once/i);
    expect(storageText()).not.toContain(FIXTURE_PASSWORD);

    fireEvent.click(screen.getByTestId('temporary-password-done'));
    await waitFor(() => expect(screen.queryByTestId('temporary-password-panel')).not.toBeInTheDocument());
    expect(document.body.innerHTML).not.toContain(FIXTURE_PASSWORD);
    expect(storageText()).not.toContain(FIXTURE_PASSWORD);
  });

  it('copies the password to the clipboard and can reveal it', async () => {
    actions.createUser.mockResolvedValue({ user: { id: 'new1' }, temporaryPassword: FIXTURE_PASSWORD });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fillAndSubmitCreate();
    await screen.findByTestId('temporary-password-panel');

    fireEvent.click(screen.getByTestId('temporary-password-copy'));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(FIXTURE_PASSWORD));

    fireEvent.click(screen.getByTestId('temporary-password-toggle'));
    expect(screen.getByTestId('temporary-password-value')).toHaveAttribute('type', 'text');
  });

  it('drops the password when the dialog is dismissed with Escape', async () => {
    actions.createUser.mockResolvedValue({ user: { id: 'new1' }, temporaryPassword: FIXTURE_PASSWORD });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fillAndSubmitCreate();
    const panel = await screen.findByTestId('temporary-password-panel');
    fireEvent.keyDown(panel, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByTestId('temporary-password-panel')).not.toBeInTheDocument());
    expect(document.body.innerHTML).not.toContain(FIXTURE_PASSWORD);
  });

  it('a repeat of a finished request says the account exists and shows no password panel', async () => {
    actions.createUser.mockResolvedValue({ user: { id: 'new1' }, temporaryPassword: null });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fillAndSubmitCreate();
    expect(await screen.findByText(/already has an account/i)).toHaveTextContent(/shown when it was created/i);
    expect(screen.queryByTestId('temporary-password-panel')).not.toBeInTheDocument();
  });
});

describe('Reset password', () => {
  it('is offered to a super admin for a staff account only', () => {
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Staff Person' }));
    expect(screen.getByRole('menuitem', { name: 'Reset password…' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' });
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Mwila Banda' }));
    expect(screen.queryByRole('menuitem', { name: 'Reset password…' })).not.toBeInTheDocument();
  });

  it.each(['ADMIN', 'FINANCE', 'FINANCE_LEAD'] as const)('is not offered to %s', (role) => {
    renderConsole(<UsersPage tab="users" />, { roles: [role] });
    // Finance roles cannot manage users at all, so the row menu may not even exist.
    const trigger = screen.queryByRole('button', { name: 'More actions for Staff Person' });
    if (trigger) fireEvent.click(trigger);
    expect(screen.queryByRole('menuitem', { name: 'Reset password…' })).not.toBeInTheDocument();
  });

  it('asks for confirmation, then shows the new password once in the same panel', async () => {
    actions.resetStaffPassword.mockResolvedValue({ user: { id: 'u3' }, temporaryPassword: RESET_PASSWORD });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    menuAction('More actions for Staff Person', 'Reset password…');
    expect(actions.resetStaffPassword).not.toHaveBeenCalled();
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Reset password' }));

    const panel = await screen.findByTestId('temporary-password-panel');
    expect(actions.resetStaffPassword).toHaveBeenCalledTimes(1);
    expect(actions.resetStaffPassword).toHaveBeenCalledWith('u3');
    expect(within(panel).getByTestId('temporary-password-value')).toHaveValue(RESET_PASSWORD);
    expect(storageText()).not.toContain(RESET_PASSWORD);

    fireEvent.click(screen.getByTestId('temporary-password-done'));
    await waitFor(() => expect(screen.queryByTestId('temporary-password-panel')).not.toBeInTheDocument());
    expect(document.body.innerHTML).not.toContain(RESET_PASSWORD);
  });

  it('shows a plain message and no panel when the server refuses', async () => {
    actions.resetStaffPassword.mockRejectedValue({ graphQLErrors: [{ message: 'internal detail', extensions: { errorCode: 'NOT_A_STAFF_ACCOUNT', classification: 'FAILED_PRECONDITION' } }] });
    renderConsole(<UsersPage tab="users" />, { roles: ['SUPER_ADMIN'] });
    menuAction('More actions for Staff Person', 'Reset password…');
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Reset password' }));
    expect(await screen.findByText(/not a staff account/i)).toBeInTheDocument();
    expect(screen.queryByTestId('temporary-password-panel')).not.toBeInTheDocument();
  });
});
