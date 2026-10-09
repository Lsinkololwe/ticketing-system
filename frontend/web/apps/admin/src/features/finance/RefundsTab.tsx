'use client';

import { RowActions } from '@/components/console/RowActions';
import { useMemo, useState } from 'react';
import { BulkBar, Button, Card, CardHeader, ConfirmDialog, DataTable, EmptyState, ErrorState, KpiCard, KpiGrid, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import { useRefundStatusCount } from '@pml.tickets/shared/api/admin/modules/finance';
import { usePendingRefunds, useRefundOps, useRefundOpsList, type RefundOpsRow } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { FilterBar, useStaff } from '@/components/console';
import { ago, humanize, money } from '@/lib/format';
import { RefundSheet } from './RefundSheet';
import { refundEscalation, useRefundActions } from './useRefundActions';
import { asNumber, FormDialog, Mono, TwoLine, useCopyCsv } from './shared';
import { REFUND_STATUS_LABELS, REFUND_TYPE_LABELS, enumOptions } from '@/lib/enumLabels';


function EscalationPill({ level }: { level: 0 | 1 | 2 }) {
  if (level === 2) return <span className="m3-pill" data-tone="error">Escalated again · 5 d</span>;
  if (level === 1) return <span className="m3-pill" data-tone="warning">Escalated · 2 d</span>;
  return null;
}

/** Refund requests: pending queue with escalation badges, approve/reject/process, bulk approve, new admin refund. */
export function RefundsTab() {
  const staff = useStaff();
  const { show } = useSnackbar();
  const copyCsv = useCopyCsv();
  const decide = staff.can('payoutDecide');
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [sheet, setSheet] = useState<RefundOpsRow | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const active = (k: string) => (filters[k] && filters[k] !== 'all' ? filters[k] : null);
  const list = useRefundOpsList({ page, status: active('status'), requestType: active('type') });
  const pending = usePendingRefunds();
  const processing = useRefundStatusCount('PROCESSING' as never);
  const ops = useRefundOps();
  const refresh = () => {
    list.refetch();
    setSheet(null);
  };
  const actions = useRefundActions(refresh);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    const esc = active('esc');
    return list.refunds
      .filter((r) => (!q || [r.requestId, r.ticketNumber, r.buyerId, r.eventId].some((x) => x.toLowerCase().includes(q))) && (!esc || refundEscalation(r) === Number(esc)))
      .sort((a, b) => (a.status === 'PENDING' ? 0 : 1) - (b.status === 'PENDING' ? 0 : 1) || (a.requestedAt ?? '').localeCompare(b.requestedAt ?? ''));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list.refunds, query, filters.esc]);

  const pendingAmount = pending.rows.reduce((a, r) => a + asNumber(r.refundAmount), 0);
  const partial = pending.total != null && pending.total > pending.rows.length;
  const eligible = rows.filter((r) => selected.has(r.id) && r.status === 'PENDING' && !(r.requestType === 'ADMIN_INITIATED' && r.requestedById === staff.id));
  const skipped = selected.size - eligible.length;

  return (
    <div className="m3-stack">
      <Card as="section" aria-label="Refund summary">
        <Card>
        <KpiGrid flat>
          <KpiCard label="Pending" value={pending.total == null ? '—' : `${pending.total} · ${money(pendingAmount)}${partial ? '+' : ''}`} />
          <KpiCard label="Escalated (2 d)" value={pending.total == null ? '—' : pending.rows.filter((r) => refundEscalation(r) === 1).length} />
          <KpiCard label="Escalated again (5 d)" value={pending.total == null ? '—' : pending.rows.filter((r) => refundEscalation(r) === 2).length} />
          <KpiCard label="In processing" value={processing.count ?? '—'} />
        </KpiGrid>
        </Card>
      </Card>
      <Card as="section" aria-label="Refund requests">
        <CardHeader
          title="Refund requests"
          subtitle="Every refund except an event cancellation needs a person's approval. Pending refunds escalate to finance after 2 days and again after 5 days; they are never approved automatically."
        />
        <FilterBar
          searchLabel="Search refund, ticket or buyer"
          query={query}
          onQuery={setQuery}
          filters={[
            { id: 'status', label: 'Status', options: enumOptions(REFUND_STATUS_LABELS) },
            { id: 'type', label: 'Type', options: enumOptions(REFUND_TYPE_LABELS) },
            { id: 'esc', label: 'Escalation', options: [{ value: '1', label: 'Escalated (2 d)' }, { value: '2', label: 'Escalated again (5 d)' }] },
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
            <>
              <Button
                variant="text"
                size="sm"
                icon="copy"
                disabled={rows.length === 0}
                onClick={() =>
                  void copyCsv([
                    ['Refund', 'Ticket', 'Event', 'Amount (K)', 'Type', 'Status', 'Requested'],
                    ...rows.map((r) => [r.requestId, r.ticketNumber, r.eventId, asNumber(r.refundAmount), humanize(r.requestType), humanize(r.status), r.requestedAt]),
                  ])
                }
              >
                Copy CSV
              </Button>
              <Button variant="filled" size="sm" icon="add" disabled={!decide} onClick={() => setCreating(true)}>
                New refund
              </Button>
            </>
          }
        />
        <BulkBar count={selected.size}>
          <Button variant="tonal" size="sm" disabled={!decide} onClick={() => setBulkOpen(true)}>
            Approve selected
          </Button>
          <Button variant="text" size="sm" onClick={() => setSelected(new Set())}>
            Clear selection
          </Button>
        </BulkBar>
        <DataTable<RefundOpsRow>
          caption="Refund requests"
          rows={rows}
          getRowId={(r) => r.id}
          loading={list.loading && list.refunds.length === 0}
          error={list.error && list.refunds.length === 0 ? <ErrorState error={list.error} onRetry={list.refetch} /> : undefined}
          empty={<EmptyState title="No refund requests." description={list.refunds.length > 0 ? 'No requests match the filters.' : undefined} />}
          selectable
          selectedIds={selected}
          onSelectionChange={setSelected}
          columns={[
            { id: 'refund', header: 'Refund', rowHeader: true, cell: (r) => <TwoLine main={<Mono>{r.requestId}</Mono>} sub={r.ticketNumber} monoSub /> },
            { id: 'who', header: 'Buyer and event', cell: (r) => <TwoLine main={<Mono>{r.buyerId.slice(-8)}</Mono>} sub={r.eventId.slice(-8)} monoSub /> },
            { id: 'amount', header: 'Amount', align: 'end', cell: (r) => <Mono>{money(asNumber(r.refundAmount))}</Mono> },
            { id: 'type', header: 'Type', cell: (r) => humanize(r.requestType) },
            { id: 'policy', header: 'Policy', cell: (r) => `${humanize(r.policyApplied)}${r.refundPercentage == null ? '' : ` · ${r.refundPercentage}%`}` },
            {
              id: 'age',
              header: 'Age',
              cell: (r) => (
                <>
                  {ago(r.requestedAt)}
                  {refundEscalation(r) ? (
                    <>
                      <br />
                      <EscalationPill level={refundEscalation(r)} />
                    </>
                  ) : null}
                </>
              ),
            },
            { id: 'status', header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
          ]}
          rowActions={(r) => (
            <RowActions
              name={`refund ${r.requestId}`}
              primary={{ label: 'Details', onSelect: () => setSheet(r) }}
              items={[
                ...(r.status === 'PENDING'
                  ? [
                      { id: 'approve', label: 'Approve', disabled: !decide, onSelect: () => actions.start('approve', r) },
                      { id: 'reject', label: 'Reject…', danger: true, disabled: !decide, onSelect: () => actions.start('reject', r) },
                    ]
                  : []),
                ...(r.status === 'APPROVED' || r.status === 'FAILED'
                  ? [{ id: 'process', label: r.status === 'FAILED' ? 'Process again' : 'Process', disabled: !decide, onSelect: () => actions.start('process', r) }]
                  : []),
              ]}
            />
          )}
          actionsHeader="Actions"
          pagination={{ page: page + 1, pageSize: list.pageInfo.pageSize, total: list.pageInfo.totalCount ?? 0, onPageChange: (n) => setPage(n - 1) }}
        />
      </Card>

      <RefundSheet refund={sheet} onClose={() => setSheet(null)} onAction={(a, r) => actions.start(a, r)} />
      {actions.dialogs}

      <ConfirmDialog
        open={bulkOpen}
        onClose={() => setBulkOpen(false)}
        title={`Approve ${eligible.length} refund${eligible.length === 1 ? '' : 's'}?`}
        description={
          eligible.length
            ? `Total ${money(eligible.reduce((a, r) => a + asNumber(r.refundAmount), 0))}.${skipped ? ` ${skipped} selected refund${skipped > 1 ? 's are' : ' is'} not pending or need another approver and will be skipped.` : ''}`
            : 'None of the selected refunds can be approved by you.'
        }
        confirmLabel={eligible.length ? 'Approve all' : 'Close'}
        loading={ops.submitting}
        onConfirm={() => {
          if (!eligible.length) {
            setBulkOpen(false);
            return;
          }
          void ops.bulkApprove(eligible.map((r) => r.id)).then((res) => {
            if (res.success) {
              const d = (res.data as { bulkApproveRefunds?: { processedCount: number; failedCount: number } } | undefined)?.bulkApproveRefunds;
              show(`${d?.processedCount ?? eligible.length} refunds approved${d?.failedCount ? `, ${d.failedCount} could not be approved` : ''}`);
              setSelected(new Set());
              list.refetch();
            } else {
              show(res.message ?? 'That did not work. Try again.');
            }
            setBulkOpen(false);
          });
        }}
      />

      <FormDialog
        open={creating}
        title="New admin-initiated refund"
        body="Creates a pending refund. A second person must approve it."
        confirmLabel="Create refund"
        successMessage="Refund request created. It needs a second approver"
        fields={[
          { id: 'ticketId', label: 'Ticket ID', required: true, helper: 'The refund amount comes from the refund policy for this ticket.' },
          { id: 'reason', label: 'Reason', type: 'textarea', required: true, minLength: 5 },
        ]}
        onClose={() => setCreating(false)}
        onDone={() => list.refetch()}
        onSubmit={(v) => ops.createAdminRefund(v.ticketId, v.reason)}
      />
    </div>
  );
}
