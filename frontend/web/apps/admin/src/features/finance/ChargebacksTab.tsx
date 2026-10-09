'use client';

import { RowActions } from '@/components/console/RowActions';
import { useMemo, useState } from 'react';
import { Button, Card, CardHeader, DataTable, EmptyState, ErrorState, KpiCard, KpiGrid, StatusPill } from '@pml.tickets/shared/components/m3';
import { useChargebackList, useChargebackQueue, type ChargebackRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { FilterBar, useStaff } from '@/components/console';
import { humanize, money } from '@/lib/format';
import { CB_FUNDS, CB_REASONS, CB_STATUSES, ChargebackSheet, Deadline, useChargebackActions } from './ChargebackParts';
import { asNumber, Mono, TwoLine, useCopyCsv } from './shared';

const opts = (xs: string[]) => xs.map((x) => ({ value: x, label: humanize(x) }));

/** Chargebacks: summary cards, table, record dialog and the lifecycle sheet. */
export function ChargebacksTab() {
  const staff = useStaff();
  const copyCsv = useCopyCsv();
  const decide = staff.can('payoutDecide');
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [sheet, setSheet] = useState<ChargebackRow | null>(null);
  const status = filters.status && filters.status !== 'all' ? filters.status : null;
  const list = useChargebackList({ page, status });
  const queue = useChargebackQueue();
  const actions = useChargebackActions(() => {
    list.refetch();
    setSheet(null);
  });

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return list.chargebacks.filter(
      (c) =>
        (!q || [c.chargebackId, c.originalTransactionId, c.customerId, c.eventId].some((v) => v.toLowerCase().includes(q))) &&
        (!filters.reason || filters.reason === 'all' || c.reason === filters.reason) &&
        (!filters.fund || filters.fund === 'all' || c.fundSource === filters.fund)
    );
  }, [list.chargebacks, query, filters.reason, filters.fund]);

  const soon = queue.open.filter((c) => (new Date(c.responseDeadline).getTime() - Date.now()) / 36e5 <= 24).length;
  const v = (x: string | number) => (queue.loaded ? x : '—');

  return (
    <div className="m3-stack">
      <Card as="section" aria-label="Chargeback summary">
        <Card>
        <KpiGrid flat>
          <KpiCard label="Open" value={v(queue.open.length)} />
          <KpiCard label="Amount at risk" value={v(money(queue.openAmount))} />
          <KpiCard label="Deadline within 24 h" value={v(soon)} />
          <KpiCard label="Recovery in progress" value={v(queue.recoveryCount)} />
        </KpiGrid>
        </Card>
      </Card>
      <Card as="section" aria-label="Chargebacks">
        <CardHeader title="Chargebacks" subtitle="Finance is alerted 24 hours before the provider deadline. Chargebacks still open at the deadline are accepted automatically." />
        <FilterBar
          searchLabel="Search chargebacks"
          query={query}
          onQuery={setQuery}
          filters={[
            { id: 'status', label: 'Status', options: opts(CB_STATUSES) },
            { id: 'reason', label: 'Reason', options: opts(CB_REASONS) },
            { id: 'fund', label: 'Fund source', options: opts(CB_FUNDS) },
          ]}
          values={filters}
          onFilter={(id, val) => {
            setFilters((f) => ({ ...f, [id]: val }));
            setPage(0);
          }}
          onClear={() => {
            setQuery('');
            setFilters({});
            setPage(0);
          }}
          actions={
            <>
              <Button
                variant="text"
                size="sm"
                icon="copy"
                disabled={rows.length === 0}
                onClick={() =>
                  void copyCsv([
                    ['Chargeback', 'Payment', 'Amount (K)', 'Reason', 'Status', 'Fund source', 'Deadline'],
                    ...rows.map((c) => [c.chargebackId, c.originalTransactionId, asNumber(c.chargebackAmount), humanize(c.reason), humanize(c.status), humanize(c.fundSource), c.responseDeadline]),
                  ])
                }
              >
                Copy CSV
              </Button>
              <Button variant="filled" size="sm" icon="add" disabled={!decide} onClick={() => actions.start('record')}>
                Record chargeback
              </Button>
            </>
          }
        />
        <DataTable<ChargebackRow>
          caption="Chargebacks"
          rows={rows}
          getRowId={(c) => c.id}
          loading={list.loading && list.chargebacks.length === 0}
          error={list.error && list.chargebacks.length === 0 ? <ErrorState error={list.error} onRetry={list.refetch} /> : undefined}
          empty={
            <EmptyState
              title="No chargebacks recorded."
              action={
                list.chargebacks.length === 0 ? (
                  <Button variant="filled" disabled={!decide} onClick={() => actions.start('record')}>
                    Record chargeback
                  </Button>
                ) : undefined
              }
            />
          }
          columns={[
            { id: 'cb', header: 'Chargeback', rowHeader: true, cell: (c) => <TwoLine main={<Mono>{c.chargebackId}</Mono>} sub={c.originalTransactionId} monoSub /> },
            { id: 'who', header: 'Buyer and event', cell: (c) => <TwoLine main={<Mono>{c.customerId.slice(-8)}</Mono>} sub={c.eventId.slice(-8)} monoSub /> },
            { id: 'amount', header: 'Amount', align: 'end', cell: (c) => <Mono>{money(asNumber(c.chargebackAmount))}</Mono> },
            { id: 'reason', header: 'Reason', cell: (c) => humanize(c.reason) },
            { id: 'status', header: 'Status', cell: (c) => <StatusPill status={c.status} /> },
            { id: 'fund', header: 'Fund source', cell: (c) => humanize(c.fundSource) },
            { id: 'rec', header: 'Recovery', cell: (c) => <StatusPill status={c.recoveryStatus} /> },
            { id: 'deadline', header: 'Deadline', cell: (c) => <Deadline c={c} /> },
          ]}
          rowActions={(c) => <RowActions name={`chargeback ${c.chargebackId}`} primary={{ label: 'Open', onSelect: () => setSheet(c) }} />}
          actionsHeader="Actions"
          pagination={{ page: page + 1, pageSize: list.pageInfo.pageSize, total: list.pageInfo.totalCount ?? 0, onPageChange: (n) => setPage(n - 1) }}
        />
      </Card>
      <ChargebackSheet chargeback={sheet} onClose={() => setSheet(null)} onAction={(a, c) => actions.start(a, c)} />
      {actions.dialogs}
    </div>
  );
}
