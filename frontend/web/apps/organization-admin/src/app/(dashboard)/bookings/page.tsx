'use client';

import { useDeferredValue, useMemo, useState } from 'react';
import type { BookingFilterInput, BookingStatus, RefundRequestFilterInput, RefundRequestStatus } from '@pml.tickets/shared/types/graphql';
import { PageHeader, Tabs, useSnackbar } from '@pml.tickets/shared/components/m3';
import { BookingQuickSheet } from '@/components/bookings/BookingQuickSheet';
import { BookingsListView, type BookingFilters } from '@/components/bookings/BookingsListView';
import { RefundRequestsView } from '@/components/bookings/RefundRequestsView';
import { useOrganizerBookings, useRefundInbox, useResendTicket } from '@/lib/api/bookings';
import { useOrgEvents } from '@/lib/api/events';
import { useOrgContext } from '@/lib/api/org-context';
import { fromBooking, type BookingGroup } from '@/lib/bookings/group';

export default function BookingsPage() {
  const { capabilities } = useOrgContext();
  const snack = useSnackbar();
  const { events } = useOrgEvents();
  const { resend } = useResendTicket();
  const [tab, setTab] = useState('list');
  const [filters, setFilters] = useState<BookingFilters>({ q: '', status: 'all', eventId: 'all' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);
  const [quick, setQuick] = useState<BookingGroup | null>(null);
  const q = useDeferredValue(filters.q.trim());
  const filter: BookingFilterInput = {
    eventId: filters.eventId === 'all' ? null : filters.eventId,
    status: filters.status === 'all' ? null : (filters.status as BookingStatus),
    // The server needs at least 3 characters to search.
    search: q.length >= 3 ? q : null,
    statuses: null,
    createdAfter: null,
    createdBefore: null,
  };
  const { bookings: rows, total, loading, error, refetch } = useOrganizerBookings(filter, page, size);
  const bookings = useMemo(() => rows.map((b) => fromBooking(b as never)), [rows]);
  const eventOptions = events.map((e) => ({ id: e.id, title: e.title }));

  const [rrStatus, setRrStatus] = useState('all');
  const rrFilter: RefundRequestFilterInput = {
    status: rrStatus === 'all' ? null : (rrStatus as RefundRequestStatus),
    ticketId: null, buyerId: null, eventId: null, organizerId: null, requestType: null, startDate: null, endDate: null,
  };
  const rr = useRefundInbox(rrFilter);

  const onResend = async (ticketId: string) => {
    try {
      const r = await resend(ticketId);
      const d = r.data?.resendTicket;
      snack.show(d?.destination ? `Ticket sent to ${d.destination}` : 'Ticket sent again');
    } catch (e) {
      snack.show({ message: (e as Error).message, tone: 'error' });
    }
  };

  return (
    <>
      <PageHeader title="Bookings" subtitle="Everything bought for your events. Refund requests are in the second tab." />
      <Tabs variant="seg"
        label="Bookings sections"
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'list', label: 'Bookings' },
          { id: 'refunds', label: 'Refund requests' },
        ]}
      >
        {(active) =>
          active === 'list' ? (
            <BookingsListView
              bookings={bookings}
              events={eventOptions}
              filters={filters}
              onFiltersChange={(f) => {
                setFilters(f);
                setPage(0);
              }}
              loading={loading}
              error={error}
              onRetry={() => void refetch()}
              page={page}
              pageSize={size}
              total={total}
              onPageChange={setPage}
              onPageSizeChange={(n) => {
                setSize(n);
                setPage(0);
              }}
              onQuickView={setQuick}
            />
          ) : (
            <RefundRequestsView
              status={rrStatus}
              onStatusChange={setRrStatus}
              requests={rr.requests}
              loading={rr.loading}
              error={rr.error}
              onRetry={() => void rr.refetch()}
            />
          )
        }
      </Tabs>
      <BookingQuickSheet booking={quick} canRefund={capabilities.canRefund} onResend={onResend} onClose={() => setQuick(null)} />
    </>
  );
}
