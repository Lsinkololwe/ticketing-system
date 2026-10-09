'use client';

import { useMemo, useState } from 'react';
import { Card, CardHeader, DataTable, EmptyState, ErrorState, Select, TextField, Toolbar } from '@pml.tickets/shared/components/m3';
import { formatDateTime } from '@/lib/bookings/format';
import { Status, statusLabel } from '@/components/console/Status';

export interface AttendeeRow {
  id: string;
  ticketNumber: string;
  buyerName: string | null;
  buyerEmail: string | null;
  ticketCategoryName: string | null;
  status: string;
  purchaseDate: string | null;
  validatedAt: string | null;
}

export interface AttendeesViewProps {
  attendees: AttendeeRow[];
  total: number;
  hasMore: boolean;
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
}

const PAGE_SIZES = [12, 24, 50];

/** Attendee list for one event: search, status filter and client-side paging over the loaded roster. */
export function AttendeesView(p: AttendeesViewProps) {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZES[0]!);
  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return p.attendees.filter(
      (a) =>
        (status === 'all' || (status === 'in' ? Boolean(a.validatedAt) : !a.validatedAt)) &&
        (!needle || `${a.ticketNumber} ${a.buyerName ?? ''} ${a.buyerEmail ?? ''}`.toLowerCase().includes(needle))
    );
  }, [p.attendees, q, status]);
  const slice = rows.slice((page - 1) * size, page * size);
  return (
    <Card>
      <CardHeader
        title="Attendees"
        subtitle={p.hasMore ? `Showing the first ${p.attendees.length} of ${p.total.toLocaleString('en-GB')} ticket holders. Search narrows this list.` : `${p.total.toLocaleString('en-GB')} ticket holders`}
      />
      <Toolbar label="Attendee filters">
        <TextField
          label="Search attendees"
          density="compact"
          placeholder="Name, email or ticket"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(1);
          }}
        />
        <Select
          label="Check-in"
          density="compact"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(1);
          }}
        >
          <option value="all">All guests</option>
          <option value="in">Checked in</option>
          <option value="out">Not checked in</option>
        </Select>
      </Toolbar>
      <DataTable
        caption="Attendees"
        rows={slice}
        getRowId={(a) => a.id}
        loading={p.loading && p.attendees.length === 0}
        error={p.error ? <ErrorState error={p.error} onRetry={p.onRetry} variant="inline" /> : undefined}
        empty={<EmptyState icon="users" title="No attendees match." description="Tickets appear here once they are sold." />}
        columns={[
          { id: 'buyer', header: 'Name', rowHeader: true, cell: (a) => a.buyerName ?? '—' },
          { id: 'email', header: 'Email', cell: (a) => a.buyerEmail ?? '—' },
          { id: 'no', header: 'Ticket', cell: (a) => <span className="m3-mono">{a.ticketNumber}</span> },
          { id: 'tier', header: 'Tier', cell: (a) => a.ticketCategoryName ?? '—' },
          { id: 'status', header: 'Ticket status', cell: (a) => <Status status={a.status} label={statusLabel(a.status)} /> },
          { id: 'ci', header: 'Checked in', cell: (a) => formatDateTime(a.validatedAt) },
        ]}
        pagination={{
          page,
          pageSize: size,
          total: rows.length,
          onPageChange: setPage,
          onPageSizeChange: (n) => {
            setSize(n);
            setPage(1);
          },
          pageSizeOptions: PAGE_SIZES,
          label: 'Attendees pagination',
        }}
      />
    </Card>
  );
}
