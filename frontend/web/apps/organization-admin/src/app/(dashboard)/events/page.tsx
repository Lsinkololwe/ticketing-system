'use client';

import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { EventsListView } from '@/components/events/EventsListView';
import { useLifecycle } from '@/components/events/LifecycleDialogs';
import { useOrgEventCounts, useOrgEvents } from '@/lib/api/events';
import { useOrgContext } from '@/lib/api/org-context';
import { useState } from 'react';
import type { OrganizerEventFilterInput } from '@pml.tickets/shared/types/graphql';
import type { EventFilters } from '@/components/events/eventLogic';

export default function EventsPage() {
  const [filter, setFilter] = useState<OrganizerEventFilterInput | undefined>(undefined);
  const { events, loading, error, refetch, hasMore, loadMore } = useOrgEvents(filter);
  const counts = useOrgEventCounts();
  // Source list of the "Duplicate a past event" flow, independent of the table's filters.
  const past = useOrgEvents({ status: null, searchQuery: null, eventDateAfter: null, eventDateBefore: null, statuses: ['COMPLETED', 'CANCELLED'] });
  const { capabilities } = useOrgContext();
  const onFiltersChange = (f: EventFilters) => {
    const q = f.q.trim();
    setFilter({
      status: f.status === 'all' ? null : f.status,
      searchQuery: q.length >= 2 ? q : null,
      eventDateAfter: null,
      eventDateBefore: null,
      statuses: null,
    });
  };
  const snack = useSnackbar();
  const { run, dialogs, bulkPublish } = useLifecycle(() => void refetch());

  const publishMany = async (ids: string[]) => {
    const results = await Promise.allSettled(ids.map((id) => bulkPublish(id)));
    const ok = results.filter((r) => r.status === 'fulfilled').length;
    snack.show(`${ok} published${ok < ids.length ? `, ${ids.length - ok} skipped` : ''}`);
    void refetch();
  };

  return (
    <>
      <EventsListView
        events={events}
        loading={loading}
        error={error}
        onRetry={() => void refetch()}
        canWrite={capabilities.canWriteEvents}
        counts={counts}
        hasMore={hasMore}
        onLoadMore={() => void loadMore()}
        onFiltersChange={onFiltersChange}
        pastEvents={past.events}
        onAction={(a, e) => void run(a, e)}
        onBulkPublish={(ids) => void publishMany(ids)}
      />
      {dialogs}
    </>
  );
}
