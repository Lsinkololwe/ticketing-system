'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import {
  BulkBar,
  Button,
  Dialog,
  Card,
  Chip,
  ChipGroup,
  DataTable,
  EmptyState,
  PageHeader,
  RowMenu,
  Select,
  TextField,
  Toolbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import type { OrgEventRow } from '@/lib/api/events';
import { formatCount, formatEventDate, formatMoney } from '@/lib/format/figure';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status, statusLabel } from '@/components/console/Status';
import { actionsFor, DEFAULT_FILTERS, EVENT_STATUSES, filterEvents, type EventFilters, type RowAction } from './eventLogic';

export interface EventsListViewProps {
  events: OrgEventRow[];
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  canWrite: boolean;
  onAction: (action: RowAction['id'], event: OrgEventRow) => void;
  /** Resolves with ids that were published and ids skipped (with reason). */
  onBulkPublish: (ids: string[]) => void;
  now?: Date;
  pageSize?: number;
  /** Server totals per status; absent while they load. */
  counts?: { all: number; byStatus: Record<string, number> } | null;
  hasMore?: boolean;
  onLoadMore?: () => void;
  /** Status, search and date run on the server: the host refetches on change. */
  onFiltersChange?: (f: EventFilters) => void;
  /** Completed and cancelled events the organizer can copy into a new draft. */
  pastEvents?: OrgEventRow[];
}

