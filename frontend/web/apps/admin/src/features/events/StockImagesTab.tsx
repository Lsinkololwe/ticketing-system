'use client';

import { useRef, useState } from 'react';
import { z } from 'zod';
import { Banner, Button, Card, CardHeader, ConfirmDialog, EmptyState, ErrorState, SegmentedButton, Select, Skeleton, StatusPill, Switch, TextField, useSnackbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import { useStockImageActions, useStockImages, type StockImageRow } from '@pml.tickets/shared/api/admin/modules/media-ops';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { useStaff } from '@/components/console';
import { FormDialog } from '@/features/ledger/FormDialog';
import { ListCard } from '@/features/ledger/ListCard';
import { humanize } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { STOCK_IMAGE_PURPOSE_LABELS, enumOptions, enumSchema } from '@/lib/enumLabels';

const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const stockSchema = z.object({
  title: z.string().trim().min(2, 'Give the image a title'),
  altText: z.string().trim().min(3, 'Describe the image for screen readers'),
  purpose: enumSchema(STOCK_IMAGE_PURPOSE_LABELS),
  categoryCode: z.string().trim(),
});

const toBase64 = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(',')[1] ?? '');
    r.onerror = () => reject(new Error('Could not read the file'));
    r.readAsDataURL(file);
  });

