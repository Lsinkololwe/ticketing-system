'use client';

import { useState, type ReactNode } from 'react';
import { type DataColumn } from '@pml.tickets/shared/components/m3';
import { useGatewaySettlements, type GatewaySettlementRow } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { ListCard } from './ListCard';
import { formatDate, money } from '@/lib/format';

const COLUMNS: Array<DataColumn<GatewaySettlementRow>> = [
  { id: 'id', header: 'Settlement', rowHeader: true, cell: (s) => <><span className="m3-mono">{s.settlementId}</span><br /><span className="m3-muted">{formatDate(s.settlementDate)}</span></> },
  { id: 'gross', header: 'Gross', align: 'end', cell: (s) => <span className="m3-mono">{money(Number(s.grossAmount))}</span> },
  { id: 'fee', header: 'Fees', align: 'end', cell: (s) => <span className="m3-mono">{money(Number(s.feeAmount))}</span> },
  { id: 'net', header: 'Net', align: 'end', cell: (s) => <span className="m3-mono">{money(Number(s.netAmount))}</span> },
  { id: 'bank', header: 'Bank reference', cell: (s) => <span className="m3-mono">{s.bankReference ?? '—'}</span> },
  { id: 'entry', header: 'Journal entry', cell: (s) => <span className="m3-mono">{s.entryNumber ?? s.journalEntryId}</span> },
];

/** Settlement history (gatewaySettlements), newest first. */
export function SettlementsCard({ toolbar }: { toolbar?: ReactNode }) {
  const [page, setPage] = useState(0);
  const q = useGatewaySettlements({ page, size: 10 });
  return (
    <ListCard<GatewaySettlementRow>
      title="Gateway settlements"
      subtitle="Settlement statements from PawaPay."
      toolbar={toolbar}
      caption="Gateway settlements"
      rows={q.settlements}
      columns={COLUMNS}
      getRowId={(s) => s.settlementId}
      searchLabel="Search settlements"
      searchText={(s) => `${s.settlementId} ${s.bankReference ?? ''}`}
      serverPage={{ page: page + 1, total: q.pageInfo.totalCount, onPage: (p) => setPage(p - 1) }}
      pageSize={10}
      loading={q.loading}
      error={q.error}
      onRetry={q.refetch}
      empty={{ title: 'No settlements recorded yet.' }}
    />
  );
}
