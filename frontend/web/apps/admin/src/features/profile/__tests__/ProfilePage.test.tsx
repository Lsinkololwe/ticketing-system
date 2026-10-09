import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderConsole } from '@/test/render';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }), usePathname: () => '/profile' }));
const h = vi.hoisted(() => ({
  me: { me: null as any, loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  sessions: { sessions: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  update: vi.fn().mockResolvedValue({}),
  revoke: vi.fn().mockResolvedValue(true),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useMySecurity: () => h.me,
  useMySessions: () => h.sessions,
  useUpdateMyProfile: () => ({ update: h.update, loading: false }),
  useRevokeSession: () => ({ revoke: h.revoke, loading: false }),
}));
import { ProfilePage } from '../ProfilePage';

beforeEach(() => {
  vi.clearAllMocks();
  h.me = { me: { id: 'u1', firstName: 'Ada', lastName: 'Lovelace', displayName: null, fullName: 'Ada Lovelace', email: 'ada@x.test', phoneNumber: '+260971234567', twoFactorEnabled: true, lastLoginAt: null }, loading: false, error: undefined, refetch: vi.fn() };
  h.sessions = { sessions: [{ id: 's1', current: true, clients: ['admin-console'], ipAddress: '10.0.0.1', startedAt: '2026-10-05T07:00:00Z', lastAccessAt: '2026-10-05T08:00:00Z' }, { id: 's2', current: false, clients: [], ipAddress: '10.0.0.2', startedAt: '2026-10-04T07:00:00Z', lastAccessAt: null }], loading: false, error: undefined, refetch: vi.fn() };
});

describe('ProfilePage', () => {
  it('shows editable name, read-only contact details and roles', () => {
    renderConsole(<ProfilePage />, { roles: ['ADMIN', 'FINANCE'] });
    expect(screen.getByRole('heading', { level: 1, name: 'My profile' })).toBeInTheDocument();
    expect(screen.getByLabelText('First name')).toHaveValue('Ada');
    expect(screen.getByLabelText('Email')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Phone')).toHaveValue('+260971234567');
    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByText('Finance')).toBeInTheDocument();
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled();
  });

  it('validates and saves the name through updateMyProfile', async () => {
    renderConsole(<ProfilePage />);
    fireEvent.change(screen.getByLabelText('First name'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Byron' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Enter your first name')).toBeInTheDocument();
    expect(h.update).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Augusta' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(h.update).toHaveBeenCalledWith({ firstName: 'Augusta', lastName: 'Byron', displayName: undefined }));
  });

  it('security tab shows two-step status and devices, and signs out another device', async () => {
    renderConsole(<ProfilePage />);
    await userEvent.click(screen.getByRole('tab', { name: 'Security and sessions' }));
    expect(screen.getByRole('heading', { name: 'Two-step verification' })).toBeInTheDocument();
    expect(screen.getByText('On')).toBeInTheDocument();
    const t = screen.getByRole('table', { name: 'Signed-in devices' });
    expect(within(t).getByText('This device')).toBeInTheDocument();
    expect(within(t).getAllByRole('button', { name: 'Sign out' })).toHaveLength(1);
    await userEvent.click(within(t).getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(h.revoke).toHaveBeenCalledWith('s2'));
    expect(screen.queryByText(/Not available yet/)).toBeNull();
  });

  it('shows error and loading states', async () => {
    h.me = { me: null, loading: false, error: new Error('down'), refetch: vi.fn() };
    const { unmount } = renderConsole(<ProfilePage />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    unmount();
    h.me = { me: null, loading: true, error: undefined, refetch: vi.fn() };
    renderConsole(<ProfilePage />);
    expect(document.querySelector('[aria-busy="true"], .m3-skeleton')).toBeTruthy();
  });
});
