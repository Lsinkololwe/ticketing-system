'use client';

import { useRouter } from 'next/navigation';
import { Button, Card, CardHeader, ErrorState, KeyValue, StackedBarChart, StatusPill } from '@pml.tickets/shared/components/m3';
import {
  useAdminEscrowAccounts,
  useAdminPayoutRequests,
  useChargebackStats,
  useFinancialReport,
  usePayoutRequestStats,
} from '@pml.tickets/shared';
import { useJournalEntries, useReconciliationRuns } from '@pml.tickets/shared/api/admin/modules/ledger';
import { useGatewaySettlements } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { ago, formatDate, formatNumber, humanize, money } from '@/lib/format';
import { useReportRanges, useTimedPendingCounts } from './data';
import { KpiRow, ListCard, PollNote, RoleBanner } from './parts';

export function FinanceDashboard() {
  const router = useRouter();
  const { counts, updatedAt } = useTimedPendingCounts();
  const ranges = useReportRanges();
  const today = useFinancialReport({ ...ranges.today, groupBy: 'DAY' });
  const year = useFinancialReport({ ...ranges.last12m, groupBy: 'MONTH' });
  const payoutStats = usePayoutRequestStats();
  const cb = useChargebackStats();
  const escrow = useAdminEscrowAccounts({ status: 'PAYOUT_ELIGIBLE', size: 6 });
  const payouts = useAdminPayoutRequests({ status: 'PENDING', size: 6 });

  const runs = useReconciliationRuns({}, 0, 1);
  const drafts = useJournalEntries({ status: 'DRAFT' }, 0, 1);
  const settlements = useGatewaySettlements({ size: 1 });
  const run = runs.items[0];
  const settlement = settlements.settlements[0];
  const ledgerError = runs.error ?? drafts.error ?? settlements.error;
  const ledgerReady = !(runs.loading && !runs.items.length);

  const duePayouts = escrow.pageInfo.totalCount;
  const dueRows = escrow.accounts.filter((a) => Number(a.currentBalance) > 0 && !a.lockUntil);
  const dueTotal = dueRows.reduce((s, a) => s + Number(a.currentBalance), 0);
  const points = year.data?.dataPoints ?? [];

  return (
    <>
      <RoleBanner role="FINANCE" />
      <KpiRow
        items={[
          { icon: 'money', label: 'Revenue today', value: today.data ? money(today.data.totalRevenue) : undefined, missing: 'financialReport', caption: 'Completed payments since midnight', go: { module: 'transactions', tab: 'payments' } },
          { icon: 'chart', label: 'Commission today', value: today.data ? money(today.data.totalCommissions) : undefined, missing: 'financialReport', caption: 'Earned on today’s sales' },
          {
            icon: 'wallet',
            label: 'Pending payouts',
            value: payoutStats.stats ? `${formatNumber(payoutStats.stats.pendingPayoutRequests)} · ${money(payoutStats.stats.pendingPayoutAmount)}` : undefined,
            missing: 'payoutRequestStats',
            caption: 'Awaiting a decision',
            go: { module: 'finance', tab: 'payouts' },
          },
          { icon: 'ticket', label: 'Pending refunds', value: formatNumber(counts['refund-requests']), caption: 'Awaiting a decision', go: { module: 'finance', tab: 'refunds' } },
          {
            icon: 'bank',
            label: 'Escrow due for release',
            value: escrow.loading && dueRows.length === 0 ? undefined : `${formatNumber(duePayouts)} · ${money(dueTotal)}`,
            missing: 'escrowAccounts',
            caption: 'Payout eligible now',
            go: { module: 'finance', tab: 'escrow' },
          },
          {
            icon: 'warning',
            label: 'Chargebacks open',
            value: cb.data ? formatNumber(cb.data.pendingCount + cb.data.disputedCount) : undefined,
            missing: 'chargebackStats',
            caption: 'Pending or disputed',
            go: { module: 'finance', tab: 'chargebacks' },
          },
        ]}
      />
      <div className="adm-split">
        <Card>
          <CardHeader
            title="Ledger and reconciliation status"
            subtitle="Where the books stand today"
            actions={<Button variant="text" size="sm" onClick={() => router.push('/ledger/recon')}>Open reconciliation</Button>}
          />
          {ledgerError && !run ? (
            <ErrorState error={ledgerError} onRetry={() => { runs.refetch(); drafts.refetch(); settlements.refetch(); }} />
          ) : !ledgerReady ? (
            <p className="adm-note" role="status">Loading…</p>
          ) : (
            <KeyValue
              columns
              items={[
                { label: 'Latest run', value: run ? `${run.id} · ${humanize(run.type)}` : 'No run yet' },
                { label: 'Run status', value: run ? <StatusPill status={run.status} /> : '—' },
                { label: 'Differences to resolve', value: run ? String(run.unmatchedCount ?? 0) : '—' },
                { label: 'Draft journal entries', value: String(drafts.pageInfo.totalCount) },
                { label: 'Trial balance', value: 'Checked in ledger' },
                { label: 'Last gateway settlement', value: settlement ? `${formatDate(settlement.settlementDate)} · ${money(settlement.netAmount)}` : 'None recorded' },
              ]}
            />
          )}
        </Card>
        <ListCard
          title="Escrow due for release"
          subtitle="Accounts past their hold period"
          loading={escrow.loading}
          rows={dueRows.slice(0, 6).map((a) => ({
            id: a.id,
            title: a.eventTitle ?? a.accountNumber,
            support: a.organization?.name,
            trailing: <b>{money(a.currentBalance)}</b>,
            go: { label: 'Open', href: '/finance/escrow' },
          }))}
          foot={{ label: 'All escrow accounts', href: '/finance/escrow' }}
        />
      </div>
      <div className="adm-split">
        <ListCard
          title="Payout requests to decide"
          subtitle="Newest first"
          loading={payouts.loading}
          rows={payouts.payouts.map((p) => ({
            id: p.id,
            title: p.organization?.name ?? p.requestId,
            support: `${p.eventTitle ?? 'Payout'} · ${ago(p.requestedAt)}`,
            trailing: <b>{money(p.requestedAmount)}</b>,
            go: { label: 'Review', href: '/finance/payouts' },
          }))}
          foot={{ label: 'Open payouts', href: '/finance/payouts' }}
        />
        <Card>
          <CardHeader title="Commission and costs" subtitle="Last 12 months" />
          <StackedBarChart
            title="Stacked columns of commission earned, refunds and payouts by month"
            labels={points.map((p) => p.period)}
            series={[
              { label: 'Commission', values: points.map((p) => Number(p.commissions) || 0) },
              { label: 'Refunds', values: points.map((p) => Number(p.refunds) || 0) },
              { label: 'Payouts', values: points.map((p) => Number(p.payouts) || 0) },
            ]}
            format={(n) => money(n)}
          />
        </Card>
      </div>
      <PollNote updatedAt={updatedAt} />
    </>
  );
}
