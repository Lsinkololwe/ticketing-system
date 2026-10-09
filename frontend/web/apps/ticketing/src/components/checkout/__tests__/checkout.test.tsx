// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReservationRow } from '@pml.tickets/shared';

const replace = vi.fn();
const reserve = vi.fn();
const payRes = vi.fn();
const cancelRes = vi.fn();
let authed = true;
let reservation: ReservationRow | null = null;
let loadError: unknown = null;
let booking: unknown = null;

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule());
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }), usePathname: () => '/events/e1/book' }));
vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@/components/identify/IdentifyStep', () => ({ IdentifyStep: ({ returnTo }: { returnTo: string }) => <div data-testid="identify" data-return={returnTo} /> }));
vi.mock('@/lib/auth/session-context', () => ({ useBuyerAuth: () => ({ authenticated: authed, user: authed ? { id: 'u1', givenName: 'Chanda', familyName: 'Mwansa', email: '' } : null, logout: vi.fn() }) }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useEventPage: () => ({ event: { id: 'e1', title: 'Fixture Fest', eventDateTime: '2026-12-01T15:00:00Z', endDateTime: '2026-12-01T19:00:00Z', locationName: 'Grounds', locationAddress: 'Road', cityName: 'Lusaka', bannerImageUrl: null }, loading: false, error: null }),
  useCancelReservation: () => ({ loading: false, cancel: (...a: unknown[]) => cancelRes(...a) }),
  useReserveTickets: () => ({ reserveTickets: reserve }),
  usePayReservation: () => ({ payReservation: payRes, loading: false }),
  useReservation: () => ({ reservation, error: loadError }),
  useBookingForReservation: () => ({ booking, refetch: vi.fn() }),
  usePlatformRules: () => ({ rules: { reservationHoldMinutes: 10, reservationGraceMinutes: 5 }, loading: false, error: undefined, refetch: vi.fn() }),
}));
vi.mock('@/lib/identity/client', () => ({ saveCartIntent: vi.fn() }));
vi.mock('@/lib/hold', () => ({ writeHold: vi.fn() }));

import { CheckoutClient } from '../CheckoutClient';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const res = (o: Partial<ReservationRow> = {}): ReservationRow => ({
  id: 'r1', eventId: 'e1', totalAmount: 300, currency: 'ZMW', status: 'HELD', expiresAt: new Date(Date.now() + 9 * 60_000).toISOString(),
  remainingSeconds: 540, discountAmount: 0, promoCodeApplied: null, paymentIntentId: null, confirmedAt: null, releasedAt: null, failedAt: null, failureReason: null,
  items: [{ ticketTierId: 't1', tierName: 'General', quantity: 2, unitPrice: 150, subtotal: 300 }], ...o,
});
const mount = (p: Partial<React.ComponentProps<typeof CheckoutClient>> = {}) =>
  render(<SnackbarProvider><CheckoutClient eventId="e1" initialQuantities={{}} reservationId={null} {...p} /></SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  authed = true;
  reservation = null;
  loadError = null;
  booking = null;
});

