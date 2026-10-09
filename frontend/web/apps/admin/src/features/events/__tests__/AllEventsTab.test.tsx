import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { menuAction } from '@/test/menu';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  push: vi.fn(),
  table: vi.fn(),
  feature: vi.fn(),
  payouts: vi.fn(),
  cancel: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: h.push, replace: vi.fn(), back: vi.fn() }),
  usePathname: () => '/events/all',
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/catalog-admin', () => ({
  useAdminEventsTable: h.table,
  useCitiesAdmin: () => ({ cities: [{ id: 'c1', name: 'Lusaka' }] }),
  useFeatureEvent: () => ({ feature: h.feature, loading: false }),
  useEventPayouts: h.payouts,
  useCancelEvent: () => ({ cancel: h.cancel, loading: false }),
  useAdminEventDetail: () => ({ event: null, loading: true, error: undefined, refetch: vi.fn() }),
  useEventApprovalTimeline: () => ({ timeline: null }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/event', () => ({
  useAdminEventCategories: () => ({ categories: [{ id: 'k1', name: 'Music' }] }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/reference-data', () => ({
  useReferenceData: () => ({ items: [{ id: 'r1', name: 'Venue unavailable' }] }),
}));

import { AllEventsTab } from '../AllEventsTab';

const row = (over: Record<string, unknown> = {}) => ({
  id: 'e1',
  title: 'Fixture Fest',
  status: 'PUBLISHED',
  published: true,
  featured: false,
  eventDateTime: '2030-01-01T10:00:00Z',
  endDateTime: null,
  organizerId: 'o1',
  organizerName: 'Fixture Org',
  organizationId: 'org1',
  locationName: 'Fixture Hall',
  cityName: 'Lusaka',
  categoryId: 'k1',
  totalCapacity: 100,
  soldTickets: 25,
  currency: 'ZMW',
  minTicketPrice: 50,
  submittedForApprovalAt: null,
  approvalDeadline: null,
  isOverdue: false,
  category: { id: 'k1', name: 'Music' },
  ...over,
});
const state = (over = {}) => ({
  events: [row()],
  pageInfo: { totalCount: 1, pageSize: 20, currentPage: 0, totalPages: 1 },
  loading: false,
  error: undefined,
  refetch: vi.fn(),
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  h.table.mockReturnValue(state());
  h.feature.mockResolvedValue({ success: true, message: null });
  h.payouts.mockReturnValue({ payouts: [], openPayout: null, loading: false });
  h.cancel.mockResolvedValue({ success: true, message: null, ticketsAffected: 25 });
});

describe('AllEventsTab', () => {
  it('renders headers, rows and action buttons (rows are not clickable)', () => {
    renderConsole(<AllEventsTab />);
    for (const name of ['Event', 'Organization', 'Category', 'Date', 'Status', 'Sold', 'Featured']) {
      expect(screen.getByRole('columnheader', { name })).toBeInTheDocument();
    }
    expect(screen.getByText('Fixture Fest')).toBeInTheDocument();
    expect(screen.getAllByText('Fixture Org').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Quick view Fixture Fest' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'More actions for Fixture Fest' })).toBeInTheDocument();
    expect(screen.getByRole('search')).toBeInTheDocument();
  });

  it('navigates to the full page', () => {
    renderConsole(<AllEventsTab />);
    menuAction('More actions for Fixture Fest', 'Open full page');
    expect(h.push).toHaveBeenCalledWith('/event/e1');
  });

  it('toggles the featured flag for a published event', async () => {
    renderConsole(<AllEventsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Feature' }));
    expect(h.feature).toHaveBeenCalledWith('e1', true);
    expect(await screen.findByText('Fixture Fest is now featured on the buyer home page')).toBeInTheDocument();
  });

  it('shows no feature control for unpublished events', () => {
    h.table.mockReturnValue(state({ events: [row({ status: 'DRAFT' })] }));
    renderConsole(<AllEventsTab />);
    expect(screen.queryByRole('button', { name: 'Feature' })).not.toBeInTheDocument();
  });

  it('disables the feature toggle when the role cannot feature', () => {
    renderConsole(<AllEventsTab />, { roles: ['FINANCE'] });
    expect(screen.getByRole('button', { name: 'Feature' })).toBeDisabled();
  });

  it('shows loading, error and empty states', () => {
    h.table.mockReturnValue(state({ events: [], loading: true }));
    const { unmount } = renderConsole(<AllEventsTab />);
    expect(document.querySelector('.m3-skeleton')).not.toBeNull();
    unmount();
    h.table.mockReturnValue(state({ events: [], error: new Error('boom') }));
    const e = renderConsole(<AllEventsTab />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    e.unmount();
    h.table.mockReturnValue(state({ events: [] }));
    renderConsole(<AllEventsTab />);
    expect(screen.getByText('No events yet.')).toBeInTheDocument();
  });

  it('opens the escrow account from the row menu', () => {
    renderConsole(<AllEventsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Fixture Fest' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Escrow account' }));
    expect(h.push).toHaveBeenCalledWith('/finance/escrow?event=e1');
  });

  it('cancel requires details, then cancels with category and details', async () => {
    renderConsole(<AllEventsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Fixture Fest' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Cancel event' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).getByText(/refunds start for all/)).toBeInTheDocument();
    fireEvent.click(within(dlg).getByRole('button', { name: 'Cancel event' }));
    expect((await within(dlg).findAllByText(/at least 5 characters/)).length).toBeGreaterThan(0);
    expect(h.cancel).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Details for ticket holders'), { target: { value: 'Venue flooded' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Cancel event' }));
    await vi.waitFor(() => expect(h.cancel).toHaveBeenCalledWith('e1', 'Venue unavailable: Venue flooded'));
  });

  it('blocks cancellation while a payout is open', () => {
    h.payouts.mockReturnValue({ payouts: [], openPayout: { id: 'p1', requestId: 'PO-1', status: 'PENDING' }, loading: false });
    renderConsole(<AllEventsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Fixture Fest' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Cancel event' }));
    expect(screen.getByText('Cannot cancel this event')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open payouts' }));
    expect(h.push).toHaveBeenCalledWith('/finance/payouts');
    expect(h.cancel).not.toHaveBeenCalled();
  });

  it('has no cancel option for draft events', () => {
    h.table.mockReturnValue(state({ events: [row({ status: 'DRAFT' })] }));
    renderConsole(<AllEventsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Fixture Fest' }));
    expect(screen.queryByRole('menuitem', { name: 'Cancel event' })).not.toBeInTheDocument();
  });

  it('opens the quick view side sheet', () => {
    renderConsole(<AllEventsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Quick view Fixture Fest' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
