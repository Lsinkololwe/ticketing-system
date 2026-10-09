import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { TicketRow } from '@/lib/api/bookings';
import { fromBooking } from '@/lib/bookings/group';
import { BookingDetailView } from './BookingDetailView';
import { BookingQuickSheet } from './BookingQuickSheet';
import { BookingsListView } from './BookingsListView';
import { EventTicketsView } from './EventTicketsView';
import { ReasonDialog } from './ReasonDialog';
import { RefundBookingView } from './RefundBookingView';
import { RefundRequestsView } from './RefundRequestsView';
import { RestProblemError } from '@pml.tickets/shared';

// Fixtures live in this test only.
const t = (o: Partial<TicketRow>): TicketRow => ({
  id: o.ticketNumber ?? 't',
  ticketNumber: 'TK-1',
  eventId: 'e1',
  eventTitle: 'Fixture Fest',
  buyerName: 'Buyer One',
  buyerEmail: 'b@example.test',
  buyerPhone: '+260977000112',
  ticketCategoryName: 'General',
  price: '100',
  currency: 'ZMW',
  status: 'ISSUED',
  purchaseDate: '2026-10-01T10:00:00Z',
  validatedAt: null,
  cancelledAt: null,
  cancellationReason: null,
  refundedAt: null,
  refundReason: null,
  paymentReference: 'PAY-1',
  netAmount: null,
  commissionAmount: null,
  paymentInfo: { paymentMethod: 'MTN', status: 'SUCCEEDED', providerReference: 'x', paymentDate: null },
  refundInfo: null,
  ...o,
});
const tickets = [t({ ticketNumber: 'TK-1' }), t({ ticketNumber: 'TK-2', status: 'REFUNDED' })];
const bookings = [
  fromBooking({
    id: 'bk1', bookingNumber: 'BK-1001', eventId: 'e1', eventTitle: 'Fixture Fest', contactName: 'Buyer One', contactEmail: 'b@example.test', contactPhone: '+260977000112',
    status: 'PARTIALLY_REFUNDED', totalAmount: '200', currency: 'ZMW', refundedAmount: '100', refundableAmount: '100', createdAt: '2026-10-01T10:00:00Z',
    payment: { provider: 'MTN', status: 'SUCCEEDED', reference: 'PAY-1' }, tickets,
  }),
];
const filters = { q: '', status: 'all', eventId: 'all' };
const listProps = {
  bookings,
  events: [{ id: 'e1', title: 'Fixture Fest' }],
  filters,
  onFiltersChange: () => undefined,
  loading: false,
  page: 0,
  pageSize: 12,
  total: 1,
  onPageChange: () => undefined,
  onPageSizeChange: () => undefined,
  onQuickView: () => undefined,
};