describe('CheckoutClient', () => {
  it('asks a signed-out buyer with parked tickets to identify, returning to checkout', () => {
    authed = false;
    mount({ initialQuantities: { t1: 2 } });
    expect(screen.getByText('Where should we send your tickets?')).toBeInTheDocument();
    expect(screen.getByTestId('identify')).toHaveAttribute('data-return', '/events/e1/book');
    expect(screen.getByRole('list', { name: 'Checkout progress' })).toBeInTheDocument();
  });
  it('shows an empty state when there is nothing to check out', () => {
    mount();
    expect(screen.getByText('Choose your tickets first')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Choose tickets again' })).toHaveAttribute('href', '/events/e1');
  });
  it('reserves parked tickets for a signed-in buyer with an idempotency key and resumes by id', async () => {
    reserve.mockResolvedValue({ data: { reserveTickets: { id: 'r9', expiresAt: new Date(Date.now() + 600_000).toISOString() } } });
    mount({ initialQuantities: { t1: 2 } });
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/events/e1/book?r=r9'));
    expect(reserve.mock.calls[0][0]).toMatchObject({ eventId: 'e1', selections: [{ ticketTierId: 't1', quantity: 2 }] });
    expect(reserve.mock.calls[0][0].idempotencyKey).toBeTruthy();
  });
  it('walks Reserve, Contact and Pay with the hold timer and summary', async () => {
    reservation = res();
    mount({ reservationId: 'r1' });
    await screen.findByText('Your tickets are reserved');
    expect(screen.getAllByRole('timer')[0]).toHaveTextContent(/Tickets held for \d:\d\d/);
    const summary = screen.getByRole('complementary', { name: 'Order summary' });
    expect(summary).toHaveTextContent('General × 2');
    expect(summary).toHaveTextContent('K 300');
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByText('Contact details')).toBeInTheDocument();
    expect(screen.getByText('Chanda Mwansa')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }));
    expect(await screen.findByText('Pay with mobile money')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pay K 300' })).toBeInTheDocument();
  });
  it('validates the mobile money number, then starts the prompt and waits for approval', async () => {
    reservation = res();
    payRes.mockResolvedValue({});
    mount({ reservationId: 'r1' });
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }));
    fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }));
    fireEvent.change(screen.getByLabelText('Mobile money number'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Pay K 300' }));
    expect((await screen.findAllByText(/Enter a valid MTN Mobile Money number/)).length).toBeGreaterThan(0);
    expect(payRes).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Mobile money number'), { target: { value: '96 123 4567' } });
    fireEvent.click(screen.getByRole('button', { name: 'Pay K 300' }));
    await waitFor(() => expect(payRes).toHaveBeenCalledWith({ reservationId: 'r1', phoneNumber: '+260961234567' }));
    expect(await screen.findByText('Approve the payment on your phone')).toBeInTheDocument();
    expect(screen.getByText('+260 96 1234567')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Resend prompt in/ })).toBeDisabled();
  });
  it('keeps the buyer on Pay with a declined note when the provider refuses the request', async () => {
    reservation = res();
    payRes.mockRejectedValue({ message: 'rejected', graphQLErrors: [{ message: 'rejected' }] });
    mount({ reservationId: 'r1' });
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }));
    fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }));
    fireEvent.change(screen.getByLabelText('Mobile money number'), { target: { value: '0971234567' } });
    fireEvent.click(screen.getByRole('button', { name: 'Pay K 300' }));
    expect(await screen.findByText('Payment declined.')).toBeInTheDocument();
    expect(screen.getByText('Pay with mobile money')).toBeInTheDocument();
  });
  it('resumes at the approval step when a payment is already in progress', async () => {
    reservation = res({ paymentIntentId: 'pi1' });
    mount({ reservationId: 'r1' });
    expect(await screen.findByText('Approve the payment on your phone')).toBeInTheDocument();
  });
  it('releases the reservation after confirming', async () => {
    reservation = res();
    cancelRes.mockResolvedValue({});
    mount({ reservationId: 'r1' });
    fireEvent.click(await screen.findByRole('button', { name: 'Release reservation' }));
    fireEvent.click(screen.getAllByRole('button', { name: 'Release reservation' }).pop()!);
    await waitFor(() => expect(cancelRes).toHaveBeenCalledWith('r1'));
    expect(await screen.findByRole('heading', { name: 'Reservation released' })).toBeInTheDocument();
  });
  it('shows confirmation with the issued tickets and QR codes', async () => {
    reservation = res({ status: 'CONFIRMED', confirmedAt: new Date().toISOString() });
    booking = { bookingNumber: 'BK-2026-00000001', status: 'CONFIRMED', totalAmount: 300, tickets: [{ id: 'k1', ticketNumber: 'TKT-1', eventId: 'e1', eventTitle: 'Fixture Fest', eventDate: '2026-12-01T15:00:00Z', eventLocationName: 'Grounds', ticketCategoryName: 'General', price: 150, currency: 'ZMW', status: 'ISSUED', qrCode: 'QR-TKT-1-AAAA', barcode: 'b', purchaseDate: new Date().toISOString(), validUntil: null }] };
    mount({ reservationId: 'r1' });
    expect(await screen.findByText('Your booking is confirmed!')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'QR code for ticket TKT-1' })).toBeInTheDocument();
    expect(screen.getByText('BK-2026-00000001')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View my tickets' })).toHaveAttribute('href', '/my-tickets');
  });
  it('tells the buyer a payment that arrived after the hold was refunded automatically', async () => {
    reservation = res({ status: 'EXPIRED', paymentIntentId: 'pi1' });
    booking = { bookingNumber: 'BK-2026-00000002', status: 'PAID_AFTER_EXPIRY_AUTO_REFUNDED', totalAmount: 300, tickets: [] };
    mount({ reservationId: 'r1' });
    expect(await screen.findByRole('heading', { name: 'Payment received after your hold ended' })).toBeInTheDocument();
    expect(screen.getByText(/automatically refunded the full amount/)).toBeInTheDocument();
  });
  it.each([
    ['EXPIRED', 'Your hold has expired'],
    ['RELEASED', 'Reservation released'],
    ['FAILED', 'We could not complete this booking'],
  ] as const)('shows the %s end state', async (status, title) => {
    reservation = res({ status, failureReason: 'Tier sold out' });
    mount({ reservationId: 'r1' });
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to events' })).toHaveAttribute('href', '/');
  });
  it('shows the hold ended as expired when the clock ran out before any payment', async () => {
    reservation = res({ expiresAt: new Date(Date.now() - 1000).toISOString() });
    mount({ reservationId: 'r1' });
    expect(await screen.findByText('Your hold has expired')).toBeInTheDocument();
  });
  it('shows a loading state, then an error when the reservation cannot load', () => {
    const a = mount({ reservationId: 'r1' });
    expect(screen.getByText('Loading your reservation…')).toBeInTheDocument();
    a.unmount();
    loadError = { message: 'down' };
    mount({ reservationId: 'r1' });
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
  it('is advanced by time without crashing', async () => {
    reservation = res();
    mount({ reservationId: 'r1' });
    await act(async () => { await new Promise((r) => setTimeout(r, 20)); });
    expect(screen.getByText('Checkout')).toBeInTheDocument();
  });
});