function UploadDialog({ onClose }: { onClose: () => void }) {
  const snackbar = useSnackbar();
  const { upload } = useStockImageActions();
  const file = useRef<HTMLInputElement>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const form = useZodForm(stockSchema, { defaultValues: { title: '', altText: '', purpose: 'EVENT_COVER', categoryCode: '' } });
  const { register, formState: { errors } } = form;
  return (
    <FormDialog
      title="Upload stock image"
      form={form}
      onClose={onClose}
      submitLabel="Upload image"
      onSubmit={async (v) => {
        const f = file.current?.files?.[0];
        if (!f) return setFileError('Choose an image');
        if (!TYPES.includes(f.type)) return setFileError('Use a JPEG, PNG or WebP image');
        if (f.size > MAX_BYTES) return setFileError('The image must be under 5 MB');
        setFileError(null);
        try {
          await upload({ title: v.title, altText: v.altText, purpose: v.purpose, categoryCode: v.categoryCode || undefined, fileName: f.name, contentType: f.type, contentBase64: await toBase64(f) });
          snackbar.show('Stock image uploaded');
          onClose();
        } catch (e) {
          snackbar.show((e as Error).message || 'Could not upload the image');
        }
      }}
    >
      <label className="m3-field">
        <span className="m3-muted">Image file</span>
        <input ref={file} type="file" accept={TYPES.join(',')} aria-label="Image file" aria-invalid={fileError ? true : undefined} />
      </label>
      {fileError ? <span role="alert" className="m3-muted">{fileError}</span> : null}
      <TextField label="Title" density="form" {...register('title')} errorText={errors.title?.message} />
      <TextField label="Alt text" density="form" {...register('altText')} errorText={errors.altText?.message} />
      <Select label="Used as" density="form" {...register('purpose')}>
        {enumOptions(STOCK_IMAGE_PURPOSE_LABELS).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </Select>
      <TextField label="Category code (optional)" density="form" {...register('categoryCode')} />
    </FormDialog>
  );
}

/** Platform-owned stock images (stockImages): upload, activate or deactivate, delete. */
export function StockImagesTab() {
  const { can } = useStaff();
  const snackbar = useSnackbar();
  const { images, loading, error, refetch } = useStockImages();
  const acts = useStockImageActions();
  const [uploading, setUploading] = useState(false);
  const [deleting, setDeleting] = useState<StockImageRow | null>(null);
  const [view, setView] = useState<'grid' | 'table'>('grid');
  const allowed = can('stock');
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); snackbar.show(ok); } catch (e) { snackbar.show((e as Error).message || 'That did not go through'); }
  };
  const cols: Array<DataColumn<StockImageRow>> = [
    // eslint-disable-next-line @next/next/no-img-element
    { id: 'image', header: 'Image', cell: (i) => <img className="adm-thumb" src={i.url} alt={i.altText || i.title || 'Stock image'} loading="lazy" /> },
    { id: 'title', header: 'Title', rowHeader: true, cell: (i) => <>{i.title ?? '—'}<br /><span className="m3-muted">{i.altText ?? ''}</span></> },
    { id: 'purpose', header: 'Used as', cell: (i) => <>{humanize(i.purpose)}{i.categoryCode ? <><br /><span className="m3-mono m3-muted">{i.categoryCode}</span></> : null}</> },
    { id: 'active', header: 'Active', cell: (i) => (allowed ? <Switch label={`${i.title ?? 'Image'} active`} checked={i.active} onChange={(e) => void run(() => acts.update(i.id, { active: e.target.checked }), e.target.checked ? 'Image activated' : 'Image deactivated')} /> : <StatusPill status={i.active ? 'ACTIVE' : 'INACTIVE'} />) },
  ];
  return (
    <div className="m3-stack">
      {error && images.length === 0 ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : loading && images.length === 0 ? (
        <Skeleton width="100%" />
      ) : images.length === 0 ? (
        <Card as="section" aria-label="Stock images">
          <CardHeader title="Stock images" subtitle="Platform-owned images. Organizers pick from the active ones when they have no banner of their own." actions={<Button variant="filled" size="sm" icon="add" disabled={!allowed} onClick={() => setUploading(true)}>Upload image</Button>} />
          <EmptyState title="No stock images yet." description={allowed ? undefined : needText('stock')} />
        </Card>
      ) : (
        <>
          <Banner tone="info">Platform-owned images. Organizers pick from the active ones when they have no banner of their own.</Banner>
          <div className="adm-tabsrow">
            <SegmentedButton<'grid' | 'table'> label="Stock image view" value={view} onChange={setView} options={[{ value: 'grid', label: 'Grid' }, { value: 'table', label: 'Table' }]} />
          </div>
          {view === 'grid' ? (
            <Card as="section" aria-label="Stock images">
              <CardHeader
                title="Stock images"
                subtitle={`${images.filter((i) => i.active).length} active of ${images.length}`}
                actions={<Button variant="filled" size="sm" icon="add" disabled={!allowed} onClick={() => setUploading(true)}>Upload image</Button>}
              />
              <ul className="adm-mgrid" aria-label="Stock images">
                {images.map((i) => (
                  <li key={i.id} className="adm-mcard">
                    <div className="adm-mcard__im">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={i.url} alt={i.altText || i.title || 'Stock image'} loading="lazy" />
                      {!i.active ? <StatusPill className="adm-mcard__flag">Inactive</StatusPill> : null}
                    </div>
                    <div className="adm-mcard__body">
                      <b>{i.title ?? '—'}</b>
                      <small>{i.categoryCode ?? humanize(i.purpose)}</small>
                      <span className="m3-row">
                        {allowed ? <Switch label={`${i.title ?? 'Image'} active`} checked={i.active} onChange={(e) => void run(() => acts.update(i.id, { active: e.target.checked }), e.target.checked ? 'Image activated' : 'Image deactivated')} /> : null}
                        <Button variant="text" size="sm" danger disabled={!allowed} onClick={() => setDeleting(i)}>Delete…</Button>
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
          ) : (
            <ListCard<StockImageRow>
              title="Stock images"
              subtitle={`${images.filter((i) => i.active).length} active of ${images.length}`}
              toolbar={<Button variant="filled" size="sm" icon="add" disabled={!allowed} onClick={() => setUploading(true)}>Upload image</Button>}
              caption="Stock images"
              rows={images}
              columns={cols}
              getRowId={(i) => i.id}
              searchLabel="Search stock images"
              searchText={(i) => `${i.title ?? ''} ${i.altText ?? ''} ${i.categoryCode ?? ''}`}
              rowActions={(i) => <Button variant="text" size="sm" danger disabled={!allowed} onClick={() => setDeleting(i)}>Delete…</Button>}
              empty={{ title: 'No stock images yet.' }}
            />
          )}
        </>
      )}
      {uploading ? <UploadDialog onClose={() => setUploading(false)} /> : null}
      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title={`Delete ${deleting?.title ?? 'this image'}?`}
        description="Events that used it fall back to their category image."
        confirmLabel="Delete image"
        danger
        loading={acts.busy}
        onConfirm={() => deleting && void run(() => acts.remove(deleting.id), 'Stock image deleted').then(() => setDeleting(null))}
      />
    </div>
  );
}
