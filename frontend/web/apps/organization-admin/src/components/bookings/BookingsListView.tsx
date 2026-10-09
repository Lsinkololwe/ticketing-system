'use client';

import { BOOKING_STATUS_LABELS } from '@/lib/format/enumLabels';
import { Button, Card, DataTable, EmptyState, ErrorState, Select, TextField, Toolbar } from '@pml.tickets/shared/components/m3';
import type { BookingGroup } from '@/lib/bookings/group';
import { BOOKING_STATUSES, formatDateTime, kwacha, showPhone } from '@/lib/bookings/format';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status } from '@/components/console/Status';

export interface BookingFilters {
  q: string;
  status: string;
  eventId: string;
}

export interface BookingsListViewProps {
  bookings: BookingGroup[];
  events: Array<{ id: string; title: string }>;
  filters: BookingFilters;
  onFiltersChange: (next: BookingFilters) => void;
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onQuickView: (booking: BookingGroup) => void;
}

/** Org-wide bookings table: search, status and event filters, quick view and full view actions. */
export function BookingsListView(p: BookingsListViewProps) {
  const f = p.filters;
  return (
    <Card>
      <Toolbar label="Booking filters">
        <TextField
          label="Search bookings"
          density="compact"
          placeholder="Booking number or buyer name (3+ letters)"
          value={f.q}
          onChange={(e) => p.onFiltersChange({ ...f, q: e.target.value })}
        />
        <Select
          label="Status"
          density="compact"
          value={f.status}
          onChange={(e) => p.onFiltersChange({ ...f, status: e.target.value })}
        >
          <option value="all">All statuses</option>
          {BOOKING_STATUSES.map((s) => (
            <option key={s} value={s}>
              {BOOKING_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select
          label="Event"
          density="compact"
          value={f.eventId}
          onChange={(e) => p.onFiltersChange({ ...f, eventId: e.target.value })}
        >
          <option value="all">All events</option>
          {p.events.map((e) => (
            <option key={e.id} value={e.id}>
              {e.title}
            </option>
          ))}
        </Select>
      </Toolbar>
      <DataTable
        caption="Bookings"
        rows={p.bookings}
        getRowId={(b) => b.id}
        loading={p.loading && p.bookings.length === 0}
        error={p.error ? <ErrorState error={p.error} onRetry={p.onRetry} variant="inline" /> : undefined}
        empty={<EmptyState icon="receipt" title="No bookings match your filters." description="Clear the filters or wait for your first sale." />}
        columns={[
          { id: 'id', header: 'Booking', rowHeader: true, cell: (b) => <span className="m3-mono">{b.bookingNumber}</span> },
          {
            id: 'buyer',
            header: 'Buyer',
            cell: (b) => (
              <>
                {b.buyerName}
                <br />
                <span className="m3-muted">{showPhone(b.buyerPhone)}</span>
              </>
            ),
          },
          { id: 'event', header: 'Event', cell: (b) => b.eventTitle },
          { id: 'tickets', header: 'Tickets', align: 'end', cell: (b) => String(b.tickets.length) },
          { id: 'total', header: 'Total', align: 'end', cell: (b) => <span className="m3-mono">{kwacha(b.total, b.currency)}</span> },
          { id: 'status', header: 'Status', cell: (b) => <Status status={b.status} /> },
          { id: 'date', header: 'Date', cell: (b) => formatDateTime(b.purchasedAt) },
        ]}
        rowActions={(b) => (
          <>
            <Button size="sm" variant="tonal" onClick={() => p.onQuickView(b)} aria-label={`Quick view ${b.bookingNumber}`}>
              Quick view
            </Button>
            <LinkBtn href={`/bookings/${encodeURIComponent(b.id)}`} size="sm" variant="text" aria-label={`Open booking ${b.bookingNumber}`}>
              Open
            </LinkBtn>
          </>
        )}
        pagination={{
          page: p.page + 1,
          pageSize: p.pageSize,
          total: p.total,
          onPageChange: (n) => p.onPageChange(n - 1),
          onPageSizeChange: p.onPageSizeChange,
          label: 'Bookings pagination',
        }}
      />
    </Card>
  );
}
