'use client';

import { useState } from 'react';
import { Button, StatusPill, type DataColumn } from '@pml.tickets/shared/components/m3';
import {
  PAYMENT_ATTEMPT_STATUSES,
  attemptAgeMinutes,
  isStuckAttempt,
  type TxPaymentAttempt,
} from '@pml.tickets/shared/api/admin/modules/transactions';
import { usePaymentAttemptSearch } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { PaymentRiskSummary } from './PaymentRisk';
import { ListCard } from '@/features/ledger/ListCard';
import { humanize, money } from '@/lib/format';
import { REVIEW_STATUS_LABELS, enumOptions } from '@/lib/enumLabels';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { PaymentDialog, PaymentSheet, ageText, reviewOf, type PaymentDialogKind } from './PaymentParts';

export const paymentColumns: Array<DataColumn<TxPaymentAttempt>> = [
  { id: 'attempt', header: 'Attempt', cell: (a) => <><span className="m3-mono">{a.attemptNumber}</span><br /><span className="m3-mono m3-muted">{a.ticketId}</span></> },
  { id: 'buyer', header: 'Buyer', cell: (a) => <><span className="m3-mono">{a.buyerId}</span><br /><span className="m3-muted">{humanize(a.provider)} · {a.payerPhone}</span></> },
  { id: 'amount', header: 'Amount', align: 'end', cell: (a) => <span className="m3-mono">{money(Number(a.amount))}</span> },
  { id: 'age', header: 'Age', cell: (a) => <>{ageText(attemptAgeMinutes(a))}{isStuckAttempt(a) ? <> <StatusPill tone="error">Stuck</StatusPill></> : null}</> },
  { id: 'status', header: 'Status', cell: (a) => <><StatusPill status={a.status} />{reviewOf(a) !== 'NONE' ? <><br /><StatusPill status={reviewOf(a)} /></> : null}</> },
  { id: 'failure', header: 'Failure', cell: (a) => (a.failureCode ? humanize(a.failureCode) : '—') },
];

export function PaymentsTab() {
  // Gateway provider codes are the mobile money operators' `providerCode`, read from the platform's list.
  const operators = useReferenceOptions<{ providerCode?: string }>('MOBILE_MONEY_OPERATOR');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [reference, setReference] = useState('');
  const [page, setPage] = useState(0);
  const { items: rows, pageInfo, loading, error, refetch } = usePaymentAttemptSearch({
    filter: {
      statuses: filters.st ? [filters.st as TxPaymentAttempt['status']] : undefined,
      reviewStatus: filters.rv || undefined,
      provider: filters.pv || undefined,
      riskLevel: filters.rk || undefined,
      reference: reference.trim() || undefined,
    },
    page,
    size: 20,
  });
  const items = rows as unknown as TxPaymentAttempt[];
  const [openId, setOpenId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<PaymentDialogKind | null>(null);
  const open = items.find((a) => a.id === openId) ?? null;
  return (
    <div className="m3-stack">
      <PaymentRiskSummary />
      <ListCard
        title="Payment attempts"
        subtitle="Mobile money payments through PawaPay. Unconfirmed attempts are never shown as failed to buyers."
        caption="Payment attempts"
        rows={items}
        columns={[...paymentColumns, { id: 'risk', header: 'Risk', cell: (a) => (a.riskLevel ? <StatusPill status={a.riskLevel.toUpperCase()} /> : '—') }]}
        getRowId={(a) => a.id}
        searchLabel="Search attempt, ticket or buyer"
        onSearchQuery={(q) => { setReference(q); setPage(0); }}
        searchText={(a) => `${a.attemptNumber} ${a.depositId} ${a.ticketId} ${a.buyerId} ${a.payerPhone}`}
        filters={[
          { id: 'st', label: 'Status', options: PAYMENT_ATTEMPT_STATUSES.map((v) => ({ value: v, label: humanize(v) })) },
          { id: 'rv', label: 'Review status', options: enumOptions(REVIEW_STATUS_LABELS) },
          { id: 'pv', label: 'Provider', options: operators.options.filter((o) => o.metadata.providerCode).map((o) => ({ value: o.metadata.providerCode as string, label: o.label })) },
          { id: 'rk', label: 'Risk', options: ['LOW', 'MEDIUM', 'HIGH'].map((v) => ({ value: v, label: humanize(v) })) },
        ]}
        onFilterChange={(v) => { setFilters(v); setPage(0); }}
        serverPage={{ page: page + 1, total: pageInfo.totalCount, onPage: (p) => setPage(p - 1) }}
        pageSize={20}
        csv={{
          name: 'Payment attempts',
          header: ['Attempt', 'Ticket', 'Buyer', 'Provider', 'Amount (K)', 'Status', 'Review'],
          row: (a) => [a.attemptNumber, a.ticketId, a.buyerId, a.provider, Number(a.amount), humanize(a.status), humanize(reviewOf(a))],
        }}
        loading={loading}
        error={error}
        onRetry={refetch}
        empty={{ title: 'No payment attempts match.' }}
        rowActions={(a) => <Button variant="tonal" size="sm" onClick={() => setOpenId(a.id)}>Open</Button>}
      />
      <PaymentSheet attempt={open} onClose={() => { setOpenId(null); setDialog(null); }} onDialog={setDialog} />
      <PaymentDialog kind={dialog} attempt={open} onClose={() => setDialog(null)} />
    </div>
  );
}
