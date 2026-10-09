'use client';

import { useState } from 'react';
import { KpiCard, Card, KpiGrid, StatusPill, type DataColumn } from '@pml.tickets/shared/components/m3';
import { useCommissionRecords, type CommissionRecordRow } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { ListCard } from './ListCard';
import { formatDateTime, money } from '@/lib/format';
import { COMMISSION_STATUS_LABELS, enumOptions } from '@/lib/enumLabels';


const COLUMNS: Array<DataColumn<CommissionRecordRow>> = [
  { id: 'ticket', header: 'Ticket', rowHeader: true, cell: (r) => <><span className="m3-mono">{r.ticketId}</span><br /><span className="m3-mono m3-muted">{r.eventId}</span></> },
  { id: 'price', header: 'Ticket price', align: 'end', cell: (r) => <span className="m3-mono">{money(Number(r.ticketPrice))}</span> },
  { id: 'rate', header: 'Rate', align: 'end', cell: (r) => <span className="m3-mono">{Number(r.rate)}%</span> },
  { id: 'amount', header: 'Commission', align: 'end', cell: (r) => <span className="m3-mono">{money(Number(r.amount))}</span> },
  { id: 'status', header: 'Status', cell: (r) => <><StatusPill status={r.status} />{r.refundReason ? <><br /><span className="m3-muted">{r.refundReason}</span></> : null}</> },
  { id: 'at', header: 'Recorded', cell: (r) => formatDateTime(r.earnedAt ?? r.pendingAt ?? r.createdAt) },
];

/** Commission records (commissionRecords): system generated, read only, filtered on the server. */
export function CommissionTab() {
  const [status, setStatus] = useState('');
  const [ticket, setTicket] = useState('');
  const [page, setPage] = useState(0);
  const q = useCommissionRecords({ status: (status || null) as CommissionRecordRow['status'] | null, ticketId: ticket, page, size: 20 });
  const t = q.totals;
  const tile = (n: string | undefined) => (n === undefined ? (q.loading ? '…' : '—') : <span className="m3-mono">{money(Number(n))}</span>);
  return (
    <div className="m3-stack">
      <Card>
      <KpiGrid flat>
        <KpiCard label="Earned" value={tile(t?.earned)} />
        <KpiCard label="Pending (events not finished)" value={tile(t?.pending)} />
        <KpiCard label="Clawed back" value={tile(t?.clawedBack)} />
        <KpiCard label="Cancelled" value={tile(t?.cancelled)} />
      </KpiGrid>
      </Card>
      <ListCard<CommissionRecordRow>
        title="Commission records"
        subtitle="Pending at purchase, earned when the event completes and the hold passes, clawed back on refund or chargeback. Records are system generated."
        caption="Commission records"
        rows={q.records}
        columns={COLUMNS}
        getRowId={(r) => r.id}
        searchLabel="Search by ticket id"
        onSearchQuery={(v) => { setTicket(v); setPage(0); }}
        searchText={(r) => r.ticketId}
        filters={[{ id: 'st', label: 'Status', options: enumOptions(COMMISSION_STATUS_LABELS) }]}
        onFilterChange={(v) => { setStatus(v.st && v.st !== 'all' ? v.st : ''); setPage(0); }}
        serverPage={{ page: page + 1, total: q.pageInfo.totalCount, onPage: (p) => setPage(p - 1) }}
        pageSize={20}
        loading={q.loading}
        error={q.error}
        onRetry={q.refetch}
        empty={{ title: 'No commission records yet.' }}
      />
    </div>
  );
}
