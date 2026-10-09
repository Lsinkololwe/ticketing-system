'use client';

import { useEffect, useMemo, type ReactNode } from 'react';
import { z } from 'zod';
import { Button, Dialog, TextArea } from '@pml.tickets/shared/components/m3';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';

export interface ReasonDialogProps {
  open: boolean;
  title: string;
  body?: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  /** Label of the text area (default "Reason"). */
  reasonLabel?: string;
  /** When false the comment is optional (platform rule requireComments*). */
  required?: boolean;
  /** Minimum characters when required (default 5). */
  minLength?: number;
  loading?: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}

/** Schema for a written reason; exported so features and tests share the rule. */
export function reasonSchema(required: boolean, minLength: number) {
  return z.object({
    reason: required
      ? z.string().trim().min(minLength, `Give a short reason (at least ${minLength} characters)`)
      : z.string().trim(),
  });
}

/** Dialog that collects a written reason/comment before a consequential action (react-hook-form + zod). */
export function ReasonDialog({
  open,
  title,
  body,
  confirmLabel,
  danger,
  reasonLabel = 'Reason',
  required = true,
  minLength = 5,
  loading,
  onClose,
  onConfirm,
}: ReasonDialogProps) {
  const schema = useMemo(() => reasonSchema(required, minLength), [required, minLength]);
  const form = useZodForm(schema, { defaultValues: { reason: '' } });
  const { register, handleSubmit, reset, formState } = form;

  useEffect(() => {
    if (open) reset({ reason: '' });
  }, [open, reset]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="filled"
            danger={danger}
            loading={loading}
            onClick={handleSubmit((v) => onConfirm(v.reason))}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {body ? <p className="m3-muted">{body}</p> : null}
      <TextArea
        label={required ? reasonLabel : `${reasonLabel} (optional)`}
        rows={3}
        errorText={formState.errors.reason?.message}
        {...register('reason')}
      />
    </Dialog>
  );
}
