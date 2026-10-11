import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  push: vi.fn(),
  detail: vi.fn(),
  feature: vi.fn(),
  approve: vi.fn(),
  reject: vi.fn(),
  escrow: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: h.push, replace: vi.fn(), back: vi.fn() }),
  usePathname: () => '/event/e1',
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/catalog-admin', () => ({
  useAdminEventDetail: h.detail,
  useEventApprovalTimeline: () => ({
    timeline: {
      eventId: 'e1',
      currentStatus: 'PENDING_APPROVAL',
      assignedReviewerName: 'Rita Reviewer',
      submittedAt: null,
      slaDeadline: null,
      isOverdue: false,
      hoursUntilDeadline: 10,
      submissionCount: 2,
      hasActiveEscalation: false,
      escalation: null,
      timelineEvents: [{ id: 't1', timestamp: '2030-01-01T09:00:00Z', action: 'SUBMITTED', actorName: 'Olu', actorRole: null, description: 'Submitted', comments: 'Please check', isEscalationRelated: false }],
    },
    loading: false,
    error: undefined,
    refetch: vi.fn(),
  }),
  useAddApprovalComment: () => ({ add: vi.fn(), loading: false }),
  useFeatureEvent: () => ({ feature: h.feature, loading: false }),
  useEventEscrow: h.escrow,
  useEventPayouts: () => ({ payouts: [], openPayout: null, loading: false }),
  useEventRefunds: () => ({ refunds: [], total: 0 }),
  useCancelEvent: () => ({ cancel: vi.fn(), loading: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/event', () => ({
  useEventDecisions: () => ({ approve: h.approve, reject: h.reject, requestChanges: vi.fn(), submitting: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-config', () => ({
  usePlatformConfiguration: () => ({ config: { requireCommentsOnRejection: true, requireCommentsOnChangesRequested: true } }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/reference-data', () => ({
  useReferenceData: () => ({ items: [] }),
}));

const ops = vi.hoisted(() => ({
  override: vi.fn().mockResolvedValue(undefined),
  assets: [] as any[],
  chargebacks: [] as any[],
}));
vi.mock('@pml.tickets/shared/api/admin/modules/media-ops', () => ({
  useMediaAssets: () => ({ assets: ops.assets, loading: false, error: undefined, refetch: vi.fn() }),
  useOverrideEventBanner: () => ({ override: ops.override, loading: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/finance-ops', () => ({
  useChargebackList: () => ({ chargebacks: ops.chargebacks, loading: false, error: undefined, refetch: vi.fn() }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({
  useCommissionRecords: () => ({ totals: { earned: '12.00', pending: '3.00', clawedBack: '0', cancelled: '0' }, loading: false, error: undefined, refetch: vi.fn() }),
}));

import { EventDetailPage } from '../EventDetailPage';

const event = (over = {}) => ({
  id: 'e1',
  title: 'Fixture Fest',
  description: 'Fixture description',
  status: 'PENDING_APPROVAL',
  published: false,
  publishedAt: null,
  featured: false,
  eventDateTime: '2030-01-01T10:00:00Z',
  endDateTime: null,
  organizerId: 'o1',
  organizerName: 'Fixture Org',
  organizationId: 'org1',
  organization: { id: 'org1', name: 'Fixture Org', businessEmail: null, businessPhone: null },
  locationName: 'Fixture Hall',
  locationAddress: null,
  cityName: 'Lusaka',
  categoryId: 'k1',
  category: { id: 'k1', name: 'Music' },
  location: { city: 'Lusaka', province: 'Lusaka', country: 'Zambia' },
  totalCapacity: 100,
  soldTickets: 0,
  availableTickets: 100,
  currency: 'ZMW',
  minTicketPrice: 50,
  maxTicketPrice: 80,
  refundPolicy: null,
  cancellationPolicy: null,
  bannerImageUrl: null,
  thumbnailImageUrl: null,
  galleryImages: null,
  submittedForApprovalAt: null,
  approvalDeadline: null,
  approvedAt: null,
  approvedBy: null,
  rejectedAt: null,
  rejectedBy: null,
  rejectionReason: null,
  isOverdue: false,
  approvalBlockers: [],
  createdAt: null,
  updatedAt: null,
  ticketTiers: [{ id: 't', name: 'General', code: 'GEN', price: 50, currency: 'ZMW', quantity: 100, soldQuantity: 10, isActive: true, isHidden: false, earlyBirdPrice: null, earlyBirdEndsAt: null }],
  ...over,
});
const ok = (e: unknown) => ({ event: e, loading: false, error: undefined, refetch: vi.fn() });

beforeEach(() => {
  vi.clearAllMocks();
  h.detail.mockReturnValue(ok(event()));
  h.escrow.mockReturnValue({ escrow: null, loading: false });
  h.approve.mockResolvedValue({ success: true, message: null, errors: [] });
  h.reject.mockResolvedValue({ success: true, message: null, errors: [] });
  h.feature.mockResolvedValue({ success: true, message: null });
});

describe('EventDetailPage', () => {
  it('renders the header strip, tabs and overview', () => {
    renderConsole(<EventDetailPage eventId="e1" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Fixture Fest' })).toBeInTheDocument();
    for (const t of ['Overview', 'Ticket tiers', 'Approval', 'Money', 'Media', 'Activity']) expect(screen.getByRole('tab', { name: t })).toBeInTheDocument();
    expect(screen.getByText('Event details')).toBeInTheDocument();
    expect(screen.getByText('Fixture description')).toBeInTheDocument();
    expect(screen.getByText('In escrow')).toBeInTheDocument();
  });

  it('goes back to all events and to the organization', () => {
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getByRole('button', { name: 'Fixture Org' }));
    expect(h.push).toHaveBeenCalledWith('/org/org1');
  });

  it('shows tiers read-only', () => {
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getByRole('tab', { name: 'Ticket tiers' }));
    expect(screen.getByText('Read only. Organizers manage tiers in their own app.')).toBeInTheDocument();
    expect(screen.getByText('General')).toBeInTheDocument();
  });

  it('blocks Approve while blockers remain and lists them on the Approval tab', () => {
    h.detail.mockReturnValue(ok(event({ approvalBlockers: ['NO_CAPACITY'] })));
    renderConsole(<EventDetailPage eventId="e1" />);
    expect(screen.getAllByRole('button', { name: 'Approve' })[0]).toBeDisabled();
    fireEvent.click(screen.getByRole('tab', { name: 'Approval' }));
    expect(screen.getByText('Capacity set')).toBeInTheDocument();
    expect(screen.getByText('Please check')).toBeInTheDocument();
  });

  it('requires a reason to reject', async () => {
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Reject' })[0]);
    const dlg = screen.getByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject event' }));
    expect(h.reject).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Reason shown to the organizer'), { target: { value: 'Not suitable' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject event' }));
    await vi.waitFor(() => expect(h.reject).toHaveBeenCalledWith('e1', 'Not suitable'));
  });

  it('features a published event', async () => {
    h.detail.mockReturnValue(ok(event({ status: 'PUBLISHED' })));
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Feature event' })[0]);
    await vi.waitFor(() => expect(h.feature).toHaveBeenCalledWith('e1', true));
  });

  it('opens the escrow account for finance-visible roles', () => {
    h.detail.mockReturnValue(ok(event({ status: 'PUBLISHED', soldTickets: 5 })));
    h.escrow.mockReturnValue({ escrow: { id: 'x', accountNumber: 'ESC-1', eventId: 'e1', currentBalance: 100, totalDeposits: 200, totalWithdrawals: 0, totalRefunds: 0, totalCommissions: 20, pendingWithdrawals: 0, currency: 'ZMW', status: 'ACTIVE' }, loading: false });
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Escrow account' })[0]);
    expect(h.push).toHaveBeenCalledWith('/finance/escrow?event=e1');
  });

  it('shows event media and overrides the banner with a reason', async () => {
    ops.assets = [{ id: 'm1', fileName: 'crowd.jpg', title: null, altText: 'Crowd', url: 'https://cdn.test/crowd.jpg', status: 'ACTIVE' }];
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getByRole('tab', { name: 'Media' }));
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByRole('img', { name: 'Crowd' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Use as banner'), { target: { value: 'm1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Override banner…' }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Original was inappropriate' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Replace banner' }));
    await vi.waitFor(() => expect(ops.override).toHaveBeenCalledWith('e1', 'Original was inappropriate', 'm1'));
    ops.assets = [];
  });

  it('shows the empty media state', () => {
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getByRole('tab', { name: 'Media' }));
    expect(screen.getByText(/No images uploaded yet/)).toBeInTheDocument();
  });

  it('money tab shows commission totals and chargebacks for the event', () => {
    ops.chargebacks = [{ id: 'c1', chargebackId: 'CB-9', chargebackAmount: '250', status: 'DISPUTED', responseDeadline: '2030-01-05T00:00:00Z' }];
    renderConsole(<EventDetailPage eventId="e1" />);
    fireEvent.click(screen.getByRole('tab', { name: 'Money' }));
    expect(screen.getByText('Commission earned')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Chargebacks for this event' })).toHaveTextContent('CB-9');
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    ops.chargebacks = [];
  });

  it('shows loading, error and not found', () => {
    h.detail.mockReturnValue({ event: null, loading: true, error: undefined, refetch: vi.fn() });
    const a = renderConsole(<EventDetailPage eventId="e1" />);
    expect(document.querySelector('.m3-skeleton')).not.toBeNull();
    a.unmount();
    h.detail.mockReturnValue({ event: null, loading: false, error: new Error('x'), refetch: vi.fn() });
    const b = renderConsole(<EventDetailPage eventId="e1" />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    b.unmount();
    h.detail.mockReturnValue({ event: null, loading: false, error: undefined, refetch: vi.fn() });
    renderConsole(<EventDetailPage eventId="e1" />);
    expect(screen.getByText('Event not found')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'All events' }));
    expect(h.push).toHaveBeenCalledWith('/events/all');
  });
});
