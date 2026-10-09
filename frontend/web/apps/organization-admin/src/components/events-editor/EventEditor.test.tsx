import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';
import { RestProblemError } from '@pml.tickets/shared';
import type { EditorEventData } from '@/lib/api/event-editor';

const replace = vi.fn();
const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, push, back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn(), forward: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/events/new',
}));

const createEvent = vi.fn();
const updateEvent = vi.fn();
const updateAccessibility = vi.fn();
const createTier = vi.fn();
const updateTier = vi.fn();
const deleteTier = vi.fn();
const reorderTiers = vi.fn();
let eventState: { event: EditorEventData | null; loading: boolean; error: Error | null };
const refetch = vi.fn();

vi.mock('@/lib/api/event-editor', () => ({
  useEditorEvent: () => ({ ...eventState, refetch }),
  useEditorReferenceData: () => ({
    categories: [{ id: 'c1', name: 'Fixture Music', code: 'M', isActive: true }],
    provinces: [{ id: 'p1', name: 'Fixture Province', code: 'FP' }],
    cities: [{ id: 'ci1', name: 'Fixture City', province: 'Fixture Province', provinceId: 'p1' }],
    loading: false,
    error: undefined,
  }),
  useEditorMutations: () => ({
    createEvent, updateEvent, updateAccessibility, submitForApproval: vi.fn(), cancelScheduledPublish: vi.fn(), createTier, updateTier, deleteTier, reorderTiers,
  }),
}));
vi.mock('@/lib/api/platform', () => ({
  usePlatformRules: () => ({
    rules: {
      commissionRate: 5, commissionDefault: 5, maxTicketsPerBooking: 8, refundCutoffHours: 24, reservationHoldMinutes: 10, reservationGraceMinutes: 5, rescheduleLimit: 3,
      refundPolicies: [{ code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund.', rules: [] }],
      approval: { slaHours: 48, requireCommentsOnChangesRequested: true },
    },
  }),
  useReferenceList: (type: string) => ({ items: type === 'TICKET_TIER_CATEGORY' ? [{ code: 'GENERAL', name: 'General admission' }] : [{ code: 'ALL', name: 'All ages' }] }),
}));
vi.mock('@/lib/api/media', () => ({ useMyMedia: () => ({ items: [], loading: false, error: null, hasMore: false }) }));
vi.mock('@pml.tickets/shared/api/organization-admin/modules/events', () => ({
  usePublishEvent: () => ({ publish: vi.fn().mockResolvedValue({ success: true, message: null, errors: [] }) }),
}));

import { EventEditor } from './EventEditor';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

const serverEvent = {
  id: 'e1', title: 'Fixture Fest', description: 'd', status: 'DRAFT', categoryId: 'c1',
  eventDateTime: '2031-01-01T10:00:00.000Z', endDateTime: '2031-01-01T12:00:00.000Z', bannerImageUrl: null,
  isVirtual: false, isFreeEvent: false, virtualEventUrl: null, totalCapacity: 100, soldTickets: 0,
  refundPolicy: 'FLEXIBLE', cancellationPolicy: null, termsAndConditions: null, rejectionReason: null, bannerAltText: null, tagline: null, ageRestriction: null, doorsOpenAt: null, galleryImages: [], gettingThere: null, parkingInfo: null, bagPolicy: null,
  publishAt: null, publishScheduled: false, publishedAt: null, submittedForApprovalAt: null, approvalDeadline: null, approvedAt: null, rejectedAt: null, faqs: [], runningOrder: [], checkoutSettings: null,
  location: { name: 'Fixture Hall', address: 'Road', city: 'Fixture City', province: null, country: 'Zambia' },
  accessibility: null,
  ticketTiers: [{ id: 't1', code: 'GEN1', name: 'General', description: null, price: '100', currency: 'ZMW', quantity: 50, soldQuantity: 0, minPerOrder: 1, maxPerOrder: 8, benefits: [], salesStartAt: null, salesEndAt: null, earlyBirdPrice: null, earlyBirdEndsAt: null, sortOrder: 0, isActive: true, isHidden: false, accessCode: null, category: 'GENERAL' }],
} as unknown as EditorEventData;

const wrap = (ui: React.ReactElement) => render(<SnackbarProvider>{ui}</SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  eventState = { event: null, loading: false, error: null };
  refetch.mockResolvedValue({ data: { event: serverEvent } });
  createEvent.mockResolvedValue({ data: { createEvent: { id: 'new1', status: 'DRAFT' } } });
});

describe('EventEditor container', () => {
  it('create: validates before calling the API, jumping to the first problem', async () => {
    wrap(<EventEditor />);
    fireEvent.change(screen.getByLabelText(/Event title/), { target: { value: 'ab' } });
    fireEvent.click(within_saveBar());
    await waitFor(() => expect(screen.getAllByText('Add an event title of at least 3 characters.').length).toBeGreaterThan(0));
    expect(createEvent).not.toHaveBeenCalled();
  });

  it('create: saves a draft and moves to the edit route', async () => {
    wrap(<EventEditor />);
    fireEvent.change(screen.getByLabelText(/Event title/), { target: { value: 'Fixture Fest' } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'c1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.change(screen.getByLabelText(/Starts/), { target: { value: '2031-01-01T10:00' } });
    fireEvent.change(screen.getByLabelText(/Ends/), { target: { value: '2031-01-01T12:00' } });
    fireEvent.click(within_saveBar());
    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(1));
    expect(createEvent.mock.calls[0]?.[0]).toMatchObject({ title: 'Fixture Fest', categoryId: 'c1', ticketTiers: [] });
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/events/new1/edit?tab=when'));
  });

  it('edit: fills the form from the event and saves the tier diff', async () => {
    eventState = { event: serverEvent, loading: false, error: null };
    wrap(<EventEditor eventId="e1" />);
    const title = await screen.findByLabelText(/Event title/);
    expect(title).toHaveValue('Fixture Fest');
    fireEvent.change(title, { target: { value: 'Fixture Fest 2' } });
    fireEvent.click(within_saveBar());
    await waitFor(() => expect(updateEvent).toHaveBeenCalledWith('e1', expect.objectContaining({ title: 'Fixture Fest 2' })));
    expect(updateAccessibility).toHaveBeenCalled();
    expect(updateTier).toHaveBeenCalledWith('t1', expect.objectContaining({ name: 'General' }));
  });

  it('edit: maps a server refusal onto the form and saves only once on a double click', async () => {
    eventState = { event: serverEvent, loading: false, error: null };
    updateEvent.mockRejectedValueOnce(new RestProblemError(409, { status: 409, errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' }));
    wrap(<EventEditor eventId="e1" />);
    fireEvent.change(await screen.findByLabelText(/Event title/), { target: { value: 'Fixture Fest 3' } });
    const save = within_saveBar();
    fireEvent.click(save);
    fireEvent.click(save);
    expect(await screen.findByText('Those tickets have sold out.')).toBeInTheDocument();
    expect(updateEvent).toHaveBeenCalledTimes(1);
  });

  it('edit: shows loading, then a not found error', () => {
    eventState = { event: null, loading: true, error: null };
    const { unmount } = wrap(<EventEditor eventId="e1" />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    unmount();
    eventState = { event: null, loading: false, error: null };
    wrap(<EventEditor eventId="e1" />);
    expect(screen.getByRole('alert')).toHaveTextContent('could not be found');
  });

  it('Back goes to the events list when nothing changed', () => {
    wrap(<EventEditor />);
    fireEvent.click(screen.getByRole('button', { name: 'Back to events' }));
    expect(push).toHaveBeenCalledWith('/events');
  });
});

function within_saveBar(): HTMLElement {
  // The save bar appears once the form is dirty; before that, use the step bar's final action via the bar region.
  const region = screen.queryByRole('region', { name: 'Unsaved changes' });
  if (region) return region.querySelector('button:last-of-type') as HTMLElement;
  throw new Error('save bar not shown');
}
