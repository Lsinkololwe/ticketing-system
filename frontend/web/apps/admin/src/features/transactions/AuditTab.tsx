'use client';

import { useState } from 'react';
import { Button, Card, CardHeader, DataTable, Dialog, EmptyState, ErrorState, KeyValue, StatusPill, TextField, useSnackbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import { useAuditLogs, type AuditLogRow } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { csvText, formatDateTime, humanize } from '@/lib/format';

const COLUMNS: Array<DataColumn<AuditLogRow>> = [
  { id: 'at', header: 'When', cell: (e) => formatDateTime(e.at) },
  { id: 'action', header: 'Action', rowHeader: true, cell: (e) => humanize(e.action) },
  { id: 'resource', header: 'Record', cell: (e) => <>{e.resourceType ? humanize(e.resourceType) : '—'}<br /><span className="m3-mono m3-muted">{e.resourceId ?? ''}</span></> },
  { id: 'actor', header: 'By', cell: (e) => <span className="m3-mono">{e.actorId ?? 'System'}</span> },
  { id: 'status', header: 'Result', cell: (e) => (e.status ? humanize(e.status) : '—') },
  { id: 'source', header: 'Source', cell: (e) => humanize(e.source) },
];

/** Read only audit trail (auditLogs): server filtered by action, record type and record id, paged. */
export function AuditTab() {
  const [action, setAction] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [resourceId, setResourceId] = useState('');
  const [page, setPage] = useState(0);
  const snackbar = useSnackbar();
  const [detail, setDetail] = useState<AuditLogRow | null>(null);
  const { entries, pageInfo, loading, error, refetch } = useAuditLogs({ action, resourceType, resourceId, page, size: 20 });

  const copy = async () => {
    const text = csvText([['When', 'Action', 'Record type', 'Record', 'By', 'Result'], ...entries.map((e) => [e.at, e.action, e.resourceType, e.resourceId, e.actorId, e.status])]);
    try {
      await navigator.clipboard.writeText(text);
      snackbar.show('Audit log page copied');
    } catch {
      snackbar.show('Could not copy to the clipboard');
    }
  };

  return (
    <Card>
      <CardHeader
        title="Audit log"
        subtitle="Read only and immutable. Entries are kept for 7 years."
        actions={<Button variant="tonal" size="sm" icon="copy" disabled={entries.length === 0} onClick={() => void copy()}>Copy CSV</Button>}
      />
      <div className="m3-toolbar adm-filterbar" role="search" aria-label="Audit log filters">
        <TextField label="Action" density="compact" value={action} onChange={(e) => { setAction(e.target.value.toUpperCase()); setPage(0); }} />
        <TextField label="Record type" density="compact" value={resourceType} onChange={(e) => { setResourceType(e.target.value); setPage(0); }} />
        <TextField label="Record id" density="compact" value={resourceId} onChange={(e) => { setResourceId(e.target.value); setPage(0); }} />
      </div>
      <DataTable<AuditLogRow>
        caption="Audit log"
        columns={COLUMNS}
        rows={entries}
        getRowId={(e) => e.id}
        rowActions={(e) => <Button variant="tonal" size="sm" onClick={() => setDetail(e)}>Details</Button>}
        loading={loading && entries.length === 0}
        error={error && entries.length === 0 ? <ErrorState error={error} onRetry={refetch} /> : undefined}
        empty={<EmptyState title="No audit entries match." />}
        pagination={pageInfo.totalCount > pageInfo.pageSize ? { page: page + 1, pageSize: pageInfo.pageSize, total: pageInfo.totalCount, onPageChange: (p) => setPage(p - 1), label: 'Audit log pages' } : undefined}
      />
      <Dialog
        open={detail !== null}
        onClose={() => setDetail(null)}
        title={detail ? `Audit entry ${detail.id}` : ''}
        actions={<Button variant="filled" onClick={() => setDetail(null)}>Close</Button>}
      >
        {detail ? (
          <div className="m3-stack">
            {detail.status ? <StatusPill status={detail.status.toUpperCase()} /> : null}
            <KeyValue
              items={[
                { label: 'Time', value: formatDateTime(detail.at) },
                { label: 'Action', value: humanize(detail.action) },
                { label: 'Record', value: `${detail.resourceType ? humanize(detail.resourceType) : '—'} ${detail.resourceId ?? ''}` },
                { label: 'By', value: <span className="m3-mono">{detail.actorId ?? 'System'}</span> },
                { label: 'Source', value: humanize(detail.source) },
              ]}
            />
            <pre className="m3-mono">{JSON.stringify(detail.metadata ?? {}, null, 2)}</pre>
          </div>
        ) : null}
      </Dialog>
    </Card>
  );
}
