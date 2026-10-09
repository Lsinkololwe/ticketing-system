'use client';

import { ErrorState, KeyValue, Skeleton, StatusPill } from '@pml.tickets/shared/components/m3';
import { useChargebackList } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { useCommissionRecords } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { formatDateTime, money } from '@/lib/format';

/** Chargebacks raised against one event (chargebacks filtered by eventId). */
export function EventChargebacks({ eventId }: { eventId: string }) {
  const { chargebacks, loading, error, refetch } = useChargebackList({ eventId, size: 10 });
  if (error && chargebacks.length === 0) return <ErrorState error={error} onRetry={refetch} />;
  if (loading && chargebacks.length === 0) return <Skeleton />;
  if (chargebacks.length === 0) return <p className="m3-muted">No chargebacks for this event.</p>;
  return (
    <ul aria-label="Chargebacks for this event">
      {chargebacks.map((c) => (
        <li key={c.id}>
          {c.chargebackId} · {money(Number(c.chargebackAmount))} · <StatusPill status={c.status} /> · respond by {formatDateTime(c.responseDeadline)}
        </li>
      ))}
    </ul>
  );
}

/** Commission for one event by state (commissionRecords totals). */
export function EventCommission({ eventId }: { eventId: string }) {
  const { totals, loading, error, refetch } = useCommissionRecords({ eventId, size: 1 });
  if (error && !totals) return <ErrorState error={error} onRetry={refetch} />;
  if (!totals) return loading ? <Skeleton /> : <p className="m3-muted">No commission recorded yet.</p>;
  return (
    <KeyValue
      columns
      items={[
        { label: 'Commission earned', value: money(Number(totals.earned)) },
        { label: 'Pending until the event completes', value: money(Number(totals.pending)) },
        { label: 'Clawed back', value: money(Number(totals.clawedBack)) },
        { label: 'Cancelled', value: money(Number(totals.cancelled)) },
      ]}
    />
  );
}
