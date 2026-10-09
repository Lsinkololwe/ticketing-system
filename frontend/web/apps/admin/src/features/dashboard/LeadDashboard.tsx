'use client';

import { Card, CardHeader, StatusPill } from '@pml.tickets/shared/components/m3';
import { useAdminRefundRequests, usePayoutRecoverySummary, useRecoveryQueue } from '@pml.tickets/shared';
import { useChargebackQueue } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { useDualControlQueue, useStuckTransactions } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { useStaff } from '@/components/console/StaffContext';
import { DualControlList } from '@/features/transactions/DualControlList';
import { ageHours, ago, formatDateTime, formatNumber, money } from '@/lib/format';
import { useTimedPendingCounts } from './data';
import { KpiRow, ListCard, PollNote, RoleBanner } from './parts';

const TWO_DAYS = 48;
const FIVE_DAYS = 120;

export function LeadDashboard() {
  const { updatedAt } = useTimedPendingCounts();
  const refunds = useAdminRefundRequests({ status: 'PENDING', size: 100 });
  const recovery = usePayoutRecoverySummary();
  const stuck = useRecoveryQueue('stuck', { size: 4 });
  const chargebacks = useChargebackQueue();
  const dual = useDualControlQueue();
  const stuckTx = useStuckTransactions({ size: 1 });
  const { id: staffId } = useStaff();
  const nearDeadline = chargebacks.open
    .filter((c) => new Date(c.responseDeadline).getTime() - Date.now() < 48 * 3_600_000)
    .sort((a, b) => a.responseDeadline.localeCompare(b.responseDeadline));
  const awaiting = dual.proposals.filter((p) => p.status === 'PENDING' && p.proposedById !== staffId);

  const waits = refunds.refunds.map((r) => ({ r, h: ageHours(r.requestedAt) }));
  const r5 = waits.filter((w) => w.h >= FIVE_DAYS);
  const r2 = waits.filter((w) => w.h >= TWO_DAYS && w.h < FIVE_DAYS);
  const known = !refunds.loading || refunds.refunds.length > 0;

  return (
    <>
      <RoleBanner role="FINANCE_LEAD" />
      <KpiRow
        items={[
          { icon: 'clock', label: 'Refunds waiting 2 days', value: known ? String(r2.length) : undefined, missing: 'refundRequests', caption: 'Escalated once', go: { module: 'finance', tab: 'refunds' } },
          { icon: 'warning', label: 'Refunds waiting 5 days', value: known ? String(r5.length) : undefined, missing: 'refundRequests', caption: 'Escalated again', go: { module: 'finance', tab: 'refunds' } },
          { icon: 'lock', label: 'Chargebacks near deadline', value: chargebacks.loaded ? String(nearDeadline.length) : undefined, missing: 'pendingChargebacks', caption: 'Due within 48 hours', go: { module: 'finance', tab: 'chargebacks' } },
          { icon: 'check-circle', label: 'Waiting for a second approver', value: dual.error && !dual.proposals.length ? undefined : String(awaiting.length), missing: 'dualControlQueue', caption: 'Dual control', go: { module: 'finance', tab: 'refunds' } },
          {
            icon: 'money',
            label: 'Stuck payouts',
            value: recovery.summary ? formatNumber(recovery.summary.stuckPayoutsCount) : undefined,
            missing: 'payoutRecoverySummary',
            caption: 'Failed, held or slow',
            go: { module: 'finance', tab: 'payouts' },
          },
          { icon: 'swap', label: 'Stuck transactions', value: stuckTx.error ? undefined : stuckTx.loading && !stuckTx.items.length ? '…' : String(stuckTx.pageInfo.totalCount), missing: 'stuckTransactions', caption: 'Older than 30 minutes', go: { module: 'transactions', tab: 'recovery' } },
        ]}
      />
      <div className="adm-split">
        <ListCard
          title="Escalations"
          subtitle="Refunds that have waited too long"
          loading={refunds.loading}
          rows={[...r5, ...r2].slice(0, 7).map(({ r, h }) => ({
            id: r.id,
            title: `${r.ticketNumber ?? r.requestId} · ${money(r.refundAmount)}`,
            support: `Waiting ${ago(r.requestedAt).replace(' ago', '')}`,
            trailing: <StatusPill tone={h >= FIVE_DAYS ? 'error' : 'warning'}>{h >= FIVE_DAYS ? '5 days' : '2 days'}</StatusPill>,
            go: { label: 'Open', href: '/finance/refunds' },
          }))}
          foot={{ label: 'Open refunds', href: '/finance/refunds' }}
        />
        <ListCard
          title="Chargebacks near their deadline"
          subtitle="Respond before the provider deadline"
          loading={!chargebacks.loaded}
          rows={nearDeadline.slice(0, 6).map((c) => ({
            id: c.id,
            title: `Chargeback ${money(c.chargebackAmount)}`,
            support: `Respond by ${formatDateTime(c.responseDeadline)}`,
            trailing: <StatusPill status={c.status} />,
            go: { label: 'Open', href: '/finance/chargebacks' },
          }))}
          foot={{ label: 'Open chargebacks', href: '/finance/chargebacks' }}
        />
      </div>
      <div className="adm-split">
        <Card>
          <CardHeader title="Dual-control approvals" subtitle="Created by someone else, waiting for you as the second approver" />
          <DualControlList proposals={awaiting} loading={dual.loading} error={dual.error} onRetry={dual.refetch} />
        </Card>
        <ListCard
          title="Stuck payouts and transactions"
          subtitle="Failed, held or waiting longer than expected"
          loading={stuck.loading}
          rows={stuck.items.map((p) => ({
            id: p.id,
            title: `Payout ${p.requestId} · ${money(p.requestedAmount)}`,
            support: p.organizerName ?? undefined,
            trailing: <StatusPill status={p.status} />,
            go: { label: 'Open', href: '/finance/payouts' },
          }))}
          foot={{ label: 'Transaction recovery', href: '/transactions/recovery' }}
        />
      </div>
      <PollNote updatedAt={updatedAt} />
    </>
  );
}
