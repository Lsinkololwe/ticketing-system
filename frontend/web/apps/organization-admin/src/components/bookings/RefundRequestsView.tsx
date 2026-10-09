'use client';

import { enumValues, REFUND_REQUEST_STATUS_LABELS } from '@/lib/format/enumLabels';
import { Card, CardHeader, DataTable, EmptyState, ErrorState, Select, Toolbar } from '@pml.tickets/shared/components/m3';
import type { RefundRequestRow } from '@/lib/api/bookings';
import { formatDateTime, kwacha } from '@/lib/bookings/format';
import { Status, statusLabel } from '@/components/console/Status';

const REQUEST_STATUSES = enumValues(REFUND_REQUEST_STATUS_LABELS);

export interface RefundRequestsViewProps {
  status: string;
  onStatusChange: (status: string) => void;
  requests: RefundRequestRow[];
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
}

/** Read-only inbox of buyer refund requests across all of the organization's events. */
export function RefundRequestsView(p: RefundRequestsViewProps) {
  const rows = p.requests;
  return (
    <Card>
      <CardHeader
        title="Refund requests"
        subtitle="Requests from buyers for all your events. Platform finance approves and processes them; you can follow the status here."
      />
      <Toolbar label="Refund request filters">
        <Select label="Status" density="compact" value={p.status} onChange={(e) => p.onStatusChange(e.target.value)}>
          <option value="all">All statuses</option>
          {REQUEST_STATUSES.map((s) => (
            <option key={s} value={s}>
              {REFUND_REQUEST_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
      </Toolbar>
      <DataTable
        caption="Refund requests"
        rows={rows}
        getRowId={(r) => r.id}
        loading={p.loading && p.requests.length === 0}
        error={p.error ? <ErrorState error={p.error} onRetry={p.onRetry} variant="inline" /> : undefined}
        empty={<EmptyState icon="receipt" title="No refund requests." />}
        columns={[
          { id: 'req', header: 'Request', rowHeader: true, cell: (r) => <span className="m3-mono">{r.requestId}</span> },
          { id: 'ticket', header: 'Ticket', cell: (r) => <span className="m3-mono">{r.ticketNumber}</span> },
          { id: 'amount', header: 'Amount', align: 'end', cell: (r) => <span className="m3-mono">{kwacha(r.refundAmount, r.currency)}</span> },
          { id: 'type', header: 'Type', cell: (r) => statusLabel(r.requestType) },
          { id: 'status', header: 'Status', cell: (r) => <Status status={r.status} /> },
          { id: 'at', header: 'Requested', cell: (r) => formatDateTime(r.requestedAt) },
          { id: 'reason', header: 'Reason', cell: (r) => r.reason },
        ]}
      />
    </Card>
  );
}
