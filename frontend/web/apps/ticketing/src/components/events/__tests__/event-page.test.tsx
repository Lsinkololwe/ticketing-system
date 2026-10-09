// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { EventPageRow, EventTierRow } from '@pml.tickets/shared';

const push = vi.fn();
const reserveTickets = vi.fn();
const validate = vi.fn();
const unlock = vi.fn();
const RULES = {
  reservationHoldMinutes: 12, reservationGraceMinutes: 5, refundCutoffHours: 48, maxTicketsPerBooking: 3,
  refundPolicies: [{ code: 'MODERATE', label: 'Moderate', summary: 'Full refund until 7 days before.', rules: [{ daysBefore: 7, percent: 100 }] }],
};
const saveCartIntent = vi.fn();
let authed = true;
let pageState: { event: EventPageRow | null; loading: boolean; error: unknown } = { event: null, loading: false, error: null };

vi.mock('next/navigation', () => ({ useRouter: () => ({ push }), usePathname: () => '/events/e1' }));
vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@/lib/auth/session-context', () => ({ useBuyerAuth: () => ({ authenticated: authed, user: null, logout: vi.fn() }) }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useDiscoverEvents: () => ({ events: [], total: 0, hasNext: false, loading: false, error: undefined, refetch: vi.fn(), loadMore: vi.fn() }),
  useReserveTickets: () => ({ reserveTickets }),
  useEventPage: () => ({ ...pageState, refetch: vi.fn() }),
  usePromoValidation: () => ({ loading: false, validate }),
  useUnlockTier: () => ({ loading: false, unlock }),
  usePlatformRules: () => ({ rules: RULES, loading: false, error: undefined, refetch: vi.fn() }),
}));
vi.mock('@/lib/identity/client', () => ({ saveCartIntent: (...a: unknown[]) => saveCartIntent(...a) }));

import { EventClient } from '../EventClient';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const future = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString();
const tier = (o: Partial<EventTierRow>): EventTierRow => ({
  id: 't1', name: 'General', code: 'GEN', description: null, price: 150, originalPrice: null, earlyBirdPrice: null, earlyBirdEndsAt: null,
  salesStartAt: null, salesEndAt: null, currency: 'ZMW', quantity: 100, soldQuantity: 10, availableQuantity: 90, minPerOrder: 1, maxPerOrder: 4,
  benefits: ['Standard entry'], isActive: true, isHidden: false, sortOrder: 0, ...o,
});
const EVENT: EventPageRow = {
  id: 'e1', title: 'Fixture Fest', description: 'An evening of fixture music.', status: 'PUBLISHED', featured: false,
  eventDateTime: future(20), endDateTime: future(21), cityName: 'Lusaka', locationName: 'Fixture Grounds', locationAddress: '1 Test Road',
  bannerImageUrl: null, galleryImages: null, organizerName: 'Test Organizer Ltd', soldTickets: 10, totalCapacity: 100, availableTickets: 90,
  minTicketPrice: 150, maxTicketPrice: 600, currency: 'ZMW', soldOut: false, category: { id: 'c1', name: 'Music' },
  refundPolicy: 'MODERATE', cancellationPolicy: null, termsAndConditions: null, isVirtual: false, isFreeEvent: false,
  accessibility: { wheelchairAccessible: true, wheelchairSeatsAvailable: 6, signLanguageInterpreter: false, hearingLoopAvailable: false, accessibleParking: true, accessibleRestrooms: true, assistanceDogsAllowed: true, additionalNotes: 'Step-free north door.' },
  ageRestriction: '16 and over.', doorsOpenAt: future(20), faqs: [{ question: 'Is there parking?', answer: 'Yes, on site.' }],
  runningOrder: [{ time: '18:00', title: 'Opening act' }], gettingThere: 'Minibuses from the city centre.', parkingInfo: 'Paid lot.', bagPolicy: 'Small bags only.',
  organization: { id: 'o1', verified: true, publishedEventCount: 3 },
  ticketTiers: [tier({}), tier({ id: 't2', name: 'VIP', price: 600, sortOrder: 1, availableQuantity: 0, benefits: ['Lounge'] }), tier({ id: 'th', name: 'Secret', isHidden: true })],
};

