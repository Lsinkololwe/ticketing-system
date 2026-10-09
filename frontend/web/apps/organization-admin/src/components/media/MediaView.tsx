'use client';

import { useMemo, useState } from 'react';
import { Banner, Button, Card, DataTable, EmptyState, ErrorState, PageHeader, SegmentedButton, Skeleton, TextField } from '@pml.tickets/shared/components/m3';
import type { MediaAssetRow } from '@/lib/api/media';
import { formatEventDate } from '@/lib/format/figure';
import { MediaDetailSheet } from './MediaDetailSheet';

export const formatSize = (b: number) => (b >= 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);

export interface MediaViewProps {
  items: MediaAssetRow[];
  loading?: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  hasMore?: boolean;
  onLoadMore?: () => void;
  canManage: boolean;
  /** Validated files only; reject to surface a message. */
  onUpload: (files: File[]) => Promise<string | null | void>;
  onSave: (id: string, v: { title: string; altText: string }) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

export function MediaView(p: MediaViewProps) {
  const [q, setQ] = useState('');
  const [view, setView] = useState<'grid' | 'list'>('grid');
  const [openId, setOpenId] = useState<string | null>(null);
  const [uploadMsg, setUploadMsg] = useState<string | null>(null);
  const open = p.items.find((m) => m.id === openId) ?? null;
  const list = useMemo(
    () => p.items.filter((m) => !q || `${m.fileName} ${m.title ?? ''} ${m.altText ?? ''}`.toLowerCase().includes(q.toLowerCase())),
    [p.items, q]
  );
  const total = p.items.reduce((a, m) => a + m.sizeBytes, 0);

  return (
    <div data-testid="media-page">
      <PageHeader
        title="Media"
        subtitle={`${p.items.length} images · ${formatSize(total)} used`}
        actions={
          p.canManage ? (
            <label className="m3-btn m3-state" data-variant="filled">
              Upload images
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                hidden
                onChange={async (e) => {
                  const files = Array.from(e.target.files ?? []);
                  e.target.value = '';
                  if (files.length) setUploadMsg((await p.onUpload(files)) ?? null);
                }}
              />
            </label>
          ) : null
        }
      />
      {uploadMsg ? <Banner tone="error" urgent>{uploadMsg}</Banner> : null}
      {p.error && p.items.length === 0 ? (
        <ErrorState error={p.error} onRetry={p.onRetry} />
      ) : p.loading && p.items.length === 0 ? (
        <div className="m3-stack" role="status" aria-label="Loading" data-testid="loading"><Skeleton /><Skeleton /></div>
      ) : (
        <Card>
          <div className="m3-toolbar" role="toolbar" aria-label="Media filters">
            <TextField label="Search images" density="compact" value={q} placeholder="Name or alt text" onChange={(e) => setQ(e.target.value)} />
            <SegmentedButton label="View" value={view} onChange={setView} options={[{ value: 'grid', label: 'Grid' }, { value: 'list', label: 'List' }]} />
          </div>
          {list.length === 0 ? (
            <EmptyState icon="image" title={p.items.length ? 'No images match' : 'No images yet'} description={p.items.length ? 'Clear the search, or upload a new image.' : 'Upload JPG, PNG or WEBP images up to 5 MB each.'} />
          ) : view === 'grid' ? (
            <div className="oc-opt-grid" role="list" aria-label="Images">
              {list.map((m) => (
                <div key={m.id} role="listitem" className="m3-stack">
                  <img className="oc-thumb" alt={m.altText ?? ''} src={m.url} />
                  <b>{m.title || m.fileName}</b>
                  <span className="m3-muted">{formatSize(m.sizeBytes)}</span>
                  <Button size="sm" variant="tonal" onClick={() => setOpenId(m.id)} aria-label={`Open ${m.fileName}`}>Details</Button>
                </div>
              ))}
            </div>
          ) : (
            <DataTable<MediaAssetRow>
              caption="Images"
              rows={list}
              getRowId={(m) => m.id}
              columns={[
                { id: 'name', header: 'Image', cell: (m) => m.title || m.fileName, rowHeader: true },
                { id: 'type', header: 'Type', cell: (m) => m.contentType.replace('image/', '').toUpperCase() },
                { id: 'size', header: 'Size', cell: (m) => formatSize(m.sizeBytes) },
                { id: 'added', header: 'Added', cell: (m) => (m.createdAt ? formatEventDate(m.createdAt) : '—') },
              ]}
              rowActions={(m) => <Button size="sm" variant="tonal" onClick={() => setOpenId(m.id)}>Details</Button>}
            />
          )}
          {p.hasMore ? <div className="oc-section"><Button variant="outlined" onClick={p.onLoadMore}>Load more</Button></div> : null}
        </Card>
      )}
      <MediaDetailSheet item={open} canManage={p.canManage} onClose={() => setOpenId(null)} onSave={p.onSave} onDelete={p.onDelete} />
    </div>
  );
}
