'use client';

import { useRef, useState } from 'react';
import { useController } from 'react-hook-form';
import { Button, Dialog, EmptyState } from '@pml.tickets/shared/components/m3';
import { fileToBase64, MEDIA_MAX_BYTES, MEDIA_TYPES, useMediaActions, useMyMedia } from '@/lib/api/media';

export interface MediaPickerFieldProps {
  name: string;
  label: string;
  /** Verb on the button, e.g. "logo" gives "Choose logo" / "Change logo". */
  noun?: string;
  helperText?: string;
  disabled?: boolean;
}

function PickerDialog({ current, onPick, onClose }: { current: string; onPick: (url: string) => void; onClose: () => void }) {
  const media = useMyMedia();
  const actions = useMediaActions();
  const file = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const upload = async (f: File | undefined) => {
    if (!f) return;
    setProblem(null);
    if (!MEDIA_TYPES.includes(f.type)) return setProblem('Only JPG, PNG or WEBP images can be uploaded.');
    if (f.size > MEDIA_MAX_BYTES) return setProblem('That image is larger than 5 MB.');
    setBusy(true);
    try {
      const res = await actions.upload({ fileName: f.name, contentType: f.type, contentBase64: await fileToBase64(f), title: null, altText: null, eventId: null });
      const url = (res.data as { uploadMedia?: { url?: string | null } } | null | undefined)?.uploadMedia?.url;
      if (!url) throw new Error('The image was uploaded but no address came back.');
      onPick(url);
      onClose();
    } catch (e) {
      setProblem(e instanceof Error ? e.message : 'The image could not be uploaded.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title="Choose an image"
      actions={
        <>
          <input ref={file} className="oc-hidden-file" type="file" accept={MEDIA_TYPES.join(',')} aria-label="Upload an image" onChange={(e) => void upload(e.target.files?.[0])} />
          <Button variant="tonal" icon="add" loading={busy} onClick={() => file.current?.click()}>Upload new image</Button>
          <Button variant="text" onClick={onClose}>Cancel</Button>
        </>
      }
    >
      {problem ? <p role="alert">{problem}</p> : null}
      {media.error && media.items.length === 0 ? <p role="alert">Could not load your media library.</p> : null}
      {!media.loading && media.items.length === 0 && !media.error ? (
        <EmptyState icon="image" title="Your media library is empty" description="Upload a JPG, PNG or WEBP image up to 5 MB." />
      ) : (
        <div className="oc-opt-grid" role="group" aria-label="Media library">
          {media.items.map((m) => (
            <button key={m.id} type="button" className="m3-state" aria-pressed={m.url === current} aria-label={`Use ${m.title || m.fileName}`} onClick={() => { onPick(m.url); onClose(); }}>
              <img className="oc-thumb" alt={m.altText ?? ''} src={m.url} />
            </button>
          ))}
        </div>
      )}
    </Dialog>
  );
}

/** An image field whose value is a media-library URL: preview, choose or upload, remove. */
export function MediaPickerField({ name, label, noun = 'image', helperText, disabled }: MediaPickerFieldProps) {
  const { field, fieldState } = useController({ name });
  const [open, setOpen] = useState(false);
  const url = String(field.value ?? '');
  return (
    <div className="oc-media-field">
      <span className="oc-rich-label">{label}</span>
      <div className="oc-media-field__box" data-invalid={fieldState.error ? 'true' : undefined}>
        {url ? <img className="oc-thumb" alt={`${label} preview`} src={url} /> : <span className="oc-thumb" aria-hidden="true" />}
        {disabled ? null : (
          <>
            <Button variant="tonal" size="sm" onClick={() => setOpen(true)}>{url ? `Change ${noun}` : `Choose ${noun}`}</Button>
            {url ? <Button variant="text" size="sm" onClick={() => field.onChange('')}>Remove</Button> : null}
          </>
        )}
      </div>
      {helperText ? <span className="m3-muted">{helperText}</span> : null}
      {fieldState.error?.message ? <p role="alert" className="oc-rich-error">{fieldState.error.message}</p> : null}
      {open ? <PickerDialog current={url} onPick={(u) => field.onChange(u)} onClose={() => setOpen(false)} /> : null}
    </div>
  );
}