const renderIt = () => render(<SnackbarProvider><EventClient id="e1" /></SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  authed = true;
  pageState = { event: EVENT, loading: false, error: null };
});

describe('EventClient', () => {
  it('renders hero, facts, tiers (hidden ones excluded) and the empty summary', () => {
    renderIt();
    expect(screen.getByRole('heading', { level: 1, name: 'Fixture Fest' })).toBeInTheDocument();
    expect(screen.getAllByText('Moderate').length).toBeGreaterThan(0);
    expect(screen.getByRole('group', { name: 'General' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'VIP' })).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Secret' })).toBeNull();
    expect(screen.getByText('Not available')).toBeInTheDocument(); // VIP sold out
    expect(screen.getByText(/Choose ticket quantities/)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Reserve tickets' })[0]).toBeDisabled();
  });
  it('steps quantity up to the per-booking cap and totals the order', () => {
    renderIt();
    const add = screen.getByRole('button', { name: 'Add one General ticket' });
    for (let i = 0; i < 6; i++) fireEvent.click(add);
    expect(within(screen.getByRole('group', { name: 'General quantity' })).getByRole('status')).toHaveTextContent('3');
    expect(add).toBeDisabled();
    const side = screen.getByRole('complementary', { name: 'Your tickets' });
    expect(within(side).getByText('General × 3')).toBeInTheDocument();
    expect(side).toHaveTextContent('K 450');
    fireEvent.click(screen.getByRole('button', { name: 'Remove one General ticket' }));
    expect(side).toHaveTextContent('K 300');
  });
  it('reserves for a signed-in buyer, sending an idempotency key, then goes to checkout', async () => {
    reserveTickets.mockResolvedValue({ data: { reserveTickets: { id: 'r1', expiresAt: future(1) } } });
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Add one General ticket' }));
    fireEvent.click(within(screen.getByRole('complementary', { name: 'Your tickets' })).getByRole('button', { name: 'Reserve tickets' }));
    await waitFor(() => expect(push).toHaveBeenCalledWith('/events/e1/book?r=r1'));
    const input = reserveTickets.mock.calls[0][0];
    expect(input.selections).toEqual([{ ticketTierId: 't1', quantity: 1 }]);
    expect(input.idempotencyKey).toMatch(/[0-9a-f-]{8,}/);
  });
  it('parks the selection and goes to checkout identification when signed out', async () => {
    authed = false;
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Add one General ticket' }));
    fireEvent.click(within(screen.getByRole('complementary', { name: 'Your tickets' })).getByRole('button', { name: 'Reserve tickets' }));
    await waitFor(() => expect(push).toHaveBeenCalledWith('/events/e1/book'));
    expect(saveCartIntent).toHaveBeenCalledWith('e1', { t1: 1 });
    expect(reserveTickets).not.toHaveBeenCalled();
  });
  it('shows a reservation error from the server', async () => {
    reserveTickets.mockRejectedValue({ graphQLErrors: [{ message: 'boom' }], message: 'boom' });
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Add one General ticket' }));
    fireEvent.click(within(screen.getByRole('complementary', { name: 'Your tickets' })).getByRole('button', { name: 'Reserve tickets' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
  it('validates a promo code with the backend and shows the discount', async () => {
    validate.mockResolvedValue({ valid: true, discountAmount: 50, errorMessage: null });
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Add one General ticket' }));
    fireEvent.change(screen.getByLabelText('Promo code'), { target: { value: 'save50' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('SAVE50 applied: K 50 off.')).toBeInTheDocument();
    expect(validate).toHaveBeenCalledWith('SAVE50', 'e1', 150);
    expect(screen.getByRole('complementary', { name: 'Your tickets' })).toHaveTextContent('K 100');
  });
  it('rejects an invalid promo code with the backend message', async () => {
    validate.mockResolvedValue({ valid: false, discountAmount: 0, errorMessage: 'This promo code has expired.' });
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Add one General ticket' }));
    fireEvent.change(screen.getByLabelText('Promo code'), { target: { value: 'old' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('This promo code has expired.')).toBeInTheDocument();
  });
  it('switches information tabs', () => {
    renderIt();
    expect(screen.getByText('Test Organizer Ltd')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Schedule' }));
    expect(screen.getByText('Opening act')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Venue and access' }));
    expect(screen.getByText('Wheelchair accessible, 6 wheelchair spaces')).toBeInTheDocument();
    expect(screen.getByText('Step-free north door.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open in maps' })).toHaveAttribute('href', expect.stringContaining('google.com/maps'));
    fireEvent.click(screen.getByRole('tab', { name: 'Good to know' }));
    expect(screen.getByText('Refund policy: Moderate')).toBeInTheDocument();
    expect(screen.getByText('How do I get my tickets?')).toBeInTheDocument();
  });
  it('opens the share dialog', () => {
    renderIt();
    fireEvent.click(screen.getByRole('button', { name: 'Share' }));
    expect(screen.getByRole('dialog', { name: 'Share this event' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'WhatsApp' })).toHaveAttribute('href', expect.stringContaining('wa.me'));
  });
  it('has designed loading, error and not-found states', () => {
    pageState = { event: null, loading: true, error: null };
    const a = renderIt();
    expect(screen.getByLabelText('Loading event')).toBeInTheDocument();
    a.unmount();
    pageState = { event: null, loading: false, error: { message: 'down' } };
    const b = renderIt();
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
    b.unmount();
    pageState = { event: null, loading: false, error: null };
    renderIt();
    expect(screen.getByText('We could not find that event')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to events' })).toHaveAttribute('href', '/');
  });

  it('shows the organizer extras, verified badge and event count', () => {
    renderIt();
    expect(screen.getByText('Verified organizer')).toBeInTheDocument();
    expect(screen.getByText('3 events on Showstop')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Schedule' }));
    expect(screen.getByText('Opening act')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Venue and access' }));
    expect(screen.getByText('Minibuses from the city centre.')).toBeInTheDocument();
    expect(screen.getByText('Paid lot.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Good to know' }));
    expect(screen.getByText('Is there parking?')).toBeInTheDocument();
  });
  it('limits tickets per booking and words the hold from the platform rules', () => {
    renderIt();
    expect(screen.getAllByText(/Maximum 3 per booking/).length).toBeGreaterThan(0);
    expect(screen.getByText(/held for 12 minutes/)).toBeInTheDocument();
  });
  it('unlocks a hidden tier with an access code', async () => {
    unlock.mockResolvedValue({ ok: true, tier: tier({ id: 'th', name: 'Secret', isHidden: true }) });
    renderIt();
    fireEvent.change(screen.getByLabelText('Access code'), { target: { value: 'OPEN' } });
    fireEvent.click(screen.getByRole('button', { name: 'Unlock' }));
    await waitFor(() => expect(screen.getByRole('group', { name: 'Secret' })).toBeInTheDocument());
    expect(screen.getByText('Code accepted: the Secret tier is now unlocked.')).toBeInTheDocument();
    expect(unlock).toHaveBeenCalledWith('e1', 'OPEN');
  });
  it('says a wrong access code is not valid', async () => {
    unlock.mockResolvedValue({ ok: false, code: 'INVALID' });
    renderIt();
    fireEvent.change(screen.getByLabelText('Access code'), { target: { value: 'NOPE' } });
    fireEvent.click(screen.getByRole('button', { name: 'Unlock' }));
    expect(await screen.findByText("That access code isn't valid for this event.")).toBeInTheDocument();
  });
});
