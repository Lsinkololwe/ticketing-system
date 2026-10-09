'use client';

import { useRouter } from 'next/navigation';
import { Button, Card, CardHeader, ErrorState, List, ListItem, Skeleton } from '@pml.tickets/shared/components/m3';
import { useAuditLogs } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { useStaff } from '@/components/console/StaffContext';
import { tabsFor } from '@/config/navigation';
import { humanize } from '@/lib/format';

const hhmm = (iso: string) => new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });

/** Latest audit log entries (auditLogs): the "Recent activity" card of the admin and super admin dashboards. */
export function RecentActivity({ size = 8 }: { size?: number }) {
  const router = useRouter();
  const { roles } = useStaff();
  const { entries, loading, error, refetch } = useAuditLogs({ size });
  const canOpenLog = tabsFor(roles, 'transactions').some((t) => t.id === 'audit');
  return (
    <Card>
      <CardHeader title="Recent activity" subtitle="Latest actions from the audit log" />
      {error && entries.length === 0 ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : loading && entries.length === 0 ? (
        <Skeleton />
      ) : entries.length === 0 ? (
        <p className="adm-note">No activity recorded yet.</p>
      ) : (
        <List aria-label="Recent activity">
          {entries.map((e) => (
            <ListItem
              key={e.id}
              headline={e.actorId ?? 'System'}
              support={[humanize(e.action), e.resourceType ? humanize(e.resourceType).toLowerCase() : null, e.resourceId].filter(Boolean).join(' ')}
              trailing={<span className="m3-muted">{e.at ? hhmm(e.at) : ''}</span>}
            />
          ))}
        </List>
      )}
      {canOpenLog ? (
        <Button variant="text" size="sm" onClick={() => router.push('/transactions/audit')}>
          Audit log
        </Button>
      ) : null}
    </Card>
  );
}
