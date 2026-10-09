'use client';

import { enumValues, HOLDER_SEGMENT_LABELS } from '@/lib/format/enumLabels';
import { useState } from 'react';
import { z } from 'zod';
import { Banner, Card, CardHeader, DataTable, EmptyState, ErrorState } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, SelectRHF, TextAreaRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import type { HolderSegment } from '@pml.tickets/shared/types/graphql';
import { useHolderAudience, useHolderMessages, useMessageHolders } from '@/lib/api/event-insights';
import { formatDateTime } from '@/lib/bookings/format';
import { Status } from '@/components/console/Status';

const SEGMENTS = enumValues(HOLDER_SEGMENT_LABELS).map((value) => ({ value, label: HOLDER_SEGMENT_LABELS[value] }));

const schema = z.object({
  subject: z.string().trim().min(3, 'Enter a subject of at least 3 characters').max(120, 'Use at most 120 characters'),
  body: z.string().trim().min(10, 'Write a message of at least 10 characters').max(1000, 'Use at most 1000 characters'),
  segment: z.string().refine((v): v is HolderSegment => v in HOLDER_SEGMENT_LABELS, { error: 'Choose who to send to' }),
  ticketTierId: z.string(),
});

export interface NotifyTabProps {
  eventId: string;
  tiers: Array<{ id: string; name: string }>;
  canNotify: boolean;
  onSent?: (recipients: number) => void;
}

/** Send an update to the people who hold a ticket for this event, and see what was sent before. */
export function NotifyTab({ eventId, tiers, canNotify, onSent }: NotifyTabProps) {
  const form = useZodForm(schema, { defaultValues: { subject: '', body: '', segment: 'ALL', ticketTierId: '' } });
  const segment = (form.watch('segment') ?? 'ALL') as HolderSegment;
  const tierId = (form.watch('ticketTierId') as string | undefined) || null;
  const audience = useHolderAudience(eventId, segment, tierId);
  const history = useHolderMessages(eventId);
  const { send } = useMessageHolders(eventId);
  const [sent, setSent] = useState<string | null>(null);

  return (
    <div className="m3-stack">
      <Card>
        <CardHeader title="Notify attendees" subtitle="Send an update to everyone who holds a ticket for this event." />
        {!canNotify ? <Banner tone="info">Your role cannot message attendees.</Banner> : null}
        {sent ? <Banner tone="success">{sent}</Banner> : null}
        <Form
          form={form}
          aria-label="Notify attendees"
          guardLeave={false}
          disabled={!canNotify}
          onSubmit={async (v) => {
            const res = await send({ subject: v.subject, body: v.body, segment: v.segment, ticketTierId: v.ticketTierId || null });
            const n = res.data?.messageTicketHolders.recipientCount ?? 0;
            setSent(`Message sent to ${n} ${n === 1 ? 'person' : 'people'}.`);
            form.reset({ subject: '', body: '', segment: v.segment, ticketTierId: v.ticketTierId });
            onSent?.(n);
          }}
        >
          <SelectRHF name="segment" label="Send to" options={SEGMENTS} />
          <SelectRHF name="ticketTierId" label="Ticket tier" placeholder="All tiers" options={tiers.map((t) => ({ value: t.id, label: t.name }))} />
          <p className="m3-muted" aria-live="polite">
            {audience.error ? 'Not available yet: the audience size could not be loaded.' : audience.count == null ? 'Counting the audience…' : `${audience.count} ${audience.count === 1 ? 'person' : 'people'} will receive this.`}
          </p>
          <TextFieldRHF name="subject" label="Subject" maxLength={120} />
          <TextAreaRHF name="body" label="Message" rows={5} maxLength={1000} />
          <FormActions submitLabel="Send message" submittingLabel="Sending…" align="start" />
        </Form>
      </Card>
      <Card>
        <CardHeader title="Sent messages" />
        <DataTable
          caption="Sent messages"
          rows={history.messages}
          getRowId={(m) => m.id}
          loading={history.loading && history.messages.length === 0}
          error={history.error && history.messages.length === 0 ? <ErrorState error={history.error} onRetry={() => void history.refetch()} variant="inline" /> : undefined}
          empty={<EmptyState icon="mail" title="No messages sent yet." />}
          columns={[
            { id: 'subject', header: 'Subject', rowHeader: true, cell: (m) => m.subject },
            { id: 'to', header: 'Delivered', align: 'end', cell: (m) => `${m.deliveredCount} of ${m.recipientCount}` },
            { id: 'status', header: 'Status', cell: (m) => <Status status={m.status} /> },
            { id: 'at', header: 'Sent', cell: (m) => formatDateTime(m.createdAt) },
          ]}
        />
      </Card>
    </div>
  );
}
