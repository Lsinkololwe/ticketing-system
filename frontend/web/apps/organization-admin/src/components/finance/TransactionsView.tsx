'use client';

import { enumValues, TRANSACTION_TYPE_LABELS } from '@/lib/format/enumLabels';
import { useMemo, useState } from 'react';
import { Card, DataTable, PageHeader, Select, TextField } from '@pml.tickets/shared/components/m3';
import type { TransactionRowVM } from '@pml.tickets/shared/api/organization-admin/modules/finance';
import { formatDateTime } from '@/lib/bookings/format';
import { signedMoney } from '@/lib/finance/payouts';
import { statusLabel } from '@/components/console/Status';
import { DataState } from '@/components/console/DataState';
import { usePaged } from './usePaged';

export const TRANSACTION_TYPES = enumValues(TRANSACTION_TYPE_LABELS);

export interface TransactionsViewProps {
  transactions: TransactionRowVM[];
  events: Array<{ id: string; title: string }>;
  type: string;
  eventId: string;
  onTypeChange: (type: string) => void;
  onEventChange: (eventId: string) => void;
  loading: boolean;
  error?: Error | null;
  onRetry?: () => void;
}

/** Every money movement for the organization's events. */
export function TransactionsView(p: TransactionsViewProps) {
  const [q, setQ] = useState('');
  const list = useMemo(
    () => p.transactions.filter((t) => !q || `${t.reference ?? ''} ${t.eventTitle ?? ''} ${t.description ?? ''}`.toLowerCase().includes(q.toLowerCase())),
    [p.transactions, q]
  );
  const paged = usePaged(list, 10);
  return (
    <div data-testid="transactions-page">
      <PageHeader title="Transactions" subtitle="Every money movement for your events." />
      <Card>
        <div className="m3-toolbar">
          <TextField density="compact" label="Search transactions" placeholder="Reference or event" value={q} onChange={(e) => { setQ(e.target.value); paged.reset(); }} />
          <Select density="compact" label="Type" value={p.type} onChange={(e) => p.onTypeChange(e.target.value)}>
            <option value="all">All types</option>
            {TRANSACTION_TYPES.map((t) => <option key={t} value={t}>{TRANSACTION_TYPE_LABELS[t]}</option>)}
          </Select>
          <Select density="compact" label="Event" value={p.eventId} onChange={(e) => p.onEventChange(e.target.value)}>
            <option value="all">All events</option>
            {p.events.map((e) => <option key={e.id} value={e.id}>{e.title}</option>)}
          </Select>
        </div>
        <DataState loading={p.loading && p.transactions.length === 0} error={p.error} onRetry={p.onRetry}>
          <DataTable
            caption="Transactions"
            rows={paged.rows}
            getRowId={(t) => t.id}
            pagination={paged.pagination}
            empty={<span>No transactions match.</span>}
            columns={[
              { id: 'at', header: 'Date', cell: (t) => formatDateTime(t.timestamp) },
              { id: 'type', header: 'Type', cell: (t) => statusLabel(t.type) },
              { id: 'event', header: 'Event', cell: (t) => t.eventTitle ?? '—' },
              { id: 'ref', header: 'Reference', cell: (t) => <span className="m3-mono">{t.reference ?? t.description ?? '—'}</span> },
              {
                id: 'amount',
                header: 'Amount',
                align: 'end',
                cell: (t) => (
                  <span className={Number(t.amount) >= 0 ? 'm3-pos' : 'm3-neg'}>
                    {signedMoney(t.amount, t.currency)}
                  </span>
                ),
              },
            ]}
          />
        </DataState>
      </Card>
    </div>
  );
}
