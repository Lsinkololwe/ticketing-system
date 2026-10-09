'use client';

import {
  Banner,
  Button,
  Card,
  KpiCard,
  KpiGrid,
  StatusPill,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import {
  useAcknowledgeAlert,
  useServiceHealth,
  useSystemAlerts,
  type ServiceHealthRow,
  type SystemAlertRow,
} from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { ModuleFrame } from '@/components/console/ModuleFrame';
import { ListCard } from '@/features/ledger/ListCard';
import { formatDateTime, humanize } from '@/lib/format';

const serviceLabel = (s: ServiceHealthRow['status']) => (s === 'UP' ? 'Healthy' : 'Unhealthy');

const SERVICE_COLUMNS: Array<DataColumn<ServiceHealthRow>> = [
  { id: 'service', header: 'Service', rowHeader: true, cell: (s) => <b>{humanize(s.name)}</b> },
  { id: 'status', header: 'Status', cell: (s) => <StatusPill status={s.status === 'UP' ? 'HEALTHY' : 'UNHEALTHY'}>{serviceLabel(s.status)}</StatusPill> },
  { id: 'latency', header: 'Latency', align: 'end', cell: (s) => <span className="m3-mono">{s.status === 'UP' ? `${s.latencyMillis} ms` : '—'}</span> },
  { id: 'detail', header: 'Detail', cell: (s) => (s.detail ? humanize(s.detail) : '—') },
  { id: 'checked', header: 'Last check', cell: (s) => formatDateTime(s.checkedAt) },
];

const SEVERITY_TONE = { INFO: 'INFO', WARNING: 'WARNING', CRITICAL: 'CRITICAL' } as const;

/**
 * Health: status tiles, the services table (serviceHealth, refreshed every 30 seconds while the tab is
 * visible) and the alerts table with acknowledgement (systemAlerts / acknowledgeAlert).
 */
export function HealthPage() {
  const services = useServiceHealth();
  const alerts = useSystemAlerts();
  const { acknowledge, loading: acking } = useAcknowledgeAlert();
  const snackbar = useSnackbar();

  const up = services.services.filter((s) => s.status === 'UP').length;
  const down = services.services.length - up;
  const open = alerts.alerts.filter((a) => a.status === 'OPEN').length;

  const ack = async (ids: string[], clear?: () => void) => {
    try {
      await Promise.all(ids.map((id) => acknowledge(id)));
      snackbar.show(ids.length === 1 ? 'Alert acknowledged' : `${ids.length} alerts acknowledged`);
      clear?.();
    } catch (e) {
      snackbar.show((e as Error).message || 'Could not acknowledge the alert');
    }
  };

  const alertCols: Array<DataColumn<SystemAlertRow>> = [
    {
      id: 'alert',
      header: 'Alert',
      rowHeader: true,
      cell: (a) => (
        <>
          <b>{a.title}</b>
          <br />
          <span className="m3-muted">{humanize(a.source)}{a.occurrences > 1 ? ` · seen ${a.occurrences} times` : ''}</span>
        </>
      ),
    },
    { id: 'severity', header: 'Severity', cell: (a) => <StatusPill status={SEVERITY_TONE[a.severity]} /> },
    { id: 'raised', header: 'Raised', cell: (a) => formatDateTime(a.raisedAt) },
    { id: 'state', header: 'State', cell: (a) => <StatusPill status={a.status === 'OPEN' ? 'OPEN' : a.status === 'ACKNOWLEDGED' ? 'ACKNOWLEDGED' : 'RESOLVED'}>{a.status === 'OPEN' ? 'Needs acknowledgement' : humanize(a.status)}</StatusPill> },
  ];

  return (
    <ModuleFrame module="health" title="Health" subtitle="Service status and alerts">
      <div className="adm-stack">
        <Card aria-label="Status summary">
          <Card>
          <KpiGrid flat>
            <KpiCard label="Healthy" value={services.loading && !services.services.length ? '…' : up} />
            <KpiCard label="Unhealthy" value={services.loading && !services.services.length ? '…' : down} />
            <KpiCard label="Alerts to acknowledge" value={alerts.loading && !alerts.alerts.length ? '…' : open} />
          </KpiGrid>
          </Card>
        </Card>

        <ListCard<ServiceHealthRow>
          title="Services"
          subtitle="Checked every 30 seconds."
          caption="Services"
          rows={services.services}
          columns={SERVICE_COLUMNS}
          getRowId={(s) => s.name}
          searchLabel="Search services"
          searchText={(s) => s.name}
          filters={[
            {
              id: 'status',
              label: 'Status',
              options: [{ value: 'UP', label: 'Healthy' }, { value: 'DOWN', label: 'Unhealthy' }],
              match: (s, v) => s.status === v,
            },
          ]}
          loading={services.loading}
          error={services.error}
          onRetry={services.refetch}
          empty={{ title: 'No services reported.' }}
        />

        <ListCard<SystemAlertRow>
          title="Alerts"
          subtitle="Acknowledge an alert once someone is looking at it."
          caption="Alerts"
          rows={alerts.alerts}
          columns={alertCols}
          getRowId={(a) => a.id}
          searchLabel="Search alerts"
          searchText={(a) => `${a.title} ${a.source}`}
          filters={[
            {
              id: 'severity',
              label: 'Severity',
              options: [{ value: 'INFO', label: 'Info' }, { value: 'WARNING', label: 'Warning' }, { value: 'CRITICAL', label: 'Critical' }],
              match: (a, v) => a.severity === v,
            },
            {
              id: 'state',
              label: 'State',
              options: [{ value: 'open', label: 'Needs acknowledgement' }, { value: 'done', label: 'Acknowledged or resolved' }],
              match: (a, v) => (v === 'open' ? a.status === 'OPEN' : a.status !== 'OPEN'),
            },
          ]}
          selectable
          bulk={(ids, clear) => (
            <Button variant="tonal" size="sm" loading={acking} onClick={() => void ack(ids.filter((id) => alerts.alerts.find((a) => a.id === id)?.status === 'OPEN'), clear)}>
              Acknowledge selected
            </Button>
          )}
          rowActions={(a) =>
            a.status === 'OPEN' ? (
              <Button variant="text" size="sm" loading={acking} onClick={() => void ack([a.id])}>
                Acknowledge
              </Button>
            ) : null
          }
          loading={alerts.loading}
          error={alerts.error}
          onRetry={alerts.refetch}
          empty={{ title: 'No alerts. All quiet.' }}
        />

        <Banner tone="info">
          Traces, metrics and detailed dashboards live in Grafana. Open it from your organisation&apos;s monitoring portal. This page refreshes every 30 seconds while it is open.
        </Banner>
      </div>
    </ModuleFrame>
  );
}
