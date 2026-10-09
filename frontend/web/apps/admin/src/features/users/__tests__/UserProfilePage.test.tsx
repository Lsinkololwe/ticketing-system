import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/user/u1',
  useSearchParams: () => new URLSearchParams(),
}));
const state = { user: null as unknown, loading: false, error: undefined as Error | undefined, refetch: vi.fn() };
const actions = { lockUser: vi.fn().mockResolvedValue({}), deactivateUser: vi.fn().mockResolvedValue({}) };
vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => ({
  ...(await orig<object>()),
  useIdentityUser: () => state,
  useUserAdminActions: () => actions,
  useBuyerTickets: () => ({ tickets: [{ id: 't1', ticketNumber: 'MTZ-001', eventTitle: 'Jazz Night', ticketCategoryName: 'VIP', price: 150, currency: 'ZMW', status: 'ISSUED', purchaseDate: '2026-03-01T00:00:00Z' }], loading: false, refetch: vi.fn() }),
  useBuyerRefunds: () => ({ refunds: [], loading: false, refetch: vi.fn() }),
}));
const ops = vi.hoisted(() => ({ audit: { entries: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() }, bookings: { bookings: [] as any[], pageInfo: { totalCount: 0, pageSize: 10 }, loading: false, error: undefined as Error | undefined, refetch: vi.fn() } }));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useAuditLogs: () => ops.audit,
  useStaffActions: () => ({ deleteUser: vi.fn(), createStaff: vi.fn(), busy: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({ useBookingsByBuyer: () => ops.bookings }));
import { UserProfilePage } from '../UserProfilePage';

const user = {
  id: 'u1', username: 'mwila', email: 'mwila@example.test', fullName: 'Mwila Banda', phoneNumber: '+260971234567', roles: ['CUSTOMER'],
  accountStatus: 'ACTIVE', emailVerified: true, phoneVerified: true, active: true, locked: false, twoFactorEnabled: true, createdAt: '2026-01-01T00:00:00Z',
  contacts: [{ id: 'c1', type: 'EMAIL', valueMasked: 'm***@example.test', primary: true, verifiedAt: '2026-01-02T00:00:00Z' }], organizationMemberships: [],
};
beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(state, { user, loading: false, error: undefined });
  ops.audit.entries = [{ id: 'l1', action: 'USER_LOCKED', at: '2026-10-01T08:00:00Z', actorId: 'root', status: 'SUCCESS' }];
  ops.bookings.bookings = [{ id: 'b1', bookingNumber: 'BK-1001', eventTitle: 'Jazz Night', eventDate: '2026-12-01', ticketCount: 2, totalAmount: '300', status: 'CONFIRMED', createdAt: '2026-03-01T00:00:00Z' }];
});

describe('User profile page', () => {
  it('shows account, masked contacts, bookings, tickets and staff activity', () => {
    renderConsole(<UserProfilePage id="u1" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Mwila Banda' })).toBeInTheDocument();
    expect(screen.getByText('m***@example.test')).toBeInTheDocument();
    expect(screen.queryByText('mwila@example.test')).not.toBeInTheDocument();
    expect(screen.getByText('MTZ-001')).toBeInTheDocument();
    expect(screen.getByText('BK-1001')).toBeInTheDocument();
    expect(screen.getByText('User locked')).toBeInTheDocument();
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByText('No refund requests')).toBeInTheDocument();
  });

  it('reveals the email only on the explicit control', () => {
    renderConsole(<UserProfilePage id="u1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Reveal email' }));
    expect(screen.getByText('mwila@example.test')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Hide email' }));
    expect(screen.queryByText('mwila@example.test')).not.toBeInTheDocument();
  });

  it('runs a reason-gated action from the actions card', async () => {
    renderConsole(<UserProfilePage id="u1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Lock account' }));
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Suspicious login' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Lock account' }).pop()!);
    await waitFor(() => expect(actions.lockUser).toHaveBeenCalledWith('u1', 'Suspicious login'));
  });

  it('finance staff cannot open the users area', () => {
    renderConsole(<UserProfilePage id="u1" />, { roles: ['FINANCE'] });
    expect(screen.getByText("You don't have access to this")).toBeInTheDocument();
  });

  it('back button, not found and error', () => {
    renderConsole(<UserProfilePage id="u1" />);
    fireEvent.click(screen.getByRole('button', { name: 'All users' }));
    expect(push).toHaveBeenCalledWith('/users/users');
  });

  it('not found and error states', () => {
    state.user = null;
    const a = renderConsole(<UserProfilePage id="zz" />);
    expect(screen.getByText('User not found')).toBeInTheDocument();
    a.unmount();
    state.error = new Error('x');
    renderConsole(<UserProfilePage id="zz" />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});
