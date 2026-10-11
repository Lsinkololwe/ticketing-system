'use client';

import { RowActions } from '@/components/console/RowActions';
import { useMemo, useState } from 'react';
import { Button, Card, CardHeader, DataTable, EmptyState, ErrorState, StatusPill } from '@pml.tickets/shared/components/m3';
import { useAdminEscrowAccounts } from '@pml.tickets/shared/api/admin/modules/finance';
import type { AdminEscrowAccountRow } from '@pml.tickets/shared/api/admin/modules/finance/finance.hooks';
import { FilterBar, useStaff } from '@/components/console';
import { formatDate, money } from '@/lib/format';
import { ConsistencyDialog } from './ConsistencyDialog';
import { EscrowSheet } from './EscrowSheet';
import { useEscrowActions } from './useEscrowActions';
import { asNumber, Mono, TwoLine } from './shared';
import { ESCROW_STATUS_LABELS, enumOptions } from '@/lib/enumLabels';


/** The event's name now, when it is not the name the account was opened under. */
function currentName(x: { eventTitle?: string | null; event?: { title?: string | null } | null }): string | undefined {
  const now = x.event?.title;
  return now && now !== x.eventTitle ? `now \u201c${now}\u201d` : undefined;
}

/** Escrow accounts: one per event, with lock/unlock in the row and everything else in the sheet. */
export function EscrowTab() {
  const staff = useStaff();
  const decide = staff.can('payoutDecide');
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [sheetId, setSheetId] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const status = filters.status && filters.status !== 'all' ? filters.status : null;
  const list = useAdminEscrowAccounts({ page, status: status as never });
  const actions = useEscrowActions(() => list.refetch());

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return list.accounts.filter(
      (x) =>
        (!q || [x.accountNumber, x.eventTitle, x.event?.title, x.organization?.name].some((v) => (v ?? '').toLowerCase().includes(q))) &&
        (!filters.lock || filters.lock === 'all' || !!x.lockUntil === (filters.lock === 'locked'))
    );
  }, [list.accounts, query, filters.lock]);

  return (
    <div className="m3-stack">
      <Card as="section" aria-label="Escrow accounts">
        <CardHeader title="Escrow accounts" subtitle="One account per event. Every movement is a double-entry journal pair." />
        <FilterBar
          searchLabel="Search account or event"
          query={query}
          onQuery={setQuery}
          filters={[
            { id: 'status', label: 'Status', options: enumOptions(ESCROW_STATUS_LABELS) },
            { id: 'lock', label: 'Lock', options: [{ value: 'locked', label: 'Locked' }, { value: 'open', label: 'Not locked' }] },
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
            <Button variant="tonal" size="sm" onClick={() => setChecking(true)}>
              Consistency check
            </Button>
          }
        />
        <DataTable<AdminEscrowAccountRow>
          caption="Escrow accounts"
          rows={rows}
          getRowId={(x) => x.id}
          loading={list.loading && list.accounts.length === 0}
          error={list.error && list.accounts.length === 0 ? <ErrorState error={list.error} onRetry={list.refetch} /> : undefined}
          empty={<EmptyState title="No escrow accounts yet." description={list.accounts.length > 0 ? 'No accounts match the filters.' : undefined} />}
          columns={[
            { id: 'acct', header: 'Account', rowHeader: true, cell: (x) => <Mono>{x.accountNumber}</Mono> },
            { id: 'event', header: 'Event', cell: (x) => <TwoLine main={x.eventTitle ?? '—'} sub={[x.organization?.name, currentName(x)].filter(Boolean).join(' · ')} /> },
            { id: 'status', header: 'Status', cell: (x) => <TwoLine main={<StatusPill status={x.status} />} sub={x.lockUntil ? `Locked until ${formatDate(x.lockUntil)}` : undefined} /> },
            { id: 'bal', header: 'Balance', align: 'end', cell: (x) => <Mono>{money(asNumber(x.currentBalance))}</Mono> },
            { id: 'dep', header: 'Deposits', align: 'end', cell: (x) => <Mono>{money(asNumber(x.totalDeposits))}</Mono> },
            { id: 'ref', header: 'Refunds', align: 'end', cell: (x) => <Mono>{money(asNumber(x.totalRefunds))}</Mono> },
            { id: 'elig', header: 'Payout eligible', cell: (x) => formatDate(x.payoutEligibleAt) },
          ]}
          rowActions={(x) => (
            <RowActions
              name={`escrow ${x.accountNumber}`}
              primary={{ label: 'Open', onSelect: () => setSheetId(x.id) }}
              items={[
                x.lockUntil
                  ? { id: 'unlock', label: 'Unlock', disabled: !decide, onSelect: () => actions.start('unlock', x) }
                  : { id: 'lock', label: 'Lock', disabled: !decide || x.status === 'CLOSED', onSelect: () => actions.start('lock', x) },
              ]}
            />
          )}
          actionsHeader="Actions"
          pagination={{ page: page + 1, pageSize: list.pageInfo.pageSize, total: list.pageInfo.totalCount, onPageChange: (n) => setPage(n - 1) }}
        />
      </Card>
      <EscrowSheet key={sheetId ?? 'none'} accountId={sheetId} onClose={() => setSheetId(null)} onAction={actions.start} />
      {actions.dialogs}
      <ConsistencyDialog open={checking} onClose={() => setChecking(false)} />
    </div>
  );
}