export function EventsListView({ events, loading, error, onRetry, canWrite, onAction, onBulkPublish, now, pageSize = 12, counts, hasMore, onLoadMore, onFiltersChange, pastEvents }: EventsListViewProps) {
  const [dupOpen, setDupOpen] = useState(false);
  const [dupId, setDupId] = useState('');
  const [f, setF] = useState<EventFilters>(DEFAULT_FILTERS);
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const set = (patch: Partial<EventFilters>) => {
    const next = { ...f, ...patch };
    setF(next);
    onFiltersChange?.(next);
    setPage(1);
  };
  const clock = now ?? new Date();
  const list = useMemo(() => filterEvents(events, f, clock), [events, f, clock]);
  const rows = list.slice((page - 1) * pageSize, page * pageSize);
  const countOf = (s: string) => (counts ? (s === 'all' ? counts.all : counts.byStatus[s] ?? 0) : events.filter((e) => s === 'all' || e.status === s).length);
  const categories = [...new Set(events.map((e) => e.category?.name).filter(Boolean))] as string[];

  const columns: DataColumn<OrgEventRow>[] = [
    {
      id: 'title',
      header: 'Event',
      rowHeader: true,
      cell: (e) => (
        <span className="m3-row">
          {e.bannerImageUrl ? <img className="oc-thumb" alt="" src={e.bannerImageUrl} /> : <span className="oc-thumb" aria-hidden="true" />}
          <span>
            <Link className="m3-link" href={`/events/${e.id}`}>{e.title}</Link>
            <br />
            <span className="m3-muted">
              {e.locationName ?? 'No venue yet'}
              {e.cityName ? `, ${e.cityName}` : ''}
              {e.category ? ` · ${e.category.name}` : ''}
            </span>
          </span>
        </span>
      ),
    },
    { id: 'date', header: 'Date', cell: (e) => formatEventDate(e.eventDateTime) },
    { id: 'status', header: 'Status', cell: (e) => <Status status={e.status} /> },
    {
      id: 'sold',
      header: 'Sold / quantity',
      cell: (e) => {
        const pct = e.totalCapacity ? Math.min(100, (e.soldTickets / e.totalCapacity) * 100) : 0;
        return (
          <>
            <div className="oc-bar" aria-hidden="true"><i style={{ width: `${pct}%` }} /></div>
            <span className="m3-muted">{formatCount(e.soldTickets)} / {formatCount(e.totalCapacity)}</span>
          </>
        );
      },
    },
    { id: 'revenue', header: 'Revenue', align: 'end', cell: (e) => <span className="m3-mono">{formatMoney(e.revenue, e.currency)}</span> },
  ];

  const publishable = [...selected].filter((id) => events.find((e) => e.id === id)?.status === 'APPROVED');

  return (
    <div data-testid="events-page">
      <PageHeader
        title="Events"
        subtitle="Create, submit, publish and manage your events."
        actions={
          canWrite ? (
            <>
              {pastEvents ? <Button variant="outlined" onClick={() => setDupOpen(true)}>Duplicate a past event</Button> : null}
              <LinkBtn href="/events/new" variant="filled" icon="add">Create event</LinkBtn>
            </>
          ) : null
        }
      />
      {dupOpen ? (
        <Dialog
          open
          onClose={() => setDupOpen(false)}
          title="Duplicate a past event"
          actions={
            <>
              <Button variant="text" onClick={() => setDupOpen(false)}>Cancel</Button>
              <Button
                variant="filled"
                disabled={!dupId}
                onClick={() => {
                  const e = (pastEvents ?? []).find((x) => x.id === dupId);
                  setDupOpen(false);
                  setDupId('');
                  if (e) onAction('duplicate', e);
                }}
              >
                Continue
              </Button>
            </>
          }
        >
          <p>Start a new draft from an event that has already happened. Details and ticket tiers are copied; you set the new date next.</p>
          {(pastEvents ?? []).length ? (
            <Select label="Past event" value={dupId} onChange={(e) => setDupId(e.target.value)}>
              <option value="">Choose an event</option>
              {(pastEvents ?? []).map((e) => <option key={e.id} value={e.id}>{e.title} · {formatEventDate(e.eventDateTime)}</option>)}
            </Select>
          ) : (
            <EmptyState icon="calendar" title="No past events yet" description="Events that have finished or were cancelled appear here." />
          )}
        </Dialog>
      ) : null}
      <Card>
        <ChipGroup label="Quick filters">
          {(['all', ...EVENT_STATUSES] as const)
            .filter((s) => s === 'all' || countOf(s))
            .map((s) => (
              <Chip key={s} kind="filter" selected={f.status === s} onClick={() => set({ status: s })}>
                {s === 'all' ? 'All' : statusLabel(s)} · {countOf(s)}
              </Chip>
            ))}
        </ChipGroup>
        <Toolbar label="Filters">
          <TextField density="compact" label="Search events" placeholder="Title, venue or city" value={f.q} onChange={(e) => set({ q: e.target.value })} />
          <Select density="compact" label="Status" value={f.status} onChange={(e) => set({ status: e.target.value as EventFilters['status'] })}>
            <option value="all">All statuses</option>
            {EVENT_STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </Select>
          <Select density="compact" label="Category" value={f.category} onChange={(e) => set({ category: e.target.value })}>
            <option value="all">All categories</option>
            {categories.map((c) => <option key={c}>{c}</option>)}
          </Select>
          <Select density="compact" label="Date" value={f.when} onChange={(e) => set({ when: e.target.value as EventFilters['when'] })}>
            <option value="all">Any date</option>
            <option value="upcoming">Upcoming</option>
            <option value="past">Past</option>
            <option value="soon">Next 30 days</option>
          </Select>
          <Select density="compact" label="Sort by" value={f.sort} onChange={(e) => set({ sort: e.target.value as EventFilters['sort'] })}>
            <option value="date_asc">Date, soonest</option>
            <option value="date_desc">Date, latest</option>
            <option value="name">Name</option>
            <option value="revenue">Revenue</option>
            <option value="sold">Tickets sold</option>
          </Select>
        </Toolbar>
        {canWrite ? (
          <BulkBar count={selected.size}>
            <span>{selected.size} selected</span>
            <Button size="sm" variant="tonal" disabled={!publishable.length} onClick={() => onBulkPublish(publishable)}>
              Publish selected ({publishable.length})
            </Button>
          </BulkBar>
        ) : null}
        <DataTable
          caption="Events"
          columns={columns}
          rows={rows}
          getRowId={(e) => e.id}
          loading={loading && !events.length}
          error={
            error ? (
              <div role="alert">
                {error.message ?? 'Events could not be loaded.'} {onRetry ? <Button size="sm" variant="tonal" onClick={onRetry}>Try again</Button> : null}
              </div>
            ) : undefined
          }
          empty={
            <EmptyState
              icon="calendar"
              title={events.length ? 'No events match' : 'No events yet'}
              description={events.length ? 'Clear the filters or create a new event.' : 'Create your first event to start selling tickets.'}
              action={canWrite && !events.length ? <LinkBtn href="/events/new" variant="filled">Create event</LinkBtn> : undefined}
            />
          }
          selectable={canWrite}
          selectedIds={selected}
          onSelectionChange={setSelected}
          rowActions={(e) => (
            <span className="m3-row">
              <LinkBtn href={`/events/${e.id}`} size="sm" variant="tonal">Open</LinkBtn>
              <RowMenu
                label={`More actions for ${e.title}`}
                items={(canWrite ? actionsFor(e.status) : []).map((a) => ({ id: a.id, label: a.label, danger: a.danger, onSelect: () => onAction(a.id, e) }))}
              />
            </span>
          )}
          pagination={{ page, pageSize, total: list.length, onPageChange: setPage }}
        />
        {hasMore ? (
          <div className="oc-section">
            <Button variant="outlined" onClick={onLoadMore}>Load more events</Button>
          </div>
        ) : null}
      </Card>
    </div>
  );
}
