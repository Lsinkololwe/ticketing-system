// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { groupBookings, splitBookings } from '../group';

let bookings: unknown[] = [];
let outgoing: unknown[] = [];
let incoming: unknown[] = [];
const cancelTransfer = vi.fn().mockResolvedValue({});
const acceptTransfer = vi.fn().mockResolvedValue({});
const initiate = vi.fn().mockResolvedValue({});
const lookup = vi.fn();
const resend = vi.fn();
const cancelRefund = vi.fn().mockResolvedValue({});
let ticketsState = { loading: false, error: null as unknown };
let refunds: unknown[] = [];
let reminders: Array<{ id: string; ticketId: string; eventId: string; status: string }> = [];
const setFor = vi.fn().mockResolvedValue({});
const cancelFor = vi.fn().mockResolvedValue({});
const createRefund = vi.fn();
const loadQuote = vi.fn();
let quote: unknown = null;

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule());
vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useMyBookings: () => ({ bookings, total: bookings.length, ...ticketsState, refetch: vi.fn() }),
  useMyTicketTransfers: (o: { direction?: string }) => ({ transfers: o?.direction === 'INCOMING' ? incoming : outgoing, loading: false, error: null, refetch: vi.fn() }),
  useTicketTransferActions: () => ({ busy: false, cancel: cancelTransfer, accept: acceptTransfer, decline: vi.fn(), initiate }),
  useTransferRecipient: () => ({ loading: false, lookup }),
  useResendTicket: () => ({ loading: false, resend }),
  useCancelRefundRequest: () => ({ loading: false, cancel: cancelRefund }),
  usePlatformRules: () => ({ rules: { refundCutoffHours: 24, refundPolicies: [{ code: 'FULL_REFUND', label: 'Flexible', summary: 'Full refund early.', rules: [] }] }, loading: false, error: undefined, refetch: vi.fn() }),
  useReminders: () => ({ reminders, setFor, cancelFor }),
  useNotificationPrefs: () => ({ prefs: { reminderHoursBefore: 24 }, loading: false, error: null, refetch: vi.fn() }),
  useMyRefunds: () => ({ refunds, loading: false, error: null, refetch: vi.fn() }),
  useRefundQuote: () => ({ quote, loading: false, error: null, load: loadQuote }),
  useCreateRefund: () => ({ loading: false, create: createRefund }),
}));

import { TicketsClient } from '../TicketsClient';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const future = new Date(Date.now() + 10 * 86_400_000).toISOString();
const past = new Date(Date.now() - 10 * 86_400_000).toISOString();
const tk = (id: string, o: Record<string, unknown> = {}) => ({
  id, ticketNumber: `TKT-${id}`, eventId: 'e1', eventTitle: 'Fixture Fest', eventDate: future, eventLocationName: 'Grounds', ticketCategoryName: 'General',
  price: 150, currency: 'ZMW', status: 'ISSUED', transferPending: false, transferCount: 0, bookingNumber: 'BK-1', qrCode: `QR-TKT-${id}-AAAA`, barcode: 'b', purchaseDate: '2026-09-20T10:00:30Z', validUntil: null, ...o,
});
const bk = (id: string, number: string, tix: unknown[], o: Record<string, unknown> = {}) => ({
  id, bookingNumber: number, reservationId: `r${id}`, eventId: 'e1', eventTitle: 'Fixture Fest', eventDate: future, status: 'CONFIRMED', ticketCount: tix.length,
  totalAmount: 150 * tix.length, currency: 'ZMW', refundedAmount: 0, lateRefundStatus: null, contactName: null, contactEmail: null, contactPhone: null,
  createdAt: '2026-09-20T10:00:30Z', confirmedAt: '2026-09-20T10:00:30Z', items: [], tickets: tix, ...o,
});
const mount = () => render(<SnackbarProvider><TicketsClient accountId="u1" holder="Chanda Mwansa" /></SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  bookings = [bk('b1', 'BK-1', [tk('1'), tk('2'), tk('4', { status: 'REFUND_PENDING' })]), bk('b2', 'BK-2', [tk('3', { eventId: 'e2', eventTitle: 'Old Show', eventDate: past })], { eventId: 'e2', eventTitle: 'Old Show', eventDate: past })];
  outgoing = [];
  incoming = [];
  ticketsState = { loading: false, error: null };
  refunds = [];
  reminders = [];
  quote = null;
});

