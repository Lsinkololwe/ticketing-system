'use client';

import { useRouter } from 'next/navigation';
import { BarChart, Button, Card, CardHeader, DataTable, ErrorState, KeyValue, StatusPill } from '@pml.tickets/shared/components/m3';
import { useFinancialReport, usePlatformConfiguration } from '@pml.tickets/shared';
import { useServiceHealth, useStaffAccounts, useSystemAlerts } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { RecentActivity } from './RecentActivity';
import { ago, formatDateTime, humanize, money } from '@/lib/format';
import { Tiles } from '@/components/console/Tiles';
import { hrefOf, pendingFor } from '@/lib/pending';
import { useStaff } from '@/components/console/StaffContext';
import { chartPoints, trendOf, useReportRanges, useTimedPendingCounts } from './data';
import { KpiRow, PollNote, RoleBanner } from './parts';

export function SuperDashboard() {
  const router = useRouter();
  const { roles } = useStaff();
  const { counts, updatedAt } = useTimedPendingCounts();
  const ranges = useReportRanges();
  const cur = useFinancialReport({ ...ranges.last30, groupBy: 'MONTH' });
  const prior = useFinancialReport({ ...ranges.prior30, groupBy: 'MONTH' });
  const year = useFinancialReport({ ...ranges.last12m, groupBy: 'MONTH' });
  const { config } = usePlatformConfiguration();
  const health = useServiceHealth();
  const alerts = useSystemAlerts({ status: 'OPEN' });
  const staffList = useStaffAccounts({ size: 50 });
  const pend = pendingFor(roles, counts);
  const total = pend.reduce((a, x) => a + x.count, 0);

  return (
    <>
      <RoleBanner role="SUPER_ADMIN" />
      <KpiRow
        items={[
          { icon: 'server', label: 'Services healthy', value: health.error && !health.services.length ? undefined : health.loading && !health.services.length ? '…' : `${health.services.filter((x) => x.status === 'UP').length} of ${health.services.length}`, caption: `${health.services.filter((x) => x.status !== 'UP').length} need a look`, missing: 'serviceHealth', go: { module: 'health' } },
          { icon: 'warning', label: 'Alerts to acknowledge', value: alerts.error && !alerts.alerts.length ? undefined : alerts.loading && !alerts.alerts.length ? '…' : String(alerts.alerts.length), caption: `${alerts.alerts.filter((a) => a.severity === 'CRITICAL').length} critical`, missing: 'systemAlerts', go: { module: 'health' } },
          {
            icon: 'settings',
            label: 'Platform rules',
            value: config ? `Version ${config.version}` : undefined,
            missing: 'platformConfiguration',
            caption: config ? `Saved by ${config.updatedBy || 'the system'}` : undefined,
            go: { module: 'config', tab: 'rules' },
          },
          { icon: 'lock', label: 'Staff with two-step', value: staffList.error && !staffList.staff.length ? undefined : staffList.loading && !staffList.staff.length ? '…' : `${staffList.staff.filter((x) => x.twoFactorEnabled).length} of ${staffList.staff.length}`, caption: staffList.staff.some((x) => !x.twoFactorEnabled) ? `${staffList.staff.filter((x) => !x.twoFactorEnabled).length} without MFA` : 'Everyone is protected', missing: 'staffAccounts', go: { module: 'config', tab: 'roles' } },
          { icon: 'check-circle', label: 'Pending work', value: String(total), caption: `${pend.length} queues`, go: { module: 'approvals', tab: 'orgs' } },
          {
            icon: 'money',
            label: 'GMV, last 30 days',
            value: cur.data ? money(cur.data.totalRevenue) : undefined,
            missing: 'financialReport',
            trend: trendOf(cur.data?.totalRevenue, prior.data?.totalRevenue),
            caption: 'vs prior 30 days',
          },
        ]}
      />
      <div className="adm-split">
        <Card>
          <CardHeader
            title="System health"
            subtitle="Checked every 30 seconds"
            actions={<Button variant="text" size="sm" onClick={() => router.push('/health')}>Open health</Button>}
          />
          {health.error && !health.services.length ? (
            <ErrorState error={health.error} onRetry={health.refetch} />
          ) : health.services.length === 0 ? (
            <p className="adm-note">{health.loading ? 'Loading…' : 'No services reported.'}</p>
          ) : (
            <ul className="adm-statusrows" aria-label="Service health">
              {health.services.map((x) => (
                <li key={x.name}>
                  <b>{x.name}</b>
                  <StatusPill status={x.status === 'UP' ? 'HEALTHY' : 'UNHEALTHY'}>{x.status === 'UP' ? `Healthy · ${x.latencyMillis} ms` : 'Unhealthy'}</StatusPill>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <CardHeader title="Platform configuration status" subtitle="What organizers and buyers are following today" />
          <KeyValue
            columns
            items={[
              { label: 'Default commission', value: config?.commissionDefault != null ? `${config.commissionDefault}%` : '—' },
              { label: 'Minimum payout', value: config?.minimumPayout != null ? money(config.minimumPayout) : '—' },
              { label: 'Approval target', value: config ? `${config.approvalSlaHours} hours` : '—' },
              { label: 'Reservation hold', value: config?.reservationHoldMinutes != null ? `${config.reservationHoldMinutes} minutes` : '—' },
              { label: 'Escrow hold', value: config?.escrowHoldDays != null ? `${config.escrowHoldDays} days` : '—' },
              { label: 'Last saved', value: config ? `${formatDateTime(config.updatedAt)} by ${config.updatedBy || 'the system'}` : '—' },
            ]}
          />
          <div className="m3-card__foot">
            <Button variant="tonal" size="sm" onClick={() => router.push('/config/rules')}>Open platform configuration</Button>
            <Button variant="text" size="sm" onClick={() => router.push('/config/roles')}>Roles and access</Button>
          </div>
        </Card>
      </div>
      <div className="adm-split">
        <Card>
          <CardHeader title="Staff and two-step verification" subtitle="Platform staff accounts" />
          {staffList.error && !staffList.staff.length ? (
            <ErrorState error={staffList.error} onRetry={staffList.refetch} />
          ) : staffList.staff.length === 0 ? (
            <p className="adm-note">{staffList.loading ? 'Loading…' : 'No staff accounts.'}</p>
          ) : (
            <DataTable
              caption="Staff accounts"
              rows={staffList.staff.slice(0, 7)}
              getRowId={(x) => x.id}
              columns={[
                { id: 'name', header: 'Staff member', rowHeader: true, cell: (x) => <b>{x.fullName}</b> },
                { id: 'role', header: 'Role', cell: (x) => (x.roles ?? []).map((r) => <StatusPill key={r}>{humanize(r)}</StatusPill>) },
                { id: 'mfa', header: 'Two-step', cell: (x) => <StatusPill tone={x.twoFactorEnabled ? 'success' : 'warning'}>{x.twoFactorEnabled ? 'On' : 'Off'}</StatusPill> },
                { id: 'last', header: 'Last sign-in', cell: (x) => <span className="m3-muted">{x.lastLoginAt ? ago(x.lastLoginAt) : '—'}</span> },
              ]}
            />
          )}
        </Card>
        <Card>
          <CardHeader title="Pending work" subtitle="Everything waiting for a person" />
          {pend.length === 0 ? (
            <p className="adm-note">Nothing needs attention here.</p>
          ) : (
            <Tiles
              label="Pending queues"
              items={pend.map((x) => ({ id: `${x.module}-${x.tab}`, label: x.label, value: x.count, onSelect: () => router.push(hrefOf(x.module, x.tab)) }))}
            />
          )}
        </Card>
      </div>
      <div className="adm-split">
        <Card>
          <CardHeader title="Revenue by month" subtitle="Monthly gross ticket sales, last 12 months" />
          {year.error && !year.data ? (
            <ErrorState error={year.error} onRetry={year.refetch} />
          ) : (
            <BarChart
              title="Monthly gross ticket sales over twelve months"
              data={chartPoints(year.data?.dataPoints)}
              format={(n) => money(n)}
              seriesLabel="Revenue"
            />
          )}
        </Card>
        <RecentActivity />
      </div>
      <PollNote updatedAt={updatedAt} />
    </>
  );
}
