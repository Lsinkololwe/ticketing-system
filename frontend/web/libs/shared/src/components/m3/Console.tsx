'use client';

import { useState, type ReactNode } from 'react';
import { Button, IconButton } from './Button';
import { TextField } from './Fields';
import { Icon } from './icons';
import { Card, StatusPill } from './Display';
import { cx } from './utils';

/** Money with an explicit sign in text (credits "+", debits "-") as well as colour. */
export function Amount({ value, formatted, className }: { value: number; formatted: string; className?: string }) {
  const sign = value > 0 ? '+' : value < 0 ? '−' : '';
  return (
    <span className={cx('m3-num', value > 0 && 'm3-pos', value < 0 && 'm3-neg', className)}>
      {sign}
      {formatted}
    </span>
  );
}

export interface SettingRowProps {
  label: string;
  description?: ReactNode;
  /** The control (Switch, Select, Button). Give it an accessible name. */
  control: ReactNode;
}
/** Settings list row: text left, control right. Used by organization settings, platform configuration, notification preferences. */
export function SettingRow({ label, description, control }: SettingRowProps) {
  return (
    <div className="m3-setting">
      <div className="m3-setting__text">
        <b className="m3-setting__label">{label}</b>
        {description ? <span className="m3-muted">{description}</span> : null}
      </div>
      <div className="m3-setting__control">{control}</div>
    </div>
  );
}

export interface QueueItemProps {
  title: string;
  meta?: ReactNode;
  status?: string;
  /** ISO or display date submitted. */
  submitted?: string;
  /** Buttons: Review (opens SideSheet), Approve, Reject. */
  actions: ReactNode;
  leading?: ReactNode;
}
/**
 * One row of an approvals queue (organizers, events, documents). The item is a
 * card with its own action buttons: there is no clickable card or row.
 */
export function QueueItem({ title, meta, status, submitted, actions, leading }: QueueItemProps) {
  return (
    <Card as="article" aria-label={title}>
      <div className="m3-queue">
        {leading}
        <div className="m3-queue__main">
          <b>{title}</b>
          {meta ? <span className="m3-muted">{meta}</span> : null}
          {submitted ? <span className="m3-muted">Submitted {submitted}</span> : null}
        </div>
        {status ? <StatusPill status={status} /> : null}
        <div className="m3-row">{actions}</div>
      </div>
    </Card>
  );
}

export interface ReadinessItem {
  id: string;
  label: string;
  done: boolean;
  hint?: string;
}
/** "Ready to submit" checklist: each item states done / to do in text, not colour only. */
export function ReadinessList({ items, label = 'Readiness checklist' }: { items: ReadinessItem[]; label?: string }) {
  return (
    <ul className="m3-checklist" aria-label={label}>
      {items.map((i) => (
        <li key={i.id} data-done={i.done ? 'true' : undefined}>
          <span className="m3-checklist__mark" aria-hidden="true">
            <Icon name={i.done ? 'check' : 'close'} />
          </span>
          <span>
            {i.label}
            <span className="m3-sr-only">{i.done ? ' (done)' : ' (to do)'}</span>
            {i.hint && !i.done ? <span className="m3-muted"> {i.hint}</span> : null}
          </span>
        </li>
      ))}
    </ul>
  );
}

export interface EditableValueProps {
  label: string;
  value: string;
  onSave: (next: string) => void | Promise<void>;
  /** Validation message for the current draft; blocks saving. */
  validate?: (draft: string) => string | undefined;
}
/**
 * Inline editor for reference-data and configuration values: shows the value
 * with an Edit button; editing swaps in a field with Save and Cancel.
 */
export function EditableValue({ label, value, onSave, validate }: EditableValueProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);
  const error = editing ? validate?.(draft) : undefined;

  if (!editing) {
    return (
      <span className="m3-row">
        <span>{value}</span>
        <IconButton
          icon="edit"
          label={`Edit ${label}`}
          onClick={() => {
            setDraft(value);
            setEditing(true);
          }}
        />
      </span>
    );
  }
  return (
    <form
      className="m3-row"
      onSubmit={async (e) => {
        e.preventDefault();
        if (error) return;
        setSaving(true);
        try {
          await onSave(draft);
          setEditing(false);
        } finally {
          setSaving(false);
        }
      }}
    >
      <TextField label={label} value={draft} errorText={error} onChange={(e) => setDraft(e.target.value)} autoFocus />
      <Button type="submit" variant="filled" size="sm" loading={saving} disabled={Boolean(error)}>
        Save
      </Button>
      <Button size="sm" variant="text" onClick={() => setEditing(false)}>
        Cancel
      </Button>
    </form>
  );
}

export interface SplitLayoutProps {
  main: ReactNode;
  aside: ReactNode;
  asideLabel: string;
  /** Hide the aside (e.g. preview collapsed). */
  asideHidden?: boolean;
}
/** Editor with a sticky side column (live buyer preview). Stacks under 1180px. */
export function SplitLayout({ main, aside, asideLabel, asideHidden }: SplitLayoutProps) {
  return (
    <div className="m3-sheet-split" style={asideHidden ? { gridTemplateColumns: 'minmax(0, 1fr)' } : undefined}>
      <div className="m3-stack">{main}</div>
      {asideHidden ? null : (
        <aside className="m3-sheet-split__side" aria-label={asideLabel}>
          {aside}
        </aside>
      )}
    </div>
  );
}
