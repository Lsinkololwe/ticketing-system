import { describe, expect, it } from 'vitest';
import type { TicketRow } from '@/lib/api/bookings';
import { fromBooking, isCancellable, isRefundable } from './group';

const ticket = (o: Partial<TicketRow>): TicketRow =>
  ({
    id: 't1', ticketNumber: 'TK-1', eventId: 'e1', eventTitle: 'Fixture Fest', buyerName: 'Buyer One', buyerEmail: null, buyerPhone: '+260977000112',
    ticketCategoryName: 'General', price: '100', currency: 'ZMW', status: 'ISSUED', purchaseDate: null, validatedAt: null, cancelledAt: null,
    cancellationReason: null, refundedAt: null, refundReason: null, paymentReference: 'PAY-1', netAmount: null, commissionAmount: null, paymentInfo: null, refundInfo: null, ...o,
  }) as TicketRow;

const source = (o = {}) => ({
  id: 'bk1', bookingNumber: 'BK-1001', eventId: 'e1', eventTitle: 'Fixture Fest', contactName: 'Buyer One', contactEmail: null, contactPhone: null,
  status: 'CONFIRMED', totalAmount: '200', currency: 'ZMW', refundedAmount: '0', refundableAmount: '200', createdAt: '2026-10-01T10:00:00Z',
  payment: { provider: 'MTN', status: 'SUCCEEDED', reference: 'PAY-1' }, tickets: [ticket({}), ticket({ id: 't2', ticketNumber: 'TK-2' })], ...o,
});

describe('fromBooking', () => {
  it('maps the real booking aggregate', () => {
    const b = fromBooking(source());
    expect(b).toMatchObject({ id: 'bk1', bookingNumber: 'BK-1001', total: 200, refundable: 200, paymentMethod: 'MTN', paymentReference: 'PAY-1' });
    expect(b.tickets).toHaveLength(2);
  });
  it('falls back to the first ticket for buyer details', () => {
    const b = fromBooking(source({ contactName: null, contactPhone: null }));
    expect(b.buyerName).toBe('Buyer One');
    expect(b.buyerPhone).toBe('+260977000112');
  });
});

describe('ticket states', () => {
  it('keeps issued tickets refundable and cancellable', () => {
    expect(isRefundable({ status: 'ISSUED' })).toBe(true);
    expect(isRefundable({ status: 'REFUNDED' })).toBe(false);
    expect(isCancellable({ status: 'ISSUED' })).toBe(true);
    expect(isCancellable({ status: 'VALIDATED' })).toBe(false);
  });
});
