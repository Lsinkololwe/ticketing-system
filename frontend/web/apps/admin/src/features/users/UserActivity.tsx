'use client';

import { ErrorState, Skeleton, Timeline } from '@pml.tickets/shared/components/m3';
import { useAuditLogs } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { formatDateTime, humanize } from '@/lib/format';

/** Staff activity on one record (auditLogs filtered by resourceId). */
export function UserActivity({ resourceId, enabled = true }: { resourceId: string; enabled?: boolean }) {
  const { entries, loading, error, refetch } = useAuditLogs({ resourceId, size: 8, skip: !enabled });
  if (error && entries.length === 0) return <ErrorState error={error} onRetry={refetch} />;
  if (loading && entries.length === 0) return <Skeleton />;
  if (entries.length === 0) return <p className="m3-muted">No staff activity recorded.</p>;
  return (
    <Timeline
      label="Staff activity"
      items={entries.map((e) => ({
        id: e.id,
        title: humanize(e.action),
        time: formatDateTime(e.at),
        detail: [e.actorId ? `by ${e.actorId}` : null, e.status ? humanize(e.status) : null].filter(Boolean).join(' · '),
      }))}
    />
  );
}
