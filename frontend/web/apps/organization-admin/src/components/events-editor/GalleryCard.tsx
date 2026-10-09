'use client';

import { useState } from 'react';
import { useFormContext } from 'react-hook-form';
import { Button, Card, CardHeader, Dialog, EmptyState, IconButton } from '@pml.tickets/shared/components/m3';
import { useMyMedia } from '@/lib/api/media';
import { useEditorValues } from './useEditorValues';

function PickerDialog({ chosen, onAdd, onClose }: { chosen: string[]; onAdd: (urls: string[]) => void; onClose: () => void }) {
  const media = useMyMedia();
  const [picked, setPicked] = useState<string[]>([]);
  const toggle = (u: string) => setPicked((p) => (p.includes(u) ? p.filter((x) => x !== u) : [...p, u]));
  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title="Add gallery images"
      actions={
        <>
          <Button variant="text" onClick={onClose}>Cancel</Button>
          <Button variant="filled" disabled={picked.length === 0} onClick={() => { onAdd(picked); onClose(); }}>
            Add {picked.length || ''} {picked.length === 1 ? 'image' : 'images'}
          </Button>
        </>
      }
    >
      {media.error && media.items.length === 0 ? <p role="alert">Could not load your media library.</p> : null}
      {!media.loading && media.items.length === 0 && !media.error ? (
        <EmptyState icon="image" title="Your media library is empty" description="Upload images on the Media page first." />
      ) : (
        <div className="oc-opt-grid" role="group" aria-label="Media library">
          {media.items.filter((m) => !chosen.includes(m.url)).map((m) => (
            <button key={m.id} type="button" className="m3-state" aria-pressed={picked.includes(m.url)} aria-label={`Select ${m.fileName}`} onClick={() => toggle(m.url)}>
              <img className="oc-thumb" alt={m.altText ?? ''} src={m.url} />
            </button>
          ))}
        </div>
      )}
    </Dialog>
  );
}

/** Gallery images shown on the event page; they come from the media library. */
export function GalleryCard() {
  const { galleryImages } = useEditorValues();
  const { setValue } = useFormContext();
  const [open, setOpen] = useState(false);
  const list = galleryImages ?? [];
  const set = (next: string[]) => setValue('galleryImages', next, { shouldDirty: true, shouldValidate: true });
  return (
    <Card>
      <CardHeader
        title="Gallery"
        subtitle={`${list.length} image${list.length === 1 ? '' : 's'} shown on the event page.`}
        actions={<Button variant="tonal" size="sm" icon="add" onClick={() => setOpen(true)}>Add images</Button>}
      />
      {list.length ? (
        <div className="oc-opt-grid" role="list" aria-label="Gallery images">
          {list.map((u, i) => (
            <div key={u} role="listitem" className="m3-stack">
              <img className="oc-thumb" alt="" src={u} />
              <IconButton icon="delete" danger label={`Remove image ${i + 1}`} onClick={() => set(list.filter((x) => x !== u))} />
            </div>
          ))}
        </div>
      ) : (
        <p className="m3-muted">No gallery images yet.</p>
      )}
      {open ? <PickerDialog chosen={list} onAdd={(urls) => set([...list, ...urls])} onClose={() => setOpen(false)} /> : null}
    </Card>
  );
}
