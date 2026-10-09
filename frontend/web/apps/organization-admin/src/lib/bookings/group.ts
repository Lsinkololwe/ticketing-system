import type { TicketRow } from '@/lib/api/bookings';

/** One booking as the screens show it. */
export interface BookingGroup {
  id: string;
  /** The reference people quote, e.g. on the receipt. */
  bookingNumber: string;
  eventId: string;
  eventTitle: string;
  buyerName: string;
  buyerPhone: string | null;
  buyerEmail: string | null;
  currency: string;
  /** Sum of ticket prices. */
  total: number;
  refunded: number;
  /** What can still be refunded on the booking, in kwacha. */
  refundable: number;
  status: string;
  purchasedAt: string | null;
  paymentMethod: string | null;
  paymentStatus: string | null;
  paymentReference: string | null;
  tickets: TicketRow[];
}

const NOT_REFUNDABLE = new Set(['CANCELLED', 'REFUNDED', 'EXPIRED', 'TRANSFERRED']);
/** Backend keeps VALIDATED tickets refundable; terminal states are not. */
export const isRefundable = (t: Pick<TicketRow, 'status'>) => !NOT_REFUNDABLE.has(t.status);
export const isCancellable = (t: Pick<TicketRow, 'status'>) => t.status === 'ISSUED' || t.status === 'REFUND_PENDING';

export function ticketRefunded(t: TicketRow): number {
  if (t.status === 'REFUNDED') return Number(t.price);
  return Number(t.refundInfo?.refundAmount ?? 0) || 0;
}

/** The booking fields the console screens use (list rows and the detail share them). */
interface BookingSource {
  id: string;
  bookingNumber: string;
  eventId: string;
  eventTitle: string | null;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  status: string;
  totalAmount: string;
  currency: string;
  refundedAmount: string;
  refundableAmount: string;
  createdAt: string | null;
  payment: { provider: string | null; status: string | null; reference: string | null } | null;
  tickets: TicketRow[];
}

/** A real booking (the backend's own aggregate) in the shape the screens render. */
export function fromBooking(b: BookingSource): BookingGroup {
  const first = b.tickets[0];
  return {
    id: b.id,
    bookingNumber: b.bookingNumber,
    eventId: b.eventId,
    eventTitle: b.eventTitle ?? first?.eventTitle ?? '—',
    buyerName: b.contactName ?? first?.buyerName ?? '—',
    buyerPhone: b.contactPhone ?? first?.buyerPhone ?? null,
    buyerEmail: b.contactEmail ?? first?.buyerEmail ?? null,
    currency: b.currency,
    total: Number(b.totalAmount),
    refunded: Number(b.refundedAmount),
    refundable: Number(b.refundableAmount),
    status: b.status,
    purchasedAt: b.createdAt,
    paymentMethod: b.payment?.provider ?? null,
    paymentStatus: b.payment?.status ?? null,
    paymentReference: b.payment?.reference ?? null,
    tickets: b.tickets,
  };
}
