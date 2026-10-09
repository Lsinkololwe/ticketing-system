'use client';

import { useRef, type ReactNode } from 'react';
import { useFormContext, type FieldValues, type UseFormReturn } from 'react-hook-form';
import { Form, useFormUi } from '@pml.tickets/shared';
import { SaveBar, useSnackbar } from '@pml.tickets/shared/components/m3';

/** Unsaved-changes bar bound to the surrounding Form: Save submits it, Discard resets it. */
export function FormSaveBar({ hidden }: { hidden?: boolean }) {
  const marker = useRef<HTMLSpanElement>(null);
  const { reset } = useFormContext();
  const { submitting, dirty } = useFormUi();
  return (
    <>
      <span ref={marker} hidden />
      <SaveBar
        hidden={hidden || !dirty}
        message="You have unsaved changes"
        saving={submitting}
        onSave={() => marker.current?.closest('form')?.requestSubmit()}
        onDiscard={() => reset()}
      />
    </>
  );
}

interface SettingsFormProps<TIn extends FieldValues, TOut> {
  form: UseFormReturn<TIn, any, TOut>;
  onSubmit: (values: TOut) => void | Promise<void>;
  label: string;
  testId: string;
  readOnly?: boolean;
  children: ReactNode;
}

/** Form + snackbar notifications + save bar, shared by the settings sections. */
export function SettingsForm<TIn extends FieldValues, TOut>({ form, onSubmit, label, testId, readOnly, children }: SettingsFormProps<TIn, TOut>) {
  const snackbar = useSnackbar();
  return (
    <div data-testid={testId}>
      <Form
        form={form}
        onSubmit={(v) => onSubmit(v)}
        notify={(t) => snackbar.show({ message: t.message, tone: t.tone })}
        disabled={readOnly}
        aria-label={label}
      >
        {children}
        <FormSaveBar hidden={readOnly} />
      </Form>
    </div>
  );
}
