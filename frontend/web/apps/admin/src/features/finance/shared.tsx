'use client';

import { useCallback, useMemo, useRef, type ReactNode } from 'react';
import { z } from 'zod';
import { Form } from '@pml.tickets/shared/forms/Form';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { Button, Dialog, Select, TextArea, TextField, useSnackbar } from '@pml.tickets/shared/components/m3';
import type { DecisionResult } from '@pml.tickets/shared/api/admin/modules/finance';
import { csvText } from '@/lib/format';

/** Runs a mutation promise, then shows the success or failure message. */
export function useOpRunner() {
  const { show } = useSnackbar();
  return useCallback(
    async (promise: Promise<DecisionResult>, okMessage: string, after?: () => void): Promise<boolean> => {
      const res = await promise;
      if (res.success) {
        show(okMessage);
        after?.();
      } else {
        show(res.message ?? 'That did not work. Try again.');
      }
      return res.success;
    },
    [show]
  );
}

/** Copy a table to the clipboard as CSV (client side, from the loaded rows). */
export function useCopyCsv() {
  const { show } = useSnackbar();
  return useCallback(
    async (rows: Array<Array<string | number | null | undefined>>) => {
      try {
        await navigator.clipboard.writeText(csvText(rows));
        show('CSV copied');
      } catch {
        show('Could not copy. Allow clipboard access and try again.');
      }
    },
    [show]
  );
}

export const asNumber = (v: number | string | null | undefined): number => Number(v ?? 0);

export function Mono({ children }: { children: ReactNode }) {
  return <span className="m3-mono">{children}</span>;
}

/** Two-line table cell: main text and a muted second line. */
export function TwoLine({ main, sub, monoSub }: { main: ReactNode; sub?: ReactNode; monoSub?: boolean }) {
  return (
    <>
      {main}
      {sub ? (
        <>
          <br />
          <span className={monoSub ? 'm3-muted m3-mono' : 'm3-muted'}>{sub}</span>
        </>
      ) : null}
    </>
  );
}

export interface FormField {
  id: string;
  label: string;
  type?: 'text' | 'number' | 'date' | 'textarea' | 'select';
  value?: string;
  required?: boolean;
  minLength?: number;
  pattern?: RegExp;
  patternMessage?: string;
  min?: number;
  options?: Array<{ value: string; label: string }>;
  helper?: string;
}

export interface FormDialogProps {
  open: boolean;
  title: string;
  body?: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  fields: FormField[];
  /** Cross-field validation: return a message per field id. */
  validate?: (values: Record<string, string>) => Record<string, string>;
  onClose: () => void;
  /** Return the mutation result; the dialog closes on success and shows the error otherwise. */
  onSubmit: (values: Record<string, string>) => Promise<DecisionResult>;
  onDone?: () => void;
  successMessage: string;
}

const initial = (fields: FormField[]) => Object.fromEntries(fields.map((f) => [f.id, f.value ?? f.options?.[0]?.value ?? '']));

/** zod schema for a field list (react-hook-form + zod, per the forms kit). */
export function schemaFor(fields: FormField[], validate?: FormDialogProps['validate']) {
  const shape: Record<string, z.ZodType<string>> = {};
  for (const f of fields) {
    shape[f.id] = z
      .string()
      .trim()
      .superRefine((v, ctx) => {
        const bad = (message: string) => ctx.addIssue({ code: 'custom', message });
        if (v === '') {
          if (f.required) bad(`${f.label} is required`);
          return;
        }
        if (f.minLength && v.length < f.minLength) bad(`Give a short ${f.label.toLowerCase()} (at least ${f.minLength} characters)`);
        else if (f.pattern && !f.pattern.test(v)) bad(f.patternMessage ?? `Check ${f.label.toLowerCase()}`);
        else if (f.type === 'number' && (Number.isNaN(Number(v)) || (f.min !== undefined && Number(v) < f.min))) bad(`Enter ${f.label.toLowerCase()} of at least ${f.min ?? 0}`);
      });
  }
  return z.object(shape).superRefine((vals, ctx) => {
    const extra = validate ? validate(vals as Record<string, string>) : {};
    for (const [path, message] of Object.entries(extra)) ctx.addIssue({ code: 'custom', message, path: [path] });
  });
}

function FormDialogBody({ title, body, confirmLabel, danger, fields, validate, onClose, onSubmit, onDone, successMessage }: Omit<FormDialogProps, 'open'>) {
  const { show } = useSnackbar();
  const wrap = useRef<HTMLDivElement>(null);
  const schema = useMemo(() => schemaFor(fields, validate), [fields, validate]);
  const form = useZodForm(schema, { defaultValues: initial(fields) as never });
  const submitting = form.formState.isSubmitting;
  return (
    <Dialog
      open
      onClose={onClose}
      title={title}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="filled" danger={danger} loading={submitting} onClick={() => wrap.current?.querySelector('form')?.requestSubmit()}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div ref={wrap}>
        {body ? <p className="m3-muted">{body}</p> : null}
        <Form
          form={form}
          guardLeave={false}
          aria-label={title}
          onSubmit={async (values) => {
            const res = await onSubmit(values as Record<string, string>);
            if (res.success) {
              show(successMessage);
              onDone?.();
              onClose();
            } else {
              form.setError('root.server', { message: res.message ?? 'That did not work. Try again.' });
            }
          }}
        >
          {fields.map((f) => {
            const err = (form.formState.errors as Record<string, { message?: string } | undefined>)[f.id]?.message;
            const common = { label: f.label, helperText: f.helper, errorText: err, ...form.register(f.id as never) };
            if (f.type === 'textarea') return <TextArea key={f.id} {...common} rows={3} />;
            if (f.type === 'select')
              return (
                <Select key={f.id} {...common}>
                  {(f.options ?? []).map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Select>
              );
            return <TextField key={f.id} {...common} type={f.type ?? 'text'} />;
          })}
        </Form>
      </div>
    </Dialog>
  );
}

/** Small form dialog used by the finance actions that need typed input (react-hook-form + zod). */
export function FormDialog({ open, ...rest }: FormDialogProps) {
  return open ? <FormDialogBody {...rest} /> : null;
}
