'use client';

import type { z } from 'zod';
import { Button, Dialog } from '@pml.tickets/shared/components/m3';
import { CheckboxRHF, Form, SelectRHF, TextAreaRHF, TextFieldRHF, useFormUi, useZodForm } from '@pml.tickets/shared/forms';

export interface CrudField {
  name: string;
  label: string;
  kind?: 'text' | 'textarea' | 'select' | 'check';
  options?: Array<{ value: string; label: string }>;
  placeholder?: string;
  helper?: string;
}

export interface CrudDialogProps<S extends z.ZodType<any, any>> {
  open: boolean;
  title: string;
  schema: S;
  defaults: z.input<S>;
  fields: CrudField[];
  submitLabel: string;
  onClose: () => void;
  /** Throw to show the server refusal on the form. */
  onSubmit: (values: z.output<S>) => Promise<void>;
}

function Actions({ label, onClose }: { label: string; onClose: () => void }) {
  const { submitting } = useFormUi();
  return (
    <div className="m3-row" style={{ justifyContent: 'flex-end' }}>
      <Button type="button" variant="text" onClick={onClose}>
        Cancel
      </Button>
      <Button type="submit" variant="filled" loading={submitting}>
        {label}
      </Button>
    </div>
  );
}

function Inner<S extends z.ZodType<any, any>>({ title, schema, defaults, fields, submitLabel, onClose, onSubmit }: Omit<CrudDialogProps<S>, 'open'>) {
  const form = useZodForm(schema, { defaultValues: defaults as never });
  return (
    <Form
      form={form}
      aria-label={title}
      guardLeave={false}
      onSubmit={async (values) => {
        await onSubmit(values);
      }}
    >
      {fields.map((f) =>
        f.kind === 'check' ? (
          <CheckboxRHF key={f.name} name={f.name} label={f.label} hint={f.helper} />
        ) : f.kind === 'textarea' ? (
          <TextAreaRHF key={f.name} name={f.name} label={f.label} rows={2} helperText={f.helper} />
        ) : f.kind === 'select' ? (
          <SelectRHF key={f.name} name={f.name} label={f.label} options={f.options ?? []} placeholder={f.placeholder} helperText={f.helper} />
        ) : (
          <TextFieldRHF key={f.name} name={f.name} label={f.label} helperText={f.helper} />
        ),
      )}
      <Actions label={submitLabel} onClose={onClose} />
    </Form>
  );
}

/** Create/edit dialog for categories, provinces and cities: react-hook-form + zod. */
export function CrudDialog<S extends z.ZodType<any, any>>({ open, ...rest }: CrudDialogProps<S>) {
  return (
    <Dialog open={open} onClose={rest.onClose} title={rest.title}>
      {open ? <Inner {...rest} /> : null}
    </Dialog>
  );
}
