'use client';

import { Banner, Dialog } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, SelectRHF, TextAreaRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import {
  cancelSchema,
  duplicateSchema,
  rescheduleSchema,
  type CancelValues,
  type DuplicateValues,
  type RescheduleValues,
} from './schemas';

interface Base {
  title: string;
  onClose: () => void;
}

/** Throwing from onSubmit surfaces the server error inside the dialog. */
export function RescheduleDialog({
  title,
  sold,
  currentStart,
  rescheduleLimit,
  onClose,
  onSubmit,
}: Base & { sold: number; currentStart: string; rescheduleLimit: number | null; onSubmit: (v: RescheduleValues) => Promise<void> }) {
  const form = useZodForm(rescheduleSchema(currentStart), { defaultValues: { start: '', reason: '' } });
  return (
    <Dialog open onClose={onClose} title={`Reschedule “${title}”`}>
      <Form form={form} guardLeave={false} aria-label="Reschedule event" onSubmit={onSubmit}>
        <Banner tone="info">
          {sold
            ? `Moving the date is a material change: ${sold} ticket holders are told and can ask for a refund.`
            : 'No tickets sold, so nobody needs to be told.'}{' '}
          {rescheduleLimit != null ? `The platform allows ${rescheduleLimit} ${rescheduleLimit === 1 ? 'reschedule' : 'reschedules'} per event.` : 'The platform limits how many times an event can be rescheduled.'}
        </Banner>
        <TextFieldRHF name="start" label="New start" type="datetime-local" />
        <TextAreaRHF name="reason" label="Reason" rows={2} />
        <FormActions submitLabel="Reschedule" onCancel={onClose} />
      </Form>
    </Dialog>
  );
}

export function CancelEventDialog({
  title,
  sold,
  reasons,
  onClose,
  onSubmit,
}: Base & { sold: number; reasons: readonly string[]; onSubmit: (v: CancelValues) => Promise<void> }) {
  const form = useZodForm(cancelSchema(reasons), { defaultValues: { reason: reasons[0] ?? '', note: '' } });
  return (
    <Dialog open onClose={onClose} title={`Cancel “${title}”?`}>
      <Form form={form} guardLeave={false} aria-label="Cancel event" onSubmit={onSubmit}>
        <p>This cancels the event and refunds all {sold} sold tickets automatically. This cannot be undone.</p>
        <SelectRHF
          name="reason"
          label="Reason"
          options={reasons.map((r) => ({ value: r, label: r }))}
          helperText={reasons.length === 0 ? 'Not available yet: the reason list could not be loaded' : undefined}
        />
        <TextAreaRHF name="note" label="Message to ticket holders" rows={3} helperText="Shown to everyone who holds a ticket." />
        <FormActions submitLabel="Cancel event" cancelLabel="Keep event" onCancel={onClose} danger />
      </Form>
    </Dialog>
  );
}

export function DuplicateEventDialog({
  title,
  onClose,
  onSubmit,
}: Base & { onSubmit: (v: DuplicateValues) => Promise<void> }) {
  const form = useZodForm(duplicateSchema, { defaultValues: { title: `${title} (copy)` } });
  return (
    <Dialog open onClose={onClose} title={`Duplicate “${title}”`}>
      <Form form={form} guardLeave={false} aria-label="Duplicate event" onSubmit={onSubmit}>
        <TextFieldRHF name="title" label="Title of the copy" />
        <FormActions submitLabel="Duplicate" onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
