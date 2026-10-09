'use client';

import { useEffect, useRef, useState } from 'react';
import { useController } from 'react-hook-form';
import { richToText, richView, sanitizeRich } from '@pml.tickets/shared';

/** Toolbar of the design: bold, italic, underline, heading, lists, quote, clear. */
const BUTTONS: Array<[command: string, label: string, title: string]> = [
  ['bold', 'B', 'Bold'],
  ['italic', 'I', 'Italic'],
  ['underline', 'U', 'Underline'],
  ['formatBlock:h3', 'H', 'Heading'],
  ['insertUnorderedList', '• List', 'Bulleted list'],
  ['insertOrderedList', '1. List', 'Numbered list'],
  ['formatBlock:blockquote', '❝', 'Quote'],
  ['removeFormat', 'Clear', 'Clear formatting'],
];

export interface RichTextFieldProps {
  name: string;
  label: string;
  helperText?: string;
  /** Visible character budget of the plain text (not enforced by the editor; the schema enforces it). */
  maxLength?: number;
  rows?: number;
  disabled?: boolean;
}

/**
 * Rich text field bound to react-hook-form. The stored value is the sanitised HTML subset defined in
 * `@pml.tickets/shared` (`sanitizeRich`): the editable surface is re-sanitised on every input and pasted
 * content is reduced to plain text, so no markup other than the allowed subset can reach the form value.
 */
export function RichTextField({ name, label, helperText = 'Headings, lists, bold and italic are supported.', maxLength, rows = 5, disabled }: RichTextFieldProps) {
  const { field, fieldState } = useController({ name });
  const surface = useRef<HTMLDivElement>(null);
  const emitted = useRef<string | null>(null);
  const [preview, setPreview] = useState(false);
  const value = String(field.value ?? '');

  // Push the form value into the surface only when it changed from outside (reset, load), never while typing.
  useEffect(() => {
    const el = surface.current;
    if (!el || emitted.current === value) return;
    el.innerHTML = richView(value);
    emitted.current = value;
  }, [value, preview]);

  const sync = () => {
    const el = surface.current;
    if (!el) return;
    const clean = sanitizeRich(el.innerHTML);
    emitted.current = clean;
    field.onChange(clean);
  };
  const run = (cmd: string) => {
    const el = surface.current;
    if (!el || preview || disabled) return;
    el.focus();
    const [c, arg] = cmd.split(':');
    document.execCommand(c, false, arg ?? undefined);
    sync();
  };
  const count = richToText(value).length;
  const id = `rt-${name.replace(/\W/g, '-')}`;

  return (
    <div className="oc-rich-field">
      <span className="oc-rich-label" id={`${id}-label`}>{label}</span>
      <div className="oc-rich" data-disabled={disabled ? 'true' : undefined} data-invalid={fieldState.error ? 'true' : undefined}>
        <div className="oc-rich__bar" role="toolbar" aria-label={`Formatting for ${label}`}>
          {disabled
            ? null
            : BUTTONS.map(([cmd, text, title]) => (
                <button key={cmd} type="button" className="oc-rich__btn" aria-label={title} title={title} disabled={preview} onMouseDown={(e) => e.preventDefault()} onClick={() => run(cmd)}>
                  {text}
                </button>
              ))}
          <span className="oc-rich__spacer" />
          <button type="button" className="oc-rich__btn" aria-pressed={preview} onClick={() => setPreview((p) => !p)}>
            {preview ? 'Edit' : 'Preview'}
          </button>
        </div>
        {preview ? (
          // Rebuilt from escaped text and bare allowed tags by sanitizeRich/richView.
          <div className="oc-rich__surface" aria-live="polite" dangerouslySetInnerHTML={{ __html: richView(value) || '<p class="m3-muted">Nothing to preview yet.</p>' }} />
        ) : (
          <div
            ref={surface}
            className="oc-rich__surface"
            role="textbox"
            aria-multiline="true"
            aria-labelledby={`${id}-label`}
            aria-invalid={fieldState.error ? true : undefined}
            contentEditable={!disabled}
            suppressContentEditableWarning
            style={{ minHeight: `calc(var(--m3-sp-24) * ${rows})` }}
            onInput={sync}
            onBlur={field.onBlur}
            onPaste={(e) => {
              // Plain text only: pasted markup would bypass the toolbar's contract.
              e.preventDefault();
              document.execCommand('insertText', false, e.clipboardData.getData('text/plain'));
            }}
          />
        )}
      </div>
      <div className="oc-rich-help">
        <span className="m3-muted">{helperText}</span>
        {maxLength ? <span className="m3-muted">{count}/{maxLength}</span> : null}
      </div>
      {fieldState.error?.message ? <p role="alert" className="oc-rich-error">{fieldState.error.message}</p> : null}
    </div>
  );
}
