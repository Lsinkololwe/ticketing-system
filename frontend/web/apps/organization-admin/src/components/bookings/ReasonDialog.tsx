'use client';

import type { ReactNode } from 'react';
import { Dialog } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, TextAreaRHF, useZodForm } from '@pml.tickets/shared';
import { reasonSchema } from './schemas';

export interface ReasonDialogProps {
  open: boolean;
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  danger?: boolean;
  onClose: () => void;
  /** Reject to show a server error inside the dialog. */
  onConfirm: (reason: string) => void | Promise<void>;
}

function ReasonForm(p: ReasonDialogProps) {
  const form = useZodForm(reasonSchema, { defaultValues: { reason: '' } });
  return (
    <Form form={form} onSubmit={(v) => p.onConfirm(v.reason)} guardLeave={false} aria-label={p.title}>
      <div className="m3-stack">
        {p.description ? <p>{p.description}</p> : null}
        <TextAreaRHF name="reason" label="Reason" rows={3} />
        <FormActions submitLabel={p.confirmLabel} cancelLabel={p.cancelLabel} onCancel={p.onClose} danger={p.danger} />
      </div>
    </Form>
  );
}

/** Asks for a required reason (min 3 characters) before a destructive action. */
export function ReasonDialog(p: ReasonDialogProps) {
  return (
    <Dialog open={p.open} onClose={p.onClose} title={p.title}>
      {p.open ? <ReasonForm {...p} /> : null}
    </Dialog>
  );
}
