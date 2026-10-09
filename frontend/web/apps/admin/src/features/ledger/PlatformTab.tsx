'use client';

import { Button, Card, CardHeader, EmptyState, ErrorState, KpiCard, KpiGrid, Skeleton } from '@pml.tickets/shared/components/m3';
import { toNumber, usePlatformAccounts } from '@pml.tickets/shared/api/admin/modules/ledger';
import { useState } from 'react';
import { useStaff } from '@/components/console';
import { needText } from '@/lib/permissions';
import { TransferDialog } from './TransferDialog';
import { formatDateTime, humanize, money } from '@/lib/format';

export function PlatformTab() {
  const { items, loading, error, refetch } = usePlatformAccounts();
  const { can } = useStaff();
  const [moving, setMoving] = useState(false);
  return (
    <div className="m3-stack">
      {loading && items.length === 0 ? (
        <KpiGrid>
          <Skeleton width="100%" />
          <Skeleton width="100%" />
          <Skeleton width="100%" />
        </KpiGrid>
      ) : error && items.length === 0 ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState title="No platform accounts yet." description="Operating, reserve and tax holding accounts appear once the platform has taken payments." />
        </Card>
      ) : (
        <KpiGrid>
          {items.map((a) => (
            <KpiCard
              key={a.id}
              label={humanize(a.accountType)}
              value={<span className="m3-mono">{money(toNumber(a.balance))}</span>}
              caption={`${a.name} · ${a.currency}${a.lastUpdatedAt ? ` · updated ${formatDateTime(a.lastUpdatedAt)}` : ''}`}
            />
          ))}
        </KpiGrid>
      )}
      <Card>
        <CardHeader title="Move funds between platform accounts" subtitle="Creates an audit entry. Balances change immediately." />
        <div className="m3-row">
          <Button variant="filled" disabled={!can('postJournal')} onClick={() => setMoving(true)}>Record transfer</Button>
          {!can('postJournal') ? <span className="m3-muted">{needText('postJournal')}</span> : null}
        </div>
      </Card>
    {moving ? <TransferDialog onClose={() => setMoving(false)} /> : null}
    </div>
  );
}
