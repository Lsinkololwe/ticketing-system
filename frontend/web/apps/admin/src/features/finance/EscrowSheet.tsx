'use client';

import { useState } from 'react';
import { Banner, Button, DataTable, EmptyState, ErrorState, KeyValue, SideSheet, StatusPill } from '@pml.tickets/shared/components/m3';
import { useEscrowOpsDetail, useEscrowTransactions } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { useStaff } from '@/components/console';
import { formatDate, formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import type { EscrowAction, EscrowTarget } from './useEscrowActions';
import { asNumber, Mono } from './shared';

const PAGE = 6;

/** Escrow account detail: figures, paginated transactions and the lock / suspend / eligible / close actions. */
export function EscrowSheet({ accountId, onClose, onAction }: { accountId: string | null; onClose: () => void; onAction: (a: EscrowAction, t: EscrowTarget) => void }) {
  const staff = useStaff();
  const [page, setPage] = useState(0);
  const detail = useEscrowOpsDetail(accountId);
  const tx = useEscrowTransactions(accountId, { page, size: PAGE });
  const x = detail.account;
  if (!accountId) return null;
  const decide = staff.can('payoutDecide');
  const st = x?.status ?? '';
  const locked = !!x?.lockUntil;
  const target: EscrowTarget | null = x ? { id: x.id, accountNumber: x.accountNumber, status: x.status, currentBalance: x.currentBalance } : null;
  const act = (a: EscrowAction, label: string, danger?: boolean) => (
    <Button variant="tonal" danger={danger} disabled={!decide && a !== 'close'} onClick={() => target && onAction(a, target)}>
      {label}
    </Button>
  );

  return (
    <SideSheet
      open
      onClose={onClose}
      title={x ? `Escrow ${x.accountNumber}` : 'Escrow account'}
      subtitle={x?.eventTitle ?? undefined}
      actions={
        x && decide ? (
          <>
            {locked ? act('unlock', 'Unlock') : act('lock', 'Lock…')}
            {st === 'SUSPENDED' ? act('reactivate', 'Reactivate') : st !== 'CLOSED' ? act('suspend', 'Suspend…') : null}
            {['ACTIVE', 'HOLD'].includes(st) ? act('eligible', 'Mark payout eligible…') : null}
            {st !== 'CLOSED' && staff.can('closeEscrow') ? act('close', 'Close account…', true) : null}
          </>
        ) : undefined
      }
    >
      <div className="m3-stack">
        {detail.loading && !x ? <p aria-busy="true">Loading account…</p> : null}
        {detail.error && !x ? <ErrorState error={detail.error} onRetry={detail.refetch} /> : null}
        {x ? (
          <>
            <div className="m3-row">
              <StatusPill status={x.status} />
              {locked ? <StatusPill tone="warning">Locked until {formatDate(x.lockUntil)}</StatusPill> : null}
            </div>
            {!decide ? <p className="m3-muted">{needText('payoutDecide')}</p> : null}
            {decide && st !== 'CLOSED' && !staff.can('closeEscrow') ? <p className="m3-muted">{needText('closeEscrow')}</p> : null}
            <KeyValue columns
              items={[
                { label: 'Event', value: x.eventTitle ?? '—' },
                { label: 'Organization', value: x.organizerName ?? '—' },
                { label: 'Current balance', value: <Mono>{money(asNumber(x.currentBalance))}</Mono> },
                { label: 'Total deposits', value: <Mono>{money(asNumber(x.totalDeposits))}</Mono> },
                { label: 'Total refunds', value: <Mono>{money(asNumber(x.totalRefunds))}</Mono> },
                { label: 'Total commissions', value: <Mono>{money(asNumber(x.totalCommissions))}</Mono> },
                { label: 'Total withdrawals', value: <Mono>{money(asNumber(x.totalWithdrawals))}</Mono> },
                { label: 'Pending withdrawals', value: <Mono>{money(asNumber(x.pendingWithdrawals))}</Mono> },
                { label: 'Payout eligible from', value: formatDate(x.payoutEligibleAt) },
              ]}
            />
          </>
        ) : null}
        <section aria-label="Transactions">
          <h3 className="m3-card__title">Transactions</h3>
          {tx.error && tx.transactions.length === 0 ? <Banner tone="error">Transactions could not be loaded.</Banner> : null}
          <DataTable
            caption="Escrow transactions"
            density="compact"
            rows={tx.transactions}
            getRowId={(t) => t.id}
            loading={tx.loading && tx.transactions.length === 0}
            empty={<EmptyState title="No transactions yet." />}
            columns={[
              { id: 'date', header: 'Date', cell: (t) => formatDateTime(t.timestamp) },
              { id: 'type', header: 'Type', cell: (t) => humanize(t.type) },
              { id: 'ref', header: 'Reference', cell: (t) => <Mono>{t.payoutRequestId ?? t.refundRequestId ?? t.chargebackId ?? t.description ?? '—'}</Mono> },
              { id: 'amount', header: 'Amount', align: 'end', cell: (t) => <Mono>{`${asNumber(t.amount) >= 0 ? '+' : ''}${money(asNumber(t.amount))}`}</Mono> },
              { id: 'after', header: 'Balance after', align: 'end', cell: (t) => <Mono>{money(asNumber(t.balanceAfter))}</Mono> },
            ]}
            pagination={{ page: page + 1, pageSize: PAGE, total: tx.pageInfo.totalCount ?? 0, onPageChange: (n) => setPage(n - 1) }}
          />
        </section>
      </div>
    </SideSheet>
  );
}