describe('BookingsListView', () => {
  it('renders a row per booking with dedicated non-row actions', () => {
    const onQuick = vi.fn();
    render(<BookingsListView {...listProps} onQuickView={onQuick} />);
    expect(screen.getByRole('table', { name: 'Bookings' })).toBeInTheDocument();
    expect(screen.getByText('BK-1001')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open booking BK-1001' })).toHaveAttribute('href', '/bookings/bk1');
    fireEvent.click(screen.getByRole('button', { name: 'Quick view BK-1001' }));
    expect(onQuick).toHaveBeenCalledWith(bookings[0]);
  });
  it('shows empty, loading and error states', () => {
    const { rerender, container } = render(<BookingsListView {...listProps} bookings={[]} />);
    expect(screen.getByText('No bookings match your filters.')).toBeInTheDocument();
    rerender(<BookingsListView {...listProps} bookings={[]} loading />);
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull();
    rerender(<BookingsListView {...listProps} bookings={[]} error={{ message: 'Boom' }} />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
  it('emits filter changes', () => {
    const on = vi.fn();
    render(<BookingsListView {...listProps} onFiltersChange={on} />);
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'REFUNDED' } });
    expect(on).toHaveBeenCalledWith({ ...filters, status: 'REFUNDED' });
  });
});

describe('BookingQuickSheet', () => {
  it('shows summary and links to the full booking and refund', () => {
    render(<BookingQuickSheet booking={bookings[0]!} canRefund onClose={() => undefined} />);
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('Buyer One')).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Open full booking' })).toHaveAttribute('href', '/bookings/bk1');
    expect(within(dialog).getByRole('link', { name: 'Process refund' })).toHaveAttribute('href', '/bookings/bk1/refund');
  });
  it('renders nothing when closed', () => {
    render(<BookingQuickSheet booking={null} canRefund onClose={() => undefined} />);
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});

describe('BookingDetailView', () => {
  it('renders booking, tickets, payment and refund history', () => {
    render(<BookingDetailView id="bk1" booking={bookings[0]!} loading={false} canRefund />);
    expect(screen.getByRole('heading', { level: 1, name: 'BK-1001' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Issued tickets' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Process refund' })).toHaveAttribute('href', '/bookings/bk1/refund');
    expect(screen.getByRole('link', { name: 'Bookings' })).toHaveAttribute('href', '/bookings');
  });
  it('handles not found and error', () => {
    const { rerender } = render(<BookingDetailView id="X" booking={null} loading={false} canRefund />);
    expect(screen.getByText('Booking not found')).toBeInTheDocument();
    rerender(<BookingDetailView id="X" booking={null} loading={false} canRefund error={{ message: 'Down' }} />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});

describe('RefundBookingView', () => {
  it('requires a reason, focuses it, then submits the eligible tickets', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<RefundBookingView id="bk1" booking={bookings[0]!} loading={false} onSubmit={onSubmit} />);
    await user.click(screen.getByRole('button', { name: 'Refund' }));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(await screen.findByText('Required')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Reason')).toHaveFocus());
    await user.type(screen.getByLabelText('Reason'), 'Buyer asked');
    await user.click(screen.getByRole('button', { name: 'Refund' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(['TK-1'], 'Buyer asked', null));
  });
  it('rejects a reason shorter than three characters', async () => {
    const user = userEvent.setup();
    render(<RefundBookingView id="bk1" booking={bookings[0]!} loading={false} onSubmit={vi.fn()} />);
    await user.type(screen.getByLabelText('Reason'), 'ab');
    await user.click(screen.getByRole('button', { name: 'Refund' }));
    expect(await screen.findAllByText('Enter a reason (at least 3 characters)')).not.toHaveLength(0);
  });
  it('shows a server refusal as a banner and ignores a double submit', async () => {
    const user = userEvent.setup();
    let reject!: (e: unknown) => void;
    const onSubmit = vi.fn(() => new Promise<void>((_, r) => (reject = r)));
    render(<RefundBookingView id="bk1" booking={bookings[0]!} loading={false} onSubmit={onSubmit} />);
    await user.type(screen.getByLabelText('Reason'), 'Buyer asked');
    const btn = screen.getByRole('button', { name: 'Refund' });
    await user.dblClick(btn);
    expect(onSubmit).toHaveBeenCalledTimes(1);
    reject(new RestProblemError(409, { status: 409, errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' }));
    expect(await screen.findByText('Those tickets have sold out.')).toBeInTheDocument();
  });
  it('explains when nothing is refundable', () => {
    render(<RefundBookingView id="P" booking={{ ...bookings[0]!, tickets: [t({ status: 'REFUNDED' })] }} loading={false} onSubmit={() => undefined} />);
    expect(screen.getByText('Nothing on this booking can be refunded.')).toBeInTheDocument();
  });
});

describe('EventTicketsView', () => {
  const props = {
    tickets,
    filters: { q: '', status: 'all', checkIn: 'all' as const },
    onFiltersChange: () => undefined,
    loading: false,
    page: 0,
    pageSize: 12,
    total: 2,
    onPageChange: () => undefined,
    onPageSizeChange: () => undefined,
    canRefund: true,
    onRefund: () => undefined,
    onCancel: () => undefined,
  };
  it('renders tickets and a row menu with refund/cancel', () => {
    const onRefund = vi.fn();
    render(<EventTicketsView {...props} onRefund={onRefund} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions for ticket TK-1' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Refund ticket' }));
    expect(onRefund).toHaveBeenCalledWith(tickets[0]);
  });
  it('filters by check-in and shows empty state', () => {
    render(<EventTicketsView {...props} filters={{ q: '', status: 'all', checkIn: 'in' }} />);
    expect(screen.getByText('No tickets match. Clear the filters.')).toBeInTheDocument();
  });
});

describe('RefundRequestsView', () => {
  const req = { id: 'r1', requestId: 'RR-1', ticketNumber: 'TK-1', eventId: 'e1', refundAmount: '50', currency: 'ZMW', status: 'PENDING', requestType: 'USER_REQUESTED', reason: 'Ill', requestedAt: null, policyApplied: null };
  const props = { status: 'all', onStatusChange: () => undefined, requests: [req], loading: false };
  it('lists requests and filters by status', () => {
    const { rerender } = render(<RefundRequestsView {...props} />);
    expect(screen.getByText('RR-1')).toBeInTheDocument();
    expect(screen.queryByLabelText('Event')).toBeNull();
    rerender(<RefundRequestsView {...props} requests={[]} />);
    expect(screen.getByText('No refund requests.')).toBeInTheDocument();
  });
});

describe('ReasonDialog', () => {
  const open = (onConfirm: (r: string) => unknown) =>
    render(<ReasonDialog open title="Cancel ticket" confirmLabel="Confirm" onClose={() => undefined} onConfirm={onConfirm as never} />);
  it('blocks an empty reason, focuses the field, then confirms the trimmed reason', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    open(onConfirm);
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onConfirm).not.toHaveBeenCalled();
    expect(await screen.findByText('Required')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Reason')).toHaveFocus());
    await user.type(screen.getByLabelText('Reason'), '  No show ');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('No show'));
  });
  it('maps a server field violation onto the reason field', async () => {
    const user = userEvent.setup();
    open(vi.fn().mockRejectedValue({ errors: [{ message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.reason', constraint: 'Size' }] } }] }));
    await user.type(screen.getByLabelText('Reason'), 'No show');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(screen.getByLabelText('Reason')).toHaveAttribute('aria-invalid', 'true'));
  });
  it('submits once on a double click', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn(() => new Promise<void>(() => undefined));
    open(onConfirm);
    await user.type(screen.getByLabelText('Reason'), 'No show');
    await user.dblClick(screen.getByRole('button', { name: 'Confirm' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});

describe('partial refund and resend', () => {
  it('refunds part of a single ticket and rejects an amount above what is refundable', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<RefundBookingView id="bk1" booking={bookings[0]!} loading={false} onSubmit={onSubmit} />);
    await user.type(screen.getByLabelText('Reason'), 'Goodwill');
    await user.type(screen.getByLabelText('Partial amount (optional)'), '250');
    await user.click(screen.getByRole('button', { name: 'Refund' }));
    expect((await screen.findAllByText(/The most you can refund is/)).length).toBeGreaterThan(0);
    expect(onSubmit).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText('Partial amount (optional)'));
    await user.type(screen.getByLabelText('Partial amount (optional)'), '25.50');
    await user.click(screen.getByRole('button', { name: 'Refund' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(['TK-1'], 'Goodwill', '25.50'));
  });
  it('resends a ticket from the detail view', () => {
    const onResend = vi.fn();
    render(<BookingDetailView id="bk1" booking={bookings[0]!} loading={false} canRefund onResend={onResend} />);
    fireEvent.click(screen.getByRole('button', { name: 'Resend ticket TK-1' }));
    expect(onResend).toHaveBeenCalledWith('TK-1');
  });
});
