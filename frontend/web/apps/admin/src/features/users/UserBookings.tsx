'use client';

import { useState } from 'react';
import { DataTable, EmptyState, ErrorState, StatusPill } from '@pml.tickets/shared/components/m3';
import { useBookingsByBuyer, type AdminBuyerBookingRow } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { formatDate, money } from '@/lib/format';

/** A buyer's bookings (bookingsByBuyer), newest first. */
export function UserBookings({ buyerId }: { buyerId: string }) {
  const [page, setPage] = useState(0);
  const q = useBookingsByBuyer(buyerId, { page, size: 10 });
  return (
    <DataTable<AdminBuyerBookingRow>
      caption="Bookings"
      rows={q.bookings}
      getRowId={(b) => b.id}
      loading={q.loading && q.bookings.length === 0}
      error={q.error && q.bookings.length === 0 ? <ErrorState error={q.error} onRetry={q.refetch} /> : undefined}
      empty={<EmptyState icon="ticket" title="No bookings" description="This account has not made a booking." />}
      columns={[
        { id: 'no', header: 'Booking', rowHeader: true, cell: (b) => <span className="m3-mono">{b.bookingNumber}</span> },
        { id: 'event', header: 'Event', cell: (b) => <>{b.eventTitle ?? '—'}<br /><span className="m3-muted">{b.eventDate ?? ''}</span></> },
        { id: 'tickets', header: 'Tickets', align: 'end', cell: (b) => b.ticketCount },
        { id: 'total', header: 'Total', align: 'end', cell: (b) => <span className="m3-mono">{money(Number(b.totalAmount))}</span> },
        { id: 'status', header: 'Status', cell: (b) => <StatusPill status={b.status} /> },
        { id: 'date', header: 'Booked', cell: (b) => formatDate(b.createdAt) },
      ]}
      pagination={q.pageInfo.totalCount > q.pageInfo.pageSize ? { page: page + 1, pageSize: q.pageInfo.pageSize, total: q.pageInfo.totalCount, onPageChange: (p) => setPage(p - 1), label: 'Bookings pages' } : undefined}
    />
  );
}
