'use client';

import { Button, DataTable, KeyValue, SideSheet } from '@pml.tickets/shared/components/m3';
import type { EscrowAccountRow, EscrowTransactionRow } from '@/lib/api/finance';
import { formatEventDate, formatMoney } from '@/lib/format/figure';
import { signedMoney } from '@/lib/finance/payouts';
import { Status, statusLabel } from '@/components/console/Status';
import { usePaged } from './usePaged';

interface Props {
  account: EscrowAccountRow | null;
  rows: EscrowTransactionRow[];
  loading: boolean;
  error?: Error | null;
  onClose: () => void;
}

const money = (v: string | number | null | undefined, c?: string) => formatMoney(v, c, { decimals: 2 });

/** Right-hand panel with one escrow account's totals and its double-entry ledger. */
export function EscrowLedgerSheet({ account, rows, loading, error, onClose }: Props) {
  const paged = usePaged(rows, 6);
  if (!account) return null;
  const c = account.currency;
  return (
    <SideSheet
      open
      onClose={onClose}
      title={`Escrow ledger · ${account.eventTitle ?? 'Event'}`}
      actions={<Button variant="filled" onClick={onClose}>Close</Button>}
    >
      <KeyValue
        columns
        items={[
          { label: 'Status', value: <Status status={account.status} /> },
          { label: 'Current balance', value: money(account.currentBalance, c) },
          { label: 'Total deposits', value: money(account.totalDeposits, c) },
          { label: 'Refunds', value: money(account.totalRefunds, c) },
          { label: 'Commission', value: money(account.totalCommissions, c) },
          { label: 'Withdrawals', value: money(account.totalWithdrawals, c) },
          { label: 'Pending withdrawals', value: money(account.pendingWithdrawals ?? 0, c) },
          { label: 'Payout eligible', value: account.payoutEligibleAt ? formatEventDate(account.payoutEligibleAt) : '—' },
        ]}
      />
      <p className="m3-page-sub">Every movement is a double-entry journal pair. This list shows the escrow side.</p>
      <DataTable
        caption="Escrow ledger"
        loading={loading}
        error={error ? <span>Could not load the ledger.</span> : undefined}
        empty={<span>No transactions yet.</span>}
        rows={paged.rows}
        getRowId={(r) => r.id}
        pagination={paged.pagination}
        columns={[
          { id: 'at', header: 'Date', cell: (r) => formatEventDate(r.timestamp) },
          { id: 'entry', header: 'Entry', cell: (r) => statusLabel(r.category) },
          { id: 'je', header: 'Journal', cell: (r) => <span className="m3-mono">{r.journalEntryId ?? '—'}</span> },
          {
            id: 'amount',
            header: 'Amount',
            align: 'end',
            cell: (r) => (
              <span className={Number(r.amount) >= 0 ? 'm3-pos' : 'm3-neg'}>
                {signedMoney(r.amount, r.currency)}
              </span>
            ),
          },
          { id: 'bal', header: 'Balance', align: 'end', cell: (r) => <span className="m3-mono">{money(r.balanceAfter, r.currency)}</span> },
        ]}
      />
    </SideSheet>
  );
}
