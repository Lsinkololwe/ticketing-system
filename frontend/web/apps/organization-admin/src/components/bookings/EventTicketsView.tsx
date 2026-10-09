'use client';

import { enumValues, TICKET_STATUS_LABELS } from '@/lib/format/enumLabels';
import { Card, CardHeader, DataTable, EmptyState, ErrorState, RowMenu, Select, TextField, Toolbar } from '@pml.tickets/shared/components/m3';
import type { TicketRow } from '@/lib/api/bookings';
import { isCancellable, isRefundable } from '@/lib/bookings/group';
import { formatDateTime, kwacha, showPhone } from '@/lib/bookings/format';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status } from '@/components/console/Status';

export const TICKET_STATUSES = enumValues(TICKET_STATUS_LABELS);

export interface TicketFilters {
  q: string;
  status: string;
  checkIn: 'all' | 'in' | 'out';
}

export interface EventTicketsViewProps {
  tickets: TicketRow[];
  filters: TicketFilters;
  onFiltersChange: (f: TicketFilters) => void;
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (n: number) => void;
  canRefund: boolean;
  onRefund: (t: TicketRow) => void;
  onCancel: (t: TicketRow) => void;
}

/** Every ticket sold for one event, with per-ticket refund and cancel actions. */
export function EventTicketsView(p: EventTicketsViewProps) {
  const f = p.filters;
  const rows = p.tickets.filter((t) => f.checkIn === 'all' || (f.checkIn === 'in') === Boolean(t.validatedAt));
  return (
    <Card>
      <CardHeader title="Bookings" subtitle="Every ticket sold for this event." />
      <Toolbar label="Ticket filters">
        <TextField
          label="Search tickets"
          density="compact"
          placeholder="Ticket, buyer or phone"
          value={f.q}
          onChange={(e) => p.onFiltersChange({ ...f, q: e.target.value })}
        />
        <Select label="Ticket status" density="compact" value={f.status} onChange={(e) => p.onFiltersChange({ ...f, status: e.target.value })}>
          <option value="all">All statuses</option>
          {TICKET_STATUSES.map((s) => (
            <option key={s} value={s}>
              {TICKET_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select
          label="Check-in"
          density="compact"
          value={f.checkIn}
          onChange={(e) => p.onFiltersChange({ ...f, checkIn: e.target.value as TicketFilters['checkIn'] })}
        >
          <option value="all">All guests</option>
          <option value="in">Checked in</option>
          <option value="out">Not checked in</option>
        </Select>
      </Toolbar>
      <DataTable
        caption="Tickets for this event"
        rows={rows}
        getRowId={(t) => t.ticketNumber}
        loading={p.loading && p.tickets.length === 0}
        error={p.error ? <ErrorState error={p.error} onRetry={p.onRetry} variant="inline" /> : undefined}
        empty={<EmptyState icon="ticket" title="No tickets match. Clear the filters." />}
        columns={[
          { id: 'no', header: 'Ticket', rowHeader: true, cell: (t) => <span className="m3-mono">{t.ticketNumber}</span> },
          {
            id: 'booking',
            header: 'Booking',
            cell: (t) => {
              const id = t.paymentReference || t.ticketNumber;
              return (
                <LinkBtn href={`/bookings/${encodeURIComponent(id)}`} variant="text" size="sm" aria-label={`Open booking ${id}`}>
                  {id}
                </LinkBtn>
              );
            },
          },
          {
            id: 'buyer',
            header: 'Buyer',
            cell: (t) => (
              <>
                {t.buyerName ?? '—'}
                <br />
                <span className="m3-muted">{showPhone(t.buyerPhone)}</span>
              </>
            ),
          },
          { id: 'tier', header: 'Tier', cell: (t) => t.ticketCategoryName ?? '—' },
          { id: 'price', header: 'Price', align: 'end', cell: (t) => <span className="m3-mono">{kwacha(t.price, t.currency)}</span> },
          { id: 'status', header: 'Status', cell: (t) => <Status status={t.status} /> },
          { id: 'ci', header: 'Checked in', cell: (t) => formatDateTime(t.validatedAt) },
        ]}
        rowActions={(t) =>
          p.canRefund ? (
            <RowMenu
              label={`Actions for ticket ${t.ticketNumber}`}
              items={[
                { id: 'refund', label: 'Refund ticket', disabled: !isRefundable(t), onSelect: () => p.onRefund(t) },
                { id: 'cancel', label: 'Cancel ticket', danger: true, disabled: !isCancellable(t), onSelect: () => p.onCancel(t) },
              ]}
            />
          ) : null
        }
        pagination={{
          page: p.page + 1,
          pageSize: p.pageSize,
          total: p.total,
          onPageChange: (n) => p.onPageChange(n - 1),
          onPageSizeChange: p.onPageSizeChange,
          label: 'Tickets pagination',
        }}
      />
    </Card>
  );
}
