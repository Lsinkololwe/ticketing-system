'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Button,
  Card,
  CardHeader,
  Chip,
  DataTable,
  EmptyState,
  ErrorState,
  LinearProgress,
  StatusPill,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import { useAdminEventCategories } from '@pml.tickets/shared/api/admin/modules/event';
import {
  useAdminEventsTable,
  useCitiesAdmin,
  useFeatureEvent,
  type AdminEventTableRow,
} from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { FilterBar, RowActions, useStaff } from '@/components/console';
import { canOpenModule } from '@/config/navigation';
import { csvText, formatDate, formatNumber, humanize } from '@/lib/format';
import { CancelEventDialog, type CancelTarget } from './CancelEventDialog';
import { EventQuickView } from './EventQuickView';
import { CANCELLABLE, DATE_FILTERS, EVENT_STATUSES, dateBounds, percent } from './helpers';

const PAGE_SIZE = 20;

/** Events > All events: filterable table, feature toggle, quick view, cancel. */
export function AllEventsTab() {
  const router = useRouter();
  const snackbar = useSnackbar();
  const staff = useStaff();
  const [query, setQuery] = useState('');
  const [values, setValues] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [quick, setQuick] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<CancelTarget | null>(null);

  const bounds = dateBounds(values.date ?? 'all');
  const { events, pageInfo, loading, error, refetch } = useAdminEventsTable({
    status: values.st && values.st !== 'all' ? values.st : null,
    categoryId: values.cat && values.cat !== 'all' ? values.cat : null,
    cityId: values.city && values.city !== 'all' ? values.city : null,
    organizerId: values.org && values.org !== 'all' ? values.org : null,
    eventDateAfter: bounds.after,
    eventDateBefore: bounds.before,
    searchQuery: query.trim() || null,
    page,
    size: PAGE_SIZE,
  });
  const { categories } = useAdminEventCategories({ size: 100 });
  const { cities } = useCitiesAdmin();
  const { feature } = useFeatureEvent();

  // Organizations present on the page being viewed; the backend filters by organizer id.
  const [knownOrgs, setKnownOrgs] = useState<Record<string, string>>({});
  useEffect(() => {
    setKnownOrgs((prev) => {
      const add = events.filter((e) => !prev[e.organizerId]);
      return add.length ? { ...prev, ...Object.fromEntries(add.map((e) => [e.organizerId, e.organizerName])) } : prev;
    });
  }, [events]);

  const filters = [
    { id: 'st', label: 'Status', options: EVENT_STATUSES.map((s) => ({ value: s, label: humanize(s) })) },
    { id: 'cat', label: 'Category', options: categories.map((c) => ({ value: c.code, label: c.name })) },
    { id: 'city', label: 'City', options: cities.map((c) => ({ value: c.id, label: c.name })) },
    { id: 'org', label: 'Organization', options: Object.entries(knownOrgs).map(([value, label]) => ({ value, label })) },
    { id: 'date', label: 'Date', options: DATE_FILTERS },
  ];

  const canFeature = staff.can('featureEvent');
  const financeOk = canOpenModule(staff.roles, 'finance');

  const toggleFeature = async (e: AdminEventTableRow) => {
    const res = await feature(e.id, !e.featured);
    snackbar.show({
      message: res.success ? (e.featured ? `${e.title} is no longer featured` : `${e.title} is now featured on the buyer home page`) : (res.message ?? 'Could not change the featured flag'),
      tone: res.success ? 'neutral' : 'error',
    });
  };

  const copyCsv = async () => {
    const rows = [
      ['Event', 'Organization', 'Category', 'City', 'Start', 'Status', 'Sold', 'Capacity', 'Featured'],
      ...events.map((e) => [e.title, e.organizerName, e.category?.name, e.cityName, e.eventDateTime, humanize(e.status), e.soldTickets, e.totalCapacity, e.featured ? 'Yes' : 'No']),
    ];
    try {
      await navigator.clipboard.writeText(csvText(rows));
      snackbar.show('CSV copied');
    } catch {
      snackbar.show({ message: 'Could not copy to the clipboard', tone: 'error' });
    }
  };

  const hasFilter = query !== '' || Object.values(values).some((v) => v && v !== 'all');

  return (
    <Card as="section" aria-label="All events">
      <CardHeader title="All events" subtitle="Featured placement is an admin-only flag." />
      <FilterBar
        searchLabel="Search events"
        query={query}
        onQuery={(q) => {
          setQuery(q);
          setPage(0);
        }}
        filters={filters}
        values={values}
        onFilter={(id, v) => {
          setValues((s) => ({ ...s, [id]: v }));
          setPage(0);
        }}
        onClear={() => {
          setQuery('');
          setValues({});
          setPage(0);
        }}
        actions={
          <Button variant="outlined" size="sm" icon="copy" disabled={events.length === 0} onClick={() => void copyCsv()}>
            Copy CSV
          </Button>
        }
      />
      <DataTable<AdminEventTableRow>
        caption="All events"
        rows={events}
        getRowId={(e) => e.id}
        loading={loading && events.length === 0}
        error={error && events.length === 0 ? <ErrorState error={error} onRetry={refetch} /> : undefined}
        empty={<EmptyState title={hasFilter ? 'No events match.' : 'No events yet.'} />}
        columns={[
          {
            id: 'event',
            header: 'Event',
            rowHeader: true,
            cell: (e) => (
              <>
                <b>{e.title}</b>
                <div className="m3-muted">{e.locationName || 'No venue yet'}</div>
              </>
            ),
          },
          { id: 'org', header: 'Organization', cell: (e) => e.organizerName },
          { id: 'cat', header: 'Category', cell: (e) => e.category?.name ?? '-' },
          { id: 'date', header: 'Date', cell: (e) => formatDate(e.eventDateTime) },
          { id: 'status', header: 'Status', cell: (e) => <StatusPill status={e.status} /> },
          {
            id: 'sold',
            header: 'Sold',
            cell: (e) =>
              e.totalCapacity ? (
                <>
                  <LinearProgress value={percent(e.soldTickets, e.totalCapacity)} label={`${e.title} sold`} />
                  <span className="m3-muted">
                    {formatNumber(e.soldTickets)} / {formatNumber(e.totalCapacity)}
                  </span>
                </>
              ) : (
                '-'
              ),
          },
          {
            id: 'featured',
            header: 'Featured',
            cell: (e) =>
              e.status === 'PUBLISHED' ? (
                <Chip kind="filter" selected={e.featured} disabled={!canFeature} onClick={() => void toggleFeature(e)}>
                  {e.featured ? 'Featured' : 'Feature'}
                </Chip>
              ) : (
                '-'
              ),
          },
        ]}
        rowActions={(e) => {
          const items = [
            ...(financeOk && e.soldTickets > 0
              ? [{ id: 'escrow', label: 'Escrow account', onSelect: () => router.push(`/finance/escrow?event=${e.id}`) }]
              : []),
            ...(CANCELLABLE.includes(e.status)
              ? [{ id: 'cancel', label: 'Cancel event', danger: true, disabled: !canFeature, onSelect: () => setCancelling({ id: e.id, title: e.title, soldTickets: e.soldTickets }) }]
              : []),
          ];
          return (
            <RowActions
              name={e.title}
              primary={{ label: 'Quick view', onSelect: () => setQuick(e.id) }}
              items={[{ id: 'page', label: 'Open full page', onSelect: () => router.push(`/event/${e.id}`) }, ...items]}
            />
          );
        }}
        pagination={{
          page: page + 1,
          pageSize: pageInfo.pageSize,
          total: pageInfo.totalCount,
          onPageChange: (n) => setPage(n - 1),
        }}
      />
      <EventQuickView eventId={quick} onClose={() => setQuick(null)} onCancel={(t) => setCancelling(t)} />
      <CancelEventDialog event={cancelling} onClose={() => setCancelling(null)} onDone={() => setQuick(null)} />
    </Card>
  );
}
