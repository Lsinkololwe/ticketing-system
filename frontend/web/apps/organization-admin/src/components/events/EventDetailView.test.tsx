import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { OrgEventDetail, OrgTier } from '@/lib/api/events';

const push = vi.fn();
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }), usePathname: () => '/events/e1', useSearchParams: () => new URLSearchParams() }));
vi.mock('@/components/bookings/EventBookingsTab', () => ({ EventBookingsTab: () => <div>bookings-tab</div> }));
vi.mock('@/components/checkin/EventCheckInTab', () => ({ EventCheckInTab: () => <div>checkin-tab</div> }));
vi.mock('./NotifyTab', () => ({ NotifyTab: () => <div>notify-tab</div> }));
vi.mock('./AnalyticsTab', () => ({ AnalyticsTab: () => <div>analytics-tab</div> }));
vi.mock('@/components/team/EventAccessTab', () => ({ EventAccessTab: () => <div>access-tab</div> }));

import { EventDetailView, EVENT_TABS, type EventDetailViewProps } from './EventDetailView';

const tier = (o: Partial<OrgTier> = {}): OrgTier => ({
  id: 't1', eventId: 'e1', code: 'GEN', name: 'General', description: null, price: '150', currency: 'ZMW', quantity: 100, soldQuantity: 10, availableQuantity: 90,
  minPerOrder: 1, maxPerOrder: 8, benefits: [], salesStartAt: null, salesEndAt: null, earlyBirdPrice: null, earlyBirdEndsAt: null, sortOrder: 0, isActive: true, isHidden: false, accessCode: null, ...o,
});
const event = (o: Partial<OrgEventDetail> = {}): OrgEventDetail => ({
  id: 'e1', title: 'Fixture Fest', description: 'Desc', status: 'APPROVED', eventDateTime: '2026-11-14T17:00:00Z', endDateTime: '2026-11-14T23:00:00Z',
  locationName: 'Grounds', locationAddress: null, cityName: 'Lusaka', bannerImageUrl: null, totalCapacity: 200, soldTickets: 10, availableTickets: 190, revenue: '1500', currency: 'ZMW',
  refundPolicy: 'FLEXIBLE', rejectionReason: null, publishedAt: null, submittedForApprovalAt: null, approvedAt: null, rejectedAt: null, createdAt: '2026-08-01T00:00:00Z',
  category: { id: 'c', name: 'Music' }, ticketTiers: [tier()], ...o,
});
const noop = async () => undefined;
const base = (o: Partial<EventDetailViewProps> = {}): EventDetailViewProps => ({
  event: event(), loading: false, tab: 'overview', onTab: vi.fn(), canWrite: true, onAction: vi.fn(),
  tierActions: { create: noop, update: noop, remove: noop, setActive: noop, reorder: noop },
  promos: [], promoActions: { create: noop, update: noop, setActive: noop, remove: noop }, stats: null, statsLoading: false, ...o,
});

