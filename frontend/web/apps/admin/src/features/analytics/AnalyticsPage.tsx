'use client';

import { useMemo, useState } from 'react';
import {
  BarChart,
  Button,
  Card,
  CardHeader,
  DataTable,
  DonutChart,
  ErrorState,
  HorizontalBars,
  Tabs,
  Skeleton,
  StackedBarChart,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import {
  useChargebackStats,
  useEventStats,
  useExportFinancialReport,
  useFinancialReport,
  usePayoutRequestStats,
  useTicketStats,
  useUserStats,
  type AdminExportFormat,
  type AdminFinancialReport,
} from '@pml.tickets/shared';
import { KpiRow } from '@/features/dashboard/parts';
import { usePaymentRiskSummary } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { NewAccounts, PurchaseHeat } from './AnalyticsGrowth';
import { ModuleFrame } from '@/components/console/ModuleFrame';
import type { ReportGroupBy } from '@pml.tickets/shared/api/admin/modules/reports';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { csvText, formatNumber, humanize, money } from '@/lib/format';

/** What a report period carries (platform `REPORT_PERIOD` rows): how many days back, and how to bucket them. */
export interface ReportPeriodMeta {
  days: number;
  bucket: ReportGroupBy;
}

/** The window shown until the platform's periods arrive: loading only, never a list to pick from. */
const LOADING_WINDOW: ReportPeriodMeta = { days: 30, bucket: 'DAY' };

const EXPORTS: AdminExportFormat[] = ['CSV', 'PDF', 'EXCEL', 'JSON'];

export function summaryRows(period: string, r: AdminFinancialReport) {
  return [
    ['Period', period],
    ['Gross ticket sales', r.totalRevenue],
    ['Refunds', r.totalRefunds],
    ['Commission', r.totalCommissions],
    ['Payouts', r.totalPayouts],
    ['Pending payouts', r.pendingPayouts],
    ['Escrow balance', r.escrowBalance],
    ['Net platform revenue', r.netPlatformRevenue],
  ] as Array<[string, string | number]>;
}

export function reportLines(r: AdminFinancialReport | null) {
  if (!r) return [];
  const lines: Array<[string, number | string]> = [
    ['Gross ticket sales', r.totalRevenue],
    ['Refunds', r.totalRefunds],
    ['Commission earned', r.totalCommissions],
    ['Payouts', r.totalPayouts],
    ['Pending payouts', r.pendingPayouts],
    ['Escrow balance', r.escrowBalance],
    ['Net platform revenue', r.netPlatformRevenue],
  ];
  return lines.map(([line, amount], i) => ({ line, amount, last: i === lines.length - 1 }));
}

export function AnalyticsPage() {
  const snack = useSnackbar();
  const periods = useReferenceOptions<ReportPeriodMeta>('REPORT_PERIOD');
  const [chosen, setChosen] = useState<string | null>(null);
  // The first period the platform lists is the default; an administrator reorders the list to change it.
  const current = periods.byCode.get(chosen ?? '') ?? periods.options[0];
  const window_ = current?.metadata ?? LOADING_WINDOW;
  const range = useMemo(() => {
    const end = new Date();
    return { startDate: new Date(end.getTime() - window_.days * 864e5).toISOString(), endDate: end.toISOString() };
  }, [window_.days]);
  const year = useMemo(() => {
    const end = new Date();
    return { startDate: new Date(end.getFullYear(), end.getMonth() - 11, 1).toISOString(), endDate: end.toISOString() };
  }, []);

  const report = useFinancialReport({ ...range, groupBy: window_.bucket });
  const yearReport = useFinancialReport({ ...year, groupBy: 'MONTH' });
  const users = useUserStats();
  const events = useEventStats();
  const tickets = useTicketStats();
  const payouts = usePayoutRequestStats();
  const cb = useChargebackStats();
  const exporter = useExportFinancialReport();
  const risk = usePaymentRiskSummary(24);

  const soldInPeriod = (report.data?.dataPoints ?? []).reduce((s, p) => s + p.ticketsSold, 0);
  const closed = cb.data ? cb.data.wonCount + cb.data.lostCount : 0;
  const ps = payouts.stats;
  const yearPoints = yearReport.data?.dataPoints ?? [];
  const label = current?.label ?? '';

  const copy = async (format: AdminExportFormat) => {
    if (format === 'PDF' || format === 'EXCEL') {
      try {
        const res = await exporter.exportReport(range, format);
        if (res.errorMessage || !res.downloadUrl) throw new Error(res.errorMessage ?? 'No file was produced');
        window.open(res.downloadUrl, '_blank', 'noopener,noreferrer');
        snack.show({ message: `${humanize(format)} summary ready: ${res.fileName ?? 'download started'}` });
      } catch {
        snack.show({ message: `Could not produce the ${humanize(format)} summary. Try again.` });
      }
      return;
    }
    if (!report.data) {
      snack.show({ message: 'The report is still loading. Try again in a moment.' });
      return;
    }
    const rows = summaryRows(label, report.data);
    const text = format === 'JSON' ? JSON.stringify(Object.fromEntries(rows), null, 2) : csvText([['Metric', 'Value'], ...rows]);
    try {
      await navigator.clipboard.writeText(text);
      snack.show({ message: `${humanize(format)} summary copied. The text is ready to paste.` });
    } catch {
      snack.show({ message: 'Could not copy to the clipboard.' });
    }
  };

  return (
    <ModuleFrame module="analytics" title="Analytics and statistics" subtitle="Platform statistics and the financial report">
      <div className="adm-stack">
        <div className="m3-row">
          {periods.options.length > 0 ? (
            <Tabs
              label="Period"
              variant="seg"
              value={current?.value ?? ''}
              onChange={(k) => setChosen(k)}
              tabs={periods.options.map((o) => ({ id: o.value, label: o.label }))}
            />
          ) : (
            <span className="m3-muted" role="status">
              {periods.loading ? 'Loading periods…' : 'Report periods are not available.'}
            </span>
          )}
          <span style={{ flex: 1 }} />
          <span>Export summary:</span>
          {EXPORTS.map((x) => (
            <Button key={x} variant="tonal" size="sm" loading={exporter.loading && (x === 'PDF' || x === 'EXCEL')} onClick={() => void copy(x)}>
              {x === 'EXCEL' ? 'Excel' : x}
            </Button>
          ))}
        </div>

        <KpiRow
          items={[
            {
              icon: 'users',
              label: 'User statistics',
              value: users.data ? `${formatNumber(users.data.totalUsers)} accounts` : undefined,
              missing: 'userStats',
              caption: users.data ? `${formatNumber(users.data.activeUsers)} active · ${formatNumber(users.data.lockedUsers + users.data.suspendedUsers)} locked or suspended` : undefined,
            },
            {
              icon: 'calendar',
              label: 'Event statistics',
              value: events.stats ? `${formatNumber(events.stats.totalEvents)} events` : undefined,
              missing: 'eventStats',
              caption: events.stats ? `${events.stats.publishedEvents} live · ${events.stats.pendingApprovalEvents} awaiting approval` : undefined,
            },
            {
              icon: 'ticket',
              label: 'Ticket statistics',
              value: report.data ? `${formatNumber(soldInPeriod)} sold` : undefined,
              missing: 'financialReport',
              caption: label.toLowerCase(),
            },
            {
              icon: 'money',
              label: 'Payout statistics',
              value: report.data ? `${money(report.data.totalPayouts)} paid` : undefined,
              missing: 'financialReport',
              caption: ps ? `${ps.pendingPayoutRequests + ps.approvedPayoutRequests + ps.processingPayoutRequests} in progress` : undefined,
            },
            {
              icon: 'warning',
              label: 'Chargeback statistics',
              value: cb.data ? `${cb.data.wonCount} of ${closed} won` : undefined,
              missing: 'chargebackStats',
              caption: cb.data ? `${cb.data.pendingCount + cb.data.disputedCount} open` : undefined,
            },
            { icon: 'lock', label: 'Payment risk', value: risk.summary ? `${formatNumber(risk.summary.high)} high risk` : undefined, missing: 'paymentRiskSummary', caption: risk.summary ? `${formatNumber(risk.summary.medium)} medium · ${formatNumber(risk.summary.evaluated)} attempts` : undefined },
          ]}
        />

        <div className="adm-split">
          <Card>
            <CardHeader title="Revenue by month" subtitle="Monthly gross ticket sales, last 12 months" />
            {yearReport.error && !yearReport.data ? (
              <ErrorState error={yearReport.error} onRetry={yearReport.refetch} />
            ) : (
              <BarChart
                title="Monthly gross ticket sales over twelve months"
                data={yearPoints.map((p) => ({ label: p.period, value: Number(p.revenue) || 0 }))}
                format={(n) => money(n)}
                seriesLabel="Revenue"
              />
            )}
          </Card>
          <Card>
            <CardHeader title="Tickets by status" subtitle="All tickets issued on the platform" />
            {tickets.data?.ticketsByStatus?.length ? (
              <DonutChart
                title="Donut chart of tickets by status"
                data={tickets.data.ticketsByStatus.map((s) => ({ label: humanize(s.status), value: s.count }))}
                centre={formatNumber(tickets.data.totalTickets)}
                format={(n) => formatNumber(n)}
              />
            ) : tickets.error ? (
              <ErrorState error={tickets.error} onRetry={tickets.refetch} />
            ) : tickets.loading ? (
              <Skeleton width="100%" />
            ) : (
              <p>No tickets have been issued yet.</p>
            )}
          </Card>
        </div>

        <div className="adm-split">
          <Card>
            <CardHeader title="When people buy" subtitle="Purchases by day and time of day" />
            <PurchaseHeat from={range.startDate} to={range.endDate} />
          </Card>
          <Card>
            <CardHeader title="Events by status" />
            {events.stats ? (
              <HorizontalBars
                title="Events by status"
                data={[
                  ['Published', events.stats.publishedEvents],
                  ['Awaiting approval', events.stats.pendingApprovalEvents],
                  ['Approved, not published', events.stats.approvedNotPublishedEvents],
                  ['Draft', events.stats.draftEvents],
                  ['Completed', events.stats.completedEvents],
                  ['Cancelled', events.stats.cancelledEvents],
                  ['Rejected', events.stats.rejectedEvents],
                ]
                  .filter(([, v]) => (v as number) > 0)
                  .map(([l, v]) => ({ label: l as string, value: v as number }))}
              />
            ) : events.error ? (
              <ErrorState error={events.error} onRetry={events.refetch} />
            ) : (
              <Skeleton width="100%" />
            )}
          </Card>
        </div>

        <div className="adm-split">
          <Card>
            <CardHeader title="Payout requests by status" subtitle="Number of requests" />
            {ps ? (
              <HorizontalBars
                title="Payout requests by status"
                data={[
                  ['Pending', ps.pendingPayoutRequests],
                  ['Approved', ps.approvedPayoutRequests],
                  ['Processing', ps.processingPayoutRequests],
                  ['Completed', ps.completedPayoutRequests],
                  ['Failed', ps.failedPayoutRequests],
                ]
                  .filter(([, v]) => (v as number) > 0)
                  .map(([l, v]) => ({ label: l as string, value: v as number }))}
              />
            ) : payouts.error ? (
              <ErrorState error={payouts.error} onRetry={payouts.refetch} />
            ) : (
              <Skeleton width="100%" />
            )}
          </Card>
          <Card>
            <CardHeader title="Chargebacks by status" />
            {cb.data ? (
              <HorizontalBars
                title="Chargebacks by status"
                data={[
                  ['Pending', cb.data.pendingCount],
                  ['Disputed', cb.data.disputedCount],
                  ['Won', cb.data.wonCount],
                  ['Lost', cb.data.lostCount],
                ]
                  .filter(([, v]) => (v as number) > 0)
                  .map(([l, v]) => ({ label: l as string, value: v as number }))}
              />
            ) : cb.error ? (
              <ErrorState error={cb.error} onRetry={cb.refetch} />
            ) : (
              <Skeleton width="100%" />
            )}
          </Card>
        </div>

        <div className="adm-split">
          <Card>
            <CardHeader title="Commission, refunds and payouts" subtitle="Last 12 months" />
            <StackedBarChart
              title="Stacked columns of commission earned, refunds and payouts by month"
              labels={yearPoints.map((p) => p.period)}
              series={[
                { label: 'Commission', values: yearPoints.map((p) => Number(p.commissions) || 0) },
                { label: 'Refunds', values: yearPoints.map((p) => Number(p.refunds) || 0) },
                { label: 'Payouts', values: yearPoints.map((p) => Number(p.payouts) || 0) },
              ]}
              format={(n) => money(n)}
            />
          </Card>
          <Card>
            <CardHeader title="New accounts by type" subtitle="Last 12 months" />
            <NewAccounts from={year.startDate} to={year.endDate} />
          </Card>
        </div>

        <Card>
          <CardHeader title="Financial report" subtitle={label} />
          <DataTable
            caption={`Financial report for ${label.toLowerCase()}`}
            getRowId={(r) => r.line}
            loading={report.loading && !report.data}
            error={report.error && !report.data ? <ErrorState error={report.error} onRetry={report.refetch} /> : undefined}
            empty="No figures for this period."
            columns={[
              { id: 'line', header: 'Line', rowHeader: true, cell: (r) => (r.last ? <b>{r.line}</b> : r.line) },
              { id: 'amount', header: 'Amount', align: 'end', cell: (r) => (r.last ? <b>{money(r.amount)}</b> : money(r.amount)) },
            ]}
            rows={reportLines(report.data)}
          />
        </Card>
      </div>
    </ModuleFrame>
  );
}
