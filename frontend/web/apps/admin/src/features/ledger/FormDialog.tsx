'use client';

import type { ReactNode } from 'react';
import type { FieldValues } from 'react-hook-form';
import { Dialog } from '@pml.tickets/shared/components/m3';
import { Form, FormActions } from '@pml.tickets/shared/forms/Form';
import type { ZodForm } from '@pml.tickets/shared/forms/useZodForm';
import type { z } from 'zod';

export interface FormDialogProps<S extends z.ZodType<any, any>> {
  title: string;
  form: ZodForm<S>;
  onSubmit: (values: z.output<S>) => void | Promise<void>;
  onClose: () => void;
  submitLabel: string;
  danger?: boolean;
  wide?: boolean;
  children: ReactNode;
}

/** Dialog whose body is a react-hook-form `<Form>` with Cancel and submit actions. Mount it only while open so each opening starts clean. */
export function FormDialog<S extends z.ZodType<any, any>>({ title, form, onSubmit, onClose, submitLabel, danger, wide, children }: FormDialogProps<S>) {
  return (
    <Dialog open onClose={onClose} title={title} wide={wide}>
      <Form<FieldValues, z.output<S>> form={form as never} onSubmit={onSubmit as never} guardLeave={false} aria-label={title}>
        {children}
        <FormActions submitLabel={submitLabel} onCancel={onClose} danger={danger} />
      </Form>
    </Dialog>
  );
}
