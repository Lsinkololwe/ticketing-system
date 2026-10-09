'use client';

import { useMemo, useState } from 'react';
import {
  Button,
  Card,
  CardHeader,
  DataTable,
  ErrorState,
  EmptyState,
  StatusPill,
  TextField,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import { toNumber, useTrialBalance } from '@pml.tickets/shared/api/admin/modules/ledger';
import { csvText, formatDate, humanize, money } from '@/lib/format';

const todayDate = () => new Date().toISOString().slice(0, 10);

export function TrialBalanceTab() {
  const [asOf, setAsOf] = useState(todayDate());
  const snackbar = useSnackbar();
  const { items, loading, error, refetch } = useTrialBalance(asOf ? `${asOf}T23:59:59Z` : null);

  const rows = useMemo(
    () =>
      items.map((b) => {
        const net = toNumber(b.debitBalance) - toNumber(b.creditBalance);
        return { ...b, debit: net > 0 ? net : 0, credit: net < 0 ? -net : 0 };
      }),
    [items]
  );
  const td = rows.reduce((s, r) => s + r.debit, 0);
  const tc = rows.reduce((s, r) => s + r.credit, 0);
  const ok = Math.abs(td - tc) < 0.005;

  type Row = (typeof rows)[number] & { total?: boolean };
  const shown: Row[] = rows.length > 0 ? [...rows, { accountCode: 'TOTALS', accountName: 'Totals', accountType: '', debit: td, credit: tc, total: true } as unknown as Row] : rows;
  const columns: Array<DataColumn<Row>> = [
    { id: 'code', header: 'Code', cell: (r) => (r.total ? '' : <span className="m3-mono">{r.accountCode}</span>) },
    { id: 'name', header: 'Account', rowHeader: true, cell: (r) => (r.total ? <b data-testid="tb-totals">Totals</b> : r.accountName) },
    { id: 'type', header: 'Type', cell: (r) => (r.total ? '' : humanize(r.accountType)) },
    { id: 'dr', header: 'Debit', align: 'end', cell: (r) => <span className="m3-mono">{r.total ? <b>{money(r.debit)}</b> : r.debit ? money(r.debit) : '—'}</span> },
    { id: 'cr', header: 'Credit', align: 'end', cell: (r) => <span className="m3-mono">{r.total ? <b>{money(r.credit)}</b> : r.credit ? money(r.credit) : '—'}</span> },
  ];

  const copy = async () => {
    const text = csvText([
      ['Code', 'Account', 'Type', 'Debit', 'Credit'],
      ...rows.map((r) => [r.accountCode, r.accountName, humanize(r.accountType), r.debit ? r.debit.toFixed(2) : '', r.credit ? r.credit.toFixed(2) : '']),
    ]);
    try {
      await navigator.clipboard.writeText(text);
      snackbar.show(`Trial balance as of ${formatDate(asOf)} copied`);
    } catch {
      snackbar.show('Could not copy to the clipboard');
    }
  };

  return (
    <Card>
      <CardHeader
        title="Trial balance"
        subtitle="Posted entries only, up to the date you choose."
        actions={
          <div className="m3-row">
            {rows.length > 0 ? <StatusPill tone={ok ? 'success' : 'error'}>{ok ? 'Debits equal credits' : 'Out of balance'}</StatusPill> : null}
            <Button variant="tonal" size="sm" icon="copy" onClick={copy} disabled={rows.length === 0}>Copy CSV</Button>
          </div>
        }
      />
      <div className="m3-toolbar adm-filterbar">
        <TextField label="As of date" type="date" density="compact" value={asOf} onChange={(e) => setAsOf(e.target.value)} />
      </div>
      <DataTable
        caption="Trial balance"
        columns={columns}
        rows={shown}
        getRowId={(r) => r.accountCode}
        loading={loading && rows.length === 0}
        error={error && rows.length === 0 ? <ErrorState error={error} onRetry={refetch} /> : undefined}
        empty={<EmptyState title="Nothing posted yet." description="Accounts appear here once journal entries are posted." />}
      />
    </Card>
  );
}
