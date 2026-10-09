import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/dashboard',
}));
const counts = { 'pending-approvals': 5, 'organizer-applications': 3, 'event-reviews': 2, 'document-verification': 0, 'payout-requests': 4, 'refund-requests': 0 };
const searches: Array<{ users?: string; events?: string }> = [];
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  usePendingCounts: () => ({ counts, loading: false }),
  useAdminUsers: (o: { search?: string }) => {
    searches.push({ users: o.search });
    return { users: [{ id: 'u7', fullName: 'Mwila Banda', email: 'mwila@x.test' }] };
  },
  useAdminEvents: (o: { searchQuery?: string }) => {
    searches.push({ events: o.searchQuery ?? undefined });
    return { events: [{ id: 'e7', title: 'Mwila Live', status: 'PUBLISHED', cityName: 'Ndola' }] };
  },
}));
vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({ useStuckTransactions: () => ({ pageInfo: { totalCount: 0 }, error: null }) }));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({ useSystemAlerts: () => ({ alerts: [], error: null }) }));
import { ConsoleShell } from '../ConsoleShell';
import { TopTools } from '../TopTools';

beforeEach(() => {
  push.mockClear();
  searches.length = 0;
});

describe('ConsoleShell bell', () => {
  it('shows total pending as the accessible name and lists pending work for the role', async () => {
    renderConsole(<ConsoleShell><TopTools /></ConsoleShell>, { roles: ['ADMIN'] });
    const bell = screen.getByRole('button', { name: 'Notifications: 9 items need attention' });
    await userEvent.click(bell);
    expect(screen.getByText('Pending work for admin')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('menuitem', { name: /Payout requests pending/ }));
    expect(push).toHaveBeenCalledWith('/finance/payouts');
  });

  it('finance only sees finance queues', async () => {
    renderConsole(<ConsoleShell><TopTools /></ConsoleShell>, { roles: ['FINANCE'] });
    await userEvent.click(screen.getByRole('button', { name: /Notifications/ }));
    expect(screen.queryByRole('menuitem', { name: /Organizer applications/ })).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /Payout requests pending/ })).toBeInTheDocument();
  });
});

describe('Command palette', () => {
  it('opens with Ctrl+K and lists only pages the role may open', async () => {
    renderConsole(<ConsoleShell><TopTools /></ConsoleShell>, { roles: ['FINANCE'] });
    await userEvent.keyboard('{Control>}k{/Control}');
    const dlg = screen.getByRole('dialog', { name: 'Search everything' });
    const opts = within(dlg).getAllByRole('option').map((o) => o.textContent);
    expect(opts.join()).toContain('Finance');
    expect(opts.join()).not.toContain('Approvals');
    expect(opts.join()).not.toContain('Health');
  });

  it('opens from the Search button, searches entities, Enter navigates, Escape closes', async () => {
    renderConsole(<ConsoleShell><TopTools /></ConsoleShell>, { roles: ['SUPER_ADMIN'] });
    await userEvent.click(screen.getByRole('button', { name: /Search everything/ }));
    await userEvent.type(screen.getByRole('combobox'), 'mwila');
    expect(await screen.findByRole('option', { name: /Mwila Banda/ })).toBeInTheDocument();
    expect(await screen.findByRole('option', { name: /Mwila Live/ })).toBeInTheDocument();
    await userEvent.keyboard('{Enter}');
    expect(push).toHaveBeenCalledWith('/user/u7');
  });

  it('does not search users for roles without the users module', async () => {
    renderConsole(<ConsoleShell><TopTools /></ConsoleShell>, { roles: ['FINANCE'] });
    await userEvent.keyboard('{Control>}k{/Control}');
    await userEvent.type(screen.getByRole('combobox'), 'mwila');
    await new Promise((r) => setTimeout(r, 400));
    expect(searches.some((s) => s.users === 'mwila')).toBe(false);
  });
});
