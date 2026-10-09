'use client';

import { useState } from 'react';
import { Banner, Button, Card, CardHeader, EmptyState, ErrorState, Pagination, SegmentedButton, StatusPill, useSnackbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import { useMediaAssets, useMediaModeration, type MediaAssetRow } from '@pml.tickets/shared/api/admin/modules/media-ops';
import { FilterBar, ReasonDialog, useStaff } from '@/components/console';
import { ListCard } from '@/features/ledger/ListCard';
import { formatDateTime, humanize } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { useStepUp } from '@/lib/useStepUp';
export { StockImagesTab } from './StockImagesTab';

type Pending = { kind: 'flag' | 'remove'; asset: MediaAssetRow };

const sizeText = (bytes: number) => (bytes >= 1_048_576 ? `${(bytes / 1_048_576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

const COLUMNS: Array<DataColumn<MediaAssetRow>> = [
  {
    id: 'image',
    header: 'Image',
    cell: (a) =>
      a.status === 'REMOVED' ? (
        <span className="m3-muted">Removed</span>
      ) : (
        // eslint-disable-next-line @next/next/no-img-element
        <img className="adm-thumb" src={a.url} alt={a.altText || a.title || a.fileName} loading="lazy" />
      ),
  },
  { id: 'file', header: 'File', rowHeader: true, cell: (a) => <>{a.title || a.fileName}<br /><span className="m3-muted">{a.contentType} · {sizeText(a.sizeBytes)}</span></> },
  { id: 'event', header: 'Event', cell: (a) => <span className="m3-mono">{a.eventId ?? '—'}</span> },
  { id: 'org', header: 'Organization', cell: (a) => <span className="m3-mono">{a.organizationId ?? '—'}</span> },
  { id: 'alt', header: 'Alt text', cell: (a) => a.altText || '—' },
  {
    id: 'status',
    header: 'Status',
    cell: (a) => (
      <>
        <StatusPill status={a.status} />
        {a.flaggedReason && a.status === 'FLAGGED' ? <><br /><span className="m3-muted">{a.flaggedReason}</span></> : null}
        {a.removedReason && a.status === 'REMOVED' ? <><br /><span className="m3-muted">{a.removedReason} · {formatDateTime(a.removedAt)}</span></> : null}
      </>
    ),
  },
];

/** Media moderation queue: every organizer upload, filterable by state. Flag, remove (with a reason) or restore. */
export function MediaModerationTab() {
  const { can } = useStaff();
  const { guard } = useStepUp();
  const snackbar = useSnackbar();
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const q = useMediaAssets({ status: (status || null) as MediaAssetRow['status'] | null, search, page, size: 24 });
  const mod = useMediaModeration();
  const [pending, setPending] = useState<Pending | null>(null);
  const [view, setView] = useState<'grid' | 'table'>('grid');
  const flagged = useMediaAssets({ status: 'FLAGGED', size: 1 });
  const allowed = can('mediaMod');

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      snackbar.show(ok);
      setPending(null);
    } catch (e) {
      snackbar.show((e as Error).message || 'That did not go through');
    }
  };

  const actionsFor = (a: MediaAssetRow) => (
    <>
      {a.status === 'ACTIVE' ? <Button variant="text" size="sm" disabled={!allowed} onClick={() => setPending({ kind: 'flag', asset: a })}>Flag…</Button> : null}
      {a.status !== 'REMOVED' ? <Button variant="text" size="sm" danger disabled={!allowed} onClick={() => setPending({ kind: 'remove', asset: a })}>Remove…</Button> : null}
      {a.status !== 'ACTIVE' ? <Button variant="text" size="sm" disabled={!allowed} onClick={() => void run(() => mod.restore(a.id), `${a.fileName} restored`)}>Restore</Button> : null}
    </>
  );
  const viewToggle = (
    <SegmentedButton<'grid' | 'table'> label="Media view" value={view} onChange={setView} options={[{ value: 'grid', label: 'Grid' }, { value: 'table', label: 'Table' }]} />
  );

  return (
    <>
      {flagged.totalElements > 0 ? (
        <Banner tone="info">
          {flagged.totalElements} image{flagged.totalElements === 1 ? ' is' : 's are'} flagged and waiting for a decision. Removing an image hides it from buyers straight away; organizers are told why.
        </Banner>
      ) : null}
      {view === 'grid' ? (
        <Card as="section" aria-label="Media from all organizations">
          <CardHeader title="Media from all organizations" subtitle={`Banners and gallery images uploaded by organizers.${allowed ? '' : ` ${needText('mediaMod')}`}`} />
          <FilterBar
            searchLabel="Search file, alt text, event or organization"
            query={search}
            onQuery={(v) => { setSearch(v); setPage(0); }}
            filters={[{ id: 'status', label: 'Status', options: ['ACTIVE', 'FLAGGED', 'REMOVED'].map((x) => ({ value: x, label: humanize(x) })) }]}
            values={{ status: status || 'all' }}
            onFilter={(_, v) => { setStatus(v && v !== 'all' ? v : ''); setPage(0); }}
            onClear={() => { setSearch(''); setStatus(''); setPage(0); }}
          />
          {viewToggle}
          {q.error && q.assets.length === 0 ? (
            <ErrorState error={q.error} onRetry={q.refetch} />
          ) : q.assets.length === 0 ? (
            <EmptyState title="No images to moderate." description="Event banners can still be reviewed on each event's Media tab." />
          ) : (
            <ul className="adm-mgrid" aria-label="Media">
              {q.assets.map((a) => (
                <li key={a.id} className="adm-mcard">
                  <div className="adm-mcard__im">
                    {a.status === 'REMOVED' ? (
                      <span className="m3-muted">Removed</span>
                    ) : (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={a.url} alt={a.altText || a.title || a.fileName} loading="lazy" />
                    )}
                    {a.status === 'FLAGGED' ? <StatusPill status={a.status} className="adm-mcard__flag" /> : null}
                  </div>
                  <div className="adm-mcard__body">
                    <b title={a.fileName}>{a.title || a.fileName}</b>
                    <small>{a.eventId ?? '—'}</small>
                    <small>{a.organizationId ?? '—'}</small>
                    {a.flaggedReason && a.status === 'FLAGGED' ? <small>{a.flaggedReason}</small> : null}
                    <span className="m3-row">{actionsFor(a)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <Pagination page={page + 1} pageSize={24} total={q.totalElements} onPageChange={(p) => setPage(p - 1)} />
        </Card>
      ) : (
      <>
      <div className="adm-tabsrow">{viewToggle}</div>
      <ListCard<MediaAssetRow>
        title="Media from all organizations"
        subtitle={`Banners and gallery images uploaded by organizers.${allowed ? '' : ` ${needText('mediaMod')}`}`}
        caption="Media from all organizations"
        rows={q.assets}
        columns={COLUMNS}
        getRowId={(a) => a.id}
        searchLabel="Search file, alt text, event or organization"
        onSearchQuery={(v) => { setSearch(v); setPage(0); }}
        searchText={(a) => `${a.fileName} ${a.altText ?? ''}`}
        filters={[{ id: 'status', label: 'Status', options: ['ACTIVE', 'FLAGGED', 'REMOVED'].map((s) => ({ value: s, label: humanize(s) })) }]}
        onFilterChange={(v) => { setStatus(v.status && v.status !== 'all' ? v.status : ''); setPage(0); }}
        serverPage={{ page: page + 1, total: q.totalElements, onPage: (p) => setPage(p - 1) }}
        pageSize={24}
        loading={q.loading}
        error={q.error}
        onRetry={q.refetch}
        empty={{ title: 'No images to moderate.', description: "Event banners can still be reviewed on each event's Media tab." }}
        rowActions={actionsFor}
      />
      </>
      )}
      <ReasonDialog
        open={pending !== null}
        onClose={() => setPending(null)}
        title={pending ? `${pending.kind === 'flag' ? 'Flag' : 'Remove'} ${pending.asset.fileName}?` : ''}
        body={pending?.kind === 'remove' ? 'The image disappears from the event and the organizer is told why. You can restore it later.' : 'Flagged images stay visible while someone reviews them.'}
        confirmLabel={pending?.kind === 'flag' ? 'Flag image' : 'Remove image'}
        danger={pending?.kind === 'remove'}
        loading={mod.busy}
        onConfirm={(reason) => {
          if (!pending) return;
          const p = pending;
          void run(() => (p.kind === 'flag' ? mod.flag(p.asset.id, reason) : guard(() => mod.remove(p.asset.id, reason))), p.kind === 'flag' ? 'Image flagged' : 'Image removed');
        }}
      />
    </>
  );
}
