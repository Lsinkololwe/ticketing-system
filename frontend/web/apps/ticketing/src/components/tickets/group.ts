import type { BuyerBookingRow, BuyerBookingTicket } from '@pml.tickets/shared';

export interface BookingGroup {
  key: string;
  bookingNumber: string;
  eventId: string;
  eventTitle: string;
  eventDate: string | null;
  venue: string | null;
  purchasedAt: string | null;
  total: number;
  status: string;
  /** True when the payment arrived after the hold lapsed and was refunded in full automatically. */
  autoRefunded: boolean;
  tickets: BuyerBookingTicket[];
  upcoming: boolean;
}

/** One card per booking, as the backend groups them. Bookings that never produced tickets are left out, except a late payment that was refunded. */
export function groupBookings(bookings: BuyerBookingRow[], now = Date.now()): BookingGroup[] {
  return bookings
    .filter((b) => b.tickets.length > 0 || b.status === 'PAID_AFTER_EXPIRY_AUTO_REFUNDED')
    .map((b) => ({
      key: b.id,
      bookingNumber: b.bookingNumber,
      eventId: b.eventId,
      eventTitle: b.eventTitle ?? 'Event',
      eventDate: b.eventDate ?? null,
      venue: b.tickets[0]?.eventLocationName ?? null,
      purchasedAt: b.confirmedAt ?? b.createdAt ?? null,
      total: Number(b.totalAmount),
      status: b.status,
      autoRefunded: b.status === 'PAID_AFTER_EXPIRY_AUTO_REFUNDED',
      tickets: b.tickets,
      upcoming: b.eventDate ? Date.parse(b.eventDate) >= now : true,
    }));
}

export function splitBookings(groups: BookingGroup[]) {
  const upcoming = groups.filter((g) => g.upcoming).sort((a, b) => Date.parse(a.eventDate ?? '') - Date.parse(b.eventDate ?? ''));
  const past = groups.filter((g) => !g.upcoming).sort((a, b) => Date.parse(b.eventDate ?? '') - Date.parse(a.eventDate ?? ''));
  return { upcoming, past };
}
