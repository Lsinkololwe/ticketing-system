'use client';

import { RowActions } from '@/components/console/RowActions';
import { useMemo, useState } from 'react';
import { BulkBar, Button, Card, CardHeader, ConfirmDialog, DataTable, EmptyState, ErrorState, KpiCard, KpiGrid, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import { usePayoutRequestStats } from '@pml.tickets/shared/api/admin/modules/finance';
import { usePayoutOps, usePayoutOpsList, type PayoutOpsRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { FilterBar, useStaff } from '@/components/console';
import { formatDateTime, humanize, money } from '@/lib/format';
import { PayoutSheet } from './PayoutSheet';
import { destinationOf, eventOf, usePayoutActions } from './usePayoutActions';
import { asNumber, Mono, TwoLine, useCopyCsv } from './shared';
import { PAYOUT_STATUS_LABELS, REVIEW_STATUS_LABELS, PAYOUT_METHOD_LABELS, enumOptions } from '@/lib/enumLabels';


/** Payout requests: summary cards, filterable table, bulk retry and the right-hand detail sheet. */
export function PayoutsTab() {
  const staff = useStaff();
  const { show } = useSnackbar();
  const copyCsv = useCopyCsv();
  const decide = staff.can('payoutDecide');
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [sheet, setSheet] = useState<PayoutOpsRow | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);

  const list = usePayoutOpsList({ page, status: filters.status && filters.status !== 'all' ? filters.status : null, payoutMethod: filters.method && filters.method !== 'all' ? filters.method : null });
  const stats = usePayoutRequestStats();
  const ops = usePayoutOps();
  const refreshAll = () => {
    list.refetch();
    stats.refetch();
  };
  const actions = usePayoutActions(() => {
    refreshAll();
    setSheet(null);
  });

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return list.payouts.filter(
      (p) =>
        (!q || [p.requestId, p.eventTitle, p.event?.title, p.organization?.name, p.requestedById].some((x) => (x ?? '').toLowerCase().includes(q))) &&
        (!filters.review || filters.review === 'all' || (p.reviewStatus ?? 'NONE') === filters.review)
    );
  }, [list.payouts, query, filters.review]);

  const s = stats.stats;
  const tiles = (
    <Card>
    <KpiGrid flat>
      <KpiCard label="Awaiting approval" value={s ? `${s.pendingPayoutRequests} · ${money(asNumber(s.pendingPayoutAmount))}` : '—'} />
      <KpiCard label="Approved or processing" value={s ? `${(s.approvedPayoutRequests ?? 0) + (s.processingPayoutRequests ?? 0)} requests` : '—'} />
      <KpiCard label="On hold or failed" value={s ? `${s.failedPayoutRequests ?? 0} requests` : '—'} />
      <KpiCard label="Completed to date" value={s ? `${s.completedPayoutRequests ?? 0} requests` : '—'} />
    </KpiGrid>
    </Card>
  );

  const failedSelected = rows.filter((p) => selected.has(p.id) && p.status === 'FAILED');
  const skipped = selected.size - failedSelected.length;

  return (
    <div className="m3-stack">
      <Card as="section" aria-label="Payout summary">
        {tiles}
      </Card>
      <Card as="section" aria-label="Payout requests">
        <CardHeader
          title="Payout requests"
          subtitle="Approval is dual control: the person who requested a payout cannot approve it. No platform fee is taken: net equals the escrow balance."
        />
        <FilterBar
          searchLabel="Search request, event or organization"
          query={query}
          onQuery={setQuery}
          filters={[
            { id: 'status', label: 'Status', options: enumOptions(PAYOUT_STATUS_LABELS) },
            { id: 'review', label: 'Review status', options: enumOptions(REVIEW_STATUS_LABELS) },
            { id: 'method', label: 'Method', options: enumOptions(PAYOUT_METHOD_LABELS) },
          ]}
          values={filters}
          onFilter={(id, v) => {
            setFilters((f) => ({ ...f, [id]: v }));
            setPage(0);
          }}
          onClear={() => {
            setQuery('');
            setFilters({});
            setPage(0);
          }}
          actions={
            <Button
              variant="text"
              size="sm"
              icon="copy"
              disabled={rows.length === 0}
              onClick={() =>
                void copyCsv([
                  ['Request', 'Event', 'Organization', 'Amount (K)', 'Status', 'Requested by', 'Created'],
                  ...rows.map((p) => [p.requestId, eventOf(p), p.organization?.name, asNumber(p.requestedAmount), humanize(p.status), p.requestedById, p.requestedAt]),
                ])
              }
            >
              Copy CSV
            </Button>
          }
        />
        <BulkBar count={selected.size}>
          <Button variant="tonal" size="sm" disabled={!decide} onClick={() => setBulkOpen(true)}>
            Retry selected
          </Button>
          <Button variant="text" size="sm" onClick={() => setSelected(new Set())}>
            Clear selection
          </Button>
        </BulkBar>
        <DataTable<PayoutOpsRow>
          caption="Payout requests"
          rows={rows}
          getRowId={(p) => p.id}
          loading={list.loading && list.payouts.length === 0}
          error={list.error && list.payouts.length === 0 ? <ErrorState error={list.error} onRetry={list.refetch} /> : undefined}
          empty={<EmptyState title="No payout requests yet." description={list.payouts.length > 0 ? 'No requests match the filters.' : undefined} />}
          selectable
          selectedIds={selected}
          onSelectionChange={setSelected}
          columns={[
            { id: 'request', header: 'Request', rowHeader: true, cell: (p) => <TwoLine main={<Mono>{p.requestId}</Mono>} sub={formatDateTime(p.requestedAt)} /> },
            { id: 'event', header: 'Event', cell: (p) => <TwoLine main={eventOf(p)} sub={p.organization?.name} /> },
            { id: 'amount', header: 'Amount', align: 'end', cell: (p) => <Mono>{money(asNumber(p.requestedAmount))}</Mono> },
            { id: 'dest', header: 'Destination', cell: (p) => <TwoLine main={humanize(p.payoutMethod)} sub={destinationOf(p)} monoSub /> },
            { id: 'by', header: 'Requested by', cell: (p) => <Mono>{p.requestedById.slice(-8)}</Mono> },
            {
              id: 'status',
              header: 'Status',
              cell: (p) => (
                <>
                  <StatusPill status={p.status} />
                  {p.reviewStatus && p.reviewStatus !== 'NONE' ? (
                    <>
                      <br />
                      <StatusPill status={p.reviewStatus} />
                    </>
                  ) : null}
                </>
              ),
            },
          ]}
          rowActions={(p) => (
            <RowActions
              name={`payout ${p.requestId}`}
              primary={{ label: 'Open', onSelect: () => setSheet(p) }}
              items={[
                ...(p.status === 'PENDING' ? [{ id: 'approve', label: 'Approve', disabled: !decide, onSelect: () => actions.start('approve', p) }] : []),
                ...(p.status === 'APPROVED' ? [{ id: 'process', label: 'Process', disabled: !decide, onSelect: () => actions.start('process', p) }] : []),
                ...(p.status === 'FAILED' ? [{ id: 'retry', label: 'Retry', disabled: !decide, onSelect: () => actions.start('retry', p) }] : []),
              ]}
            />
          )}
          actionsHeader="Actions"
          pagination={{ page: page + 1, pageSize: list.pageInfo.pageSize, total: list.pageInfo.totalCount ?? 0, onPageChange: (n) => setPage(n - 1) }}
        />
      </Card>

      <PayoutSheet payout={sheet} onClose={() => setSheet(null)} onAction={(a, p) => actions.start(a, p)} />
      {actions.dialogs}

      <ConfirmDialog
        open={bulkOpen}
        onClose={() => setBulkOpen(false)}
        title={`Retry ${failedSelected.length} failed payout${failedSelected.length === 1 ? '' : 's'}?`}
        description={
          failedSelected.length
            ? `Each payout is reserved again and sent for processing.${skipped ? ` ${skipped} selected request${skipped > 1 ? 's are' : ' is'} not failed and will be skipped.` : ''}`
            : 'None of the selected requests has failed, so there is nothing to retry.'
        }
        confirmLabel={failedSelected.length ? 'Retry all' : 'Close'}
        loading={ops.submitting}
        onConfirm={() => {
          if (!failedSelected.length) {
            setBulkOpen(false);
            return;
          }
          void ops.bulkRetry(failedSelected.map((p) => p.id)).then((res) => {
            if (res.success) {
              const r = (res.data as { bulkRetryFailedPayouts?: { processedCount: number; failedCount: number } } | undefined)?.bulkRetryFailedPayouts;
              show(r ? `${r.processedCount} payouts retried${r.failedCount ? `, ${r.failedCount} could not be retried` : ''}` : `${failedSelected.length} payouts retried`);
              setSelected(new Set());
              refreshAll();
            } else {
              show(res.message ?? 'That did not work. Try again.');
            }
            setBulkOpen(false);
          });
        }}
      />
    </div>
  );
}