describe('EventDetailView', () => {
  it('renders header, all tabs and the overview controls', () => {
    render(<EventDetailView {...base()} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Fixture Fest' })).toBeInTheDocument();
    EVENT_TABS.forEach((t) => expect(screen.getByRole('tab', { name: new RegExp(t.label as string) })).toBeInTheDocument());
    expect(screen.getByRole('link', { name: /Edit details/ })).toHaveAttribute('href', '/events/e1/edit');
    expect(screen.getByText('No approval blockers.')).toBeInTheDocument();
  });

  it('calls the lifecycle callback from controls and gives reasons when disabled', () => {
    const onAction = vi.fn();
    render(<EventDetailView {...base({ onAction })} />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Publish' })[0]);
    expect(onAction).toHaveBeenCalledWith('publish');
    expect(screen.getByRole('button', { name: 'Delete draft' })).toBeDisabled();
    expect(screen.getByText('Only drafts can be deleted.')).toBeInTheDocument();
  });

  it('shows blockers and the reviewer comment for changes requested', () => {
    render(<EventDetailView {...base({ event: event({ status: 'CHANGES_REQUESTED', rejectionReason: 'Fix the refund policy', ticketTiers: [], locationName: null }) })} />);
    expect(screen.getByTestId('reviewer-comment')).toHaveTextContent('Fix the refund policy');
    expect(screen.getByText('Admins cannot approve until these are fixed.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resubmit for approval' })).toBeEnabled();
  });

  it('switches tabs through onTab and mounts the sibling tabs', () => {
    const onTab = vi.fn();
    const { rerender } = render(<EventDetailView {...base({ onTab })} />);
    fireEvent.click(screen.getByRole('tab', { name: /Bookings/ }));
    expect(onTab).toHaveBeenCalledWith('bookings');
    rerender(<EventDetailView {...base({ tab: 'bookings' })} />);
    expect(screen.getByText('bookings-tab')).toBeInTheDocument();
    rerender(<EventDetailView {...base({ tab: 'checkin' })} />);
    expect(screen.getByText('checkin-tab')).toBeInTheDocument();
    rerender(<EventDetailView {...base({ tab: 'access' })} />);
    expect(screen.getByText('access-tab')).toBeInTheDocument();
    rerender(<EventDetailView {...base({ tab: 'notify' })} />);
    expect(screen.getByText('notify-tab')).toBeInTheDocument();
    rerender(<EventDetailView {...base({ tab: 'analytics' })} />);
    expect(screen.getByText('analytics-tab')).toBeInTheDocument();
  });

  it('tiers tab: delete is disabled when sold, activate calls back, reorder', () => {
    const setActive = vi.fn(noop);
    const reorder = vi.fn(noop);
    const tiers = [tier(), tier({ id: 't2', name: 'VIP', soldQuantity: 0, sortOrder: 1, isActive: false })];
    render(<EventDetailView {...base({ tab: 'tiers', event: event({ ticketTiers: tiers }), tierActions: { create: noop, update: noop, remove: noop, setActive, reorder } })} />);
    expect(screen.getByRole('button', { name: /Delete General/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Delete VIP' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: 'Activate' }));
    expect(setActive).toHaveBeenCalledWith('t2', true);
    fireEvent.click(screen.getByRole('button', { name: 'Move General down' }));
    expect(reorder).toHaveBeenCalledWith(['t2', 't1']);
  });

  it('promos tab lists codes and opens the dialog with validation', async () => {
    const promos = [{ id: 'p1', code: 'SUNSET10', eventId: 'e1', discountType: 'PERCENTAGE' as const, discountValue: '10', maxUses: 200, currentUses: 19, validFrom: null, validUntil: null, minPurchaseAmount: null, maxDiscountAmount: null, applicableTiers: null, isActive: true }];
    const create = vi.fn(noop);
    render(<EventDetailView {...base({ tab: 'promos', promos, promoActions: { create, update: noop, setActive: noop, remove: noop } })} />);
    expect(screen.getByText('SUNSET10')).toBeInTheDocument();
    expect(screen.getByText('19 / 200')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /New code/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Create code' }));
    return waitFor(() => expect(screen.getAllByText('Use 4 to 20 letters or numbers').length).toBeGreaterThan(0));
    expect(create).not.toHaveBeenCalled();
  });

  it('shows loading, error and not-found states', () => {
    const { rerender } = render(<EventDetailView {...base({ event: null, loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    rerender(<EventDetailView {...base({ event: null, error: { message: 'Nope' } })} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Nope');
    rerender(<EventDetailView {...base({ event: null })} />);
    expect(screen.getByText('Event not found')).toBeInTheDocument();
  });

  it('back button returns to the events list', () => {
    render(<EventDetailView {...base()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Back to events' }));
    expect(push).toHaveBeenCalledWith('/events');
  });
});