describe('groupBookings', () => {
  it('makes one card per booking, keeps the booking number and splits upcoming from past', () => {
    const g = groupBookings(bookings as never);
    expect(g).toHaveLength(2);
    const s = splitBookings(g);
    expect(s.upcoming[0].bookingNumber).toBe('BK-1');
    expect(s.upcoming[0].tickets).toHaveLength(3);
    expect(s.upcoming[0].total).toBe(450);
    expect(s.past[0].eventTitle).toBe('Old Show');
  });
  it('keeps a late payment that was refunded automatically, and drops bookings without tickets', () => {
    const g = groupBookings([bk('b3', 'BK-3', [], { status: 'PAID_AFTER_EXPIRY_AUTO_REFUNDED' }), bk('b4', 'BK-4', [], { status: 'EXPIRED' })] as never);
    expect(g).toHaveLength(1);
    expect(g[0].autoRefunded).toBe(true);
  });
});

describe('TicketsClient', () => {
  it('lists upcoming bookings with counts on the tabs', () => {
    mount();
    expect(screen.getByRole('tab', { name: /Upcoming 1/ })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: /Past 1/ })).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Booking for Fixture Fest' })).toBeInTheDocument();
    expect(screen.getByText('TKT-1')).toBeInTheDocument();
    expect(screen.getByText('Refund pending')).toBeInTheDocument();
  });
  it('shows the ticket QR, the code and the ID fallback', () => {
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Show QR' })[0]);
    const dlg = screen.getByRole('dialog', { name: 'Your ticket' });
    expect(within(dlg).getByRole('img', { name: 'QR code for ticket TKT-1' })).toBeInTheDocument();
    expect(within(dlg).getByLabelText('Ticket code')).toHaveTextContent('TKT-1');
    expect(within(dlg).getByText(/show a photo ID/)).toBeInTheDocument();
    expect(within(dlg).getByText('Chanda Mwansa')).toBeInTheDocument();
  });
  it('transfers a ticket: looks the recipient up, confirms, then sends the offer', async () => {
    lookup.mockResolvedValue({ displayName: 'Mulenga K.', maskedContact: '+260 97* ***123' });
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Transfer' })[0]);
    const dlg = screen.getByRole('dialog', { name: 'Transfer ticket' });
    fireEvent.change(within(dlg).getByLabelText("Recipient's mobile number"), { target: { value: '97 123 4567' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Continue' }));
    expect(await within(dlg).findByText(/Mulenga K\./)).toBeInTheDocument();
    expect(lookup).toHaveBeenCalledWith('WHATSAPP', '+260971234567');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Transfer ticket' }));
    await waitFor(() => expect(initiate).toHaveBeenCalledWith('1', 'WHATSAPP', '+260971234567', expect.any(String)));
  });
  it('says so when no account matches the number', async () => {
    lookup.mockResolvedValue(null);
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Transfer' })[0]);
    const dlg = screen.getByRole('dialog', { name: 'Transfer ticket' });
    fireEvent.change(within(dlg).getByLabelText("Recipient's mobile number"), { target: { value: '95 500 0000' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Continue' }));
    expect(await within(dlg).findByText(/no Showstop account for that number/)).toBeInTheDocument();
  });
  it('shows a pending transfer with the recipient and cancels it after confirming', async () => {
    bookings = [bk('b1', 'BK-1', [tk('1', { transferPending: true })])];
    outgoing = [{ id: 'tr1', ticketId: '1', toDisplayName: 'Mulenga K.', recipientMasked: '+260 97* ***123', status: 'PENDING' }];
    mount();
    expect(screen.getByText('Transfer pending')).toBeInTheDocument();
    expect(screen.getByText(/is pending until they accept it/)).toHaveTextContent('Mulenga K.');
    fireEvent.click(screen.getByRole('button', { name: 'Cancel transfer' }));
    const confirm = await screen.findAllByRole('button', { name: 'Cancel transfer' });
    fireEvent.click(confirm[confirm.length - 1]);
    await waitFor(() => expect(cancelTransfer).toHaveBeenCalledWith('tr1'));
  });
  it('lists offers made to the buyer and accepts one', async () => {
    incoming = [{ id: 'in1', ticketNumber: 'TKT-9', eventTitle: 'Gift Fest', fromDisplayName: 'Chola M.', note: null, expiresAt: future, status: 'PENDING' }];
    mount();
    expect(screen.getByRole('article', { name: 'Transfer of ticket TKT-9' })).toHaveTextContent('Chola M.');
    fireEvent.click(screen.getByRole('button', { name: 'Accept ticket' }));
    await waitFor(() => expect(acceptTransfer).toHaveBeenCalledWith('in1'));
  });
  it('resends a ticket and reports a missing verified contact', async () => {
    resend.mockResolvedValue({ status: 'NO_VERIFIED_CONTACT', channel: null, destination: null });
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Resend ticket' })[0]);
    await waitFor(() => expect(resend).toHaveBeenCalledWith('1'));
    expect(await screen.findByText(/no verified contact/)).toBeInTheDocument();
  });
  it('shows the booking number on each card', () => {
    mount();
    expect(screen.getByText('BK-1')).toBeInTheDocument();
  });
  it('requests a refund from the backend quote after choosing a reason', async () => {
    quote = { ticketId: '1', ticketNumber: 'TKT-1', eventId: 'e1', eventDate: future, originalAmount: 150, daysBeforeEvent: 10, refundPercentage: 100, refundAmount: 150, platformRetains: 0, policyApplied: 'FULL_REFUND', isEligible: true, ineligibleReason: null };
    createRefund.mockResolvedValue({});
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Request refund' })[0]);
    expect(loadQuote).toHaveBeenCalledWith('1');
    const dlg = screen.getByRole('dialog', { name: 'Request a refund' });
    expect(within(dlg).getByText('Eligible for a 100% refund')).toBeInTheDocument();
    expect(within(dlg).getByText('Flexible')).toBeInTheDocument();
    fireEvent.click(within(dlg).getByRole('button', { name: 'Request refund of K 150' }));
    expect(await within(dlg).findByText('Choose a reason for your request.')).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'CANNOT_ATTEND' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Request refund of K 150' }));
    // the select holds the platform's code; the request carries the reason in the platform's words
    await waitFor(() => expect(createRefund).toHaveBeenCalledWith('1', 'Cannot attend', expect.any(String)));
    expect(await screen.findByRole('tab', { name: /Refunds/, selected: true })).toBeInTheDocument();
  });
  it('explains why a ticket is not refundable', () => {
    quote = { ticketId: '1', ticketNumber: 'TKT-1', eventId: 'e1', eventDate: future, originalAmount: 150, daysBeforeEvent: 1, refundPercentage: 0, refundAmount: 0, platformRetains: 0, policyApplied: 'NO_REFUND', isEligible: false, ineligibleReason: 'Refund requests are closed for this event.' };
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Request refund' })[0]);
    const dlg = screen.getByRole('dialog', { name: 'Request a refund' });
    expect(within(dlg).getByText('Not eligible for a refund')).toBeInTheDocument();
    expect(within(dlg).getByText('Refund requests are closed for this event.')).toBeInTheDocument();
    expect(within(dlg).queryByLabelText('Reason')).toBeNull();
  });
  it('sets and removes the event reminder for every ticket in the booking', async () => {
    const view = mount();
    fireEvent.click(screen.getByRole('switch', { name: /Remind me 24 hours before/ }));
    await waitFor(() => expect(setFor).toHaveBeenCalledTimes(3));
    view.unmount();
    reminders = ['1', '2', '4'].map((t) => ({ id: `r${t}`, ticketId: t, eventId: 'e1', status: 'SCHEDULED' }));
    mount();
    const sw = screen.getByRole('switch', { name: /Remind me 24 hours before/ });
    expect(sw).toBeChecked();
    fireEvent.click(sw);
    await waitFor(() => expect(cancelFor).toHaveBeenCalledTimes(3));
  });
  it('shows past bookings and the refunds tab with progress', () => {
    refunds = [{ id: 'rr1', requestId: 'RR-1', ticketId: '4', ticketNumber: 'TKT-4', eventId: 'e1', refundAmount: 150, refundPercentage: 100, currency: 'ZMW', status: 'PROCESSING', reason: 'Cannot attend', rejectionReason: null, requestedAt: '2026-10-01T10:00:00Z' }];
    mount();
    fireEvent.click(screen.getByRole('tab', { name: /Past/ }));
    expect(screen.getByText('Old Show')).toBeInTheDocument();
    expect(screen.getByText('This event has taken place')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Request refund' })).toBeNull();
    fireEvent.click(screen.getByRole('tab', { name: /Refunds/ }));
    expect(screen.getByRole('list', { name: 'Refund progress' })).toBeInTheDocument();
    expect(screen.getByText('Processing', { selector: 'li' })).toHaveAttribute('aria-current', 'step');
  });
  it('has empty, loading and error states', () => {
    bookings = [];
    const a = mount();
    expect(screen.getByText('No upcoming tickets')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Find an event' })).toHaveAttribute('href', '/');
    fireEvent.click(screen.getByRole('tab', { name: /Refunds/ }));
    expect(screen.getByText('No refund requests')).toBeInTheDocument();
    a.unmount();
    ticketsState = { loading: true, error: null };
    const b = mount();
    expect(screen.getByLabelText('Loading your tickets')).toBeInTheDocument();
    b.unmount();
    ticketsState = { loading: false, error: { message: 'down' } };
    mount();
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
  it('opens help from the header', () => {
    mount();
    fireEvent.click(screen.getAllByRole('button', { name: 'Need help with your booking?' })[0]);
    expect(screen.getByRole('dialog', { name: 'Need help with your booking?' })).toHaveTextContent('Payment taken but no tickets');
  });
});
