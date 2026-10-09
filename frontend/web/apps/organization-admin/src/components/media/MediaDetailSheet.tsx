'use client';

import { useState } from 'react';
import { z } from 'zod';
import { Banner, Button, ConfirmDialog, SideSheet, TextField } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import type { MediaAssetRow } from '@/lib/api/media';
import { formatEventDate } from '@/lib/format/figure';

const schema = z.object({
  title: z.string().trim().max(120, 'Use at most 120 characters'),
  altText: z.string().trim().max(200, 'Use at most 200 characters'),
});

function EditForm({ item, onSave }: { item: MediaAssetRow; onSave: (id: string, v: z.output<typeof schema>) => Promise<void> }) {
  const form = useZodForm(schema, { defaultValues: { title: item.title ?? '', altText: item.altText ?? '' } });
  return (
    <Form form={form} aria-label="Edit image" guardLeave={false} onSubmit={(v) => onSave(item.id, v)}>
      <TextFieldRHF name="title" label="Title" />
      <TextFieldRHF name="altText" label="Alt text" helperText="Describe the image for people using screen readers." />
      <FormActions submitLabel="Save" requireDirty />
    </Form>
  );
}

export interface MediaDetailSheetProps {
  item: MediaAssetRow | null;
  canManage: boolean;
  onClose: () => void;
  onSave: (id: string, v: { title: string; altText: string }) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

export function MediaDetailSheet({ item, canManage, onClose, onSave, onDelete }: MediaDetailSheetProps) {
  const [confirm, setConfirm] = useState(false);
  return (
    <>
      <SideSheet open={Boolean(item)} onClose={onClose} title="Image details" subtitle={item?.fileName}>
        {item ? (
          <div className="m3-stack">
            <img className="oc-thumb" alt={item.altText ?? ''} src={item.url} />
            {item.status !== 'ACTIVE' ? <Banner tone="warning" title="Flagged by a moderator.">{item.flaggedReason ?? item.removedReason ?? 'This image is not shown to buyers.'}</Banner> : null}
            <div className="m3-kv"><span>Size</span><b>{Math.max(1, Math.round(item.sizeBytes / 1024))} KB</b></div>
            <div className="m3-kv"><span>Added</span><b>{item.createdAt ? formatEventDate(item.createdAt) : '—'}</b></div>
            <TextField label="Image URL" readOnly value={item.url} />
            {canManage ? (
              <>
                <EditForm key={item.id} item={item} onSave={onSave} />
                <Button variant="outlined" danger onClick={() => setConfirm(true)}>Delete image</Button>
              </>
            ) : null}
          </div>
        ) : null}
      </SideSheet>
      <ConfirmDialog
        open={confirm}
        danger
        title="Delete this image?"
        description="Events that use it will lose the picture."
        confirmLabel="Delete image"
        onClose={() => setConfirm(false)}
        onConfirm={async () => {
          setConfirm(false);
          if (item) await onDelete(item.id);
          onClose();
        }}
      />
    </>
  );
}
