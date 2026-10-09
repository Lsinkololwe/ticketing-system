'use client';

import { useState } from 'react';
import { z } from 'zod';
import { Banner, Button, Card, CardHeader, Dialog } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, TextFieldRHF, useZodForm } from '@pml.tickets/shared';

export interface DangerSectionProps {
  organizationName: string;
  /** Only the owner may start or cancel a deletion. */
  isOwner: boolean;
  /** Set while a deletion request is open (ISO end of the grace period). */
  scheduledFor?: string | null;
  /** Live events with sales + open payouts block deletion; null when the console cannot tell. */
  blockers?: { liveEventsWithSales: number; openPayouts: number } | null;
  /** Throw to map a server refusal onto the dialog. */
  onRequestDeletion: (reason: string) => Promise<unknown>;
  onCancelDeletion: () => Promise<unknown>;
}

const date = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

function ConfirmDeletionDialog({ name, onConfirm, onClose }: { name: string; onConfirm: (reason: string) => Promise<unknown>; onClose: () => void }) {
  const schema = z.object({
    name: z.string().refine((v) => v.trim() === name, { error: 'Type the exact organization name' }),
    reason: z.string().trim().max(500, 'Use at most 500 characters'),
  });
  const form = useZodForm(schema, { defaultValues: { name: '', reason: '' } });
  return (
    <Dialog open onClose={onClose} title="Delete organization?">
      <Form
        form={form}
        aria-label="Delete organization"
        onSubmit={async (v) => {
          await onConfirm(v.reason);
          onClose();
        }}
      >
        <p>
          Type <b>{name}</b> to confirm. It moves to Pending deletion for 90 days.
        </p>
        <TextFieldRHF name="name" label="Organization name" />
        <TextFieldRHF name="reason" label="Reason (optional)" />
        <FormActions submitLabel="Delete organization" submittingLabel="Deleting…" danger onCancel={onClose} />
      </Form>
    </Dialog>
  );
}

export function DangerSection({ organizationName, isOwner, scheduledFor, blockers, onRequestDeletion, onCancelDeletion }: DangerSectionProps) {
  const [open, setOpen] = useState(false);
  const blocked = Boolean(blockers && (blockers.liveEventsWithSales || blockers.openPayouts));
  return (
    <>
      <Card>
        <CardHeader title="Delete organization" subtitle="The organization moves to Pending deletion for 90 days. Nothing is removed until then, and you can cancel any time before." />
        {scheduledFor ? (
          <Banner
            tone="warning"
            title="Organization scheduled for deletion."
            actions={isOwner ? <Button size="sm" variant="tonal" onClick={() => void onCancelDeletion()}>Cancel deletion</Button> : null}
          >
            It will be permanently removed on {date(scheduledFor)}. Events and payouts are frozen.
          </Banner>
        ) : null}
        {blocked && blockers ? (
          <Banner tone="error" urgent>
            Cannot start deletion: {blockers.liveEventsWithSales ? `${blockers.liveEventsWithSales} live event(s) have ticket sales. ` : ''}
            {blockers.openPayouts ? `${blockers.openPayouts} payout request(s) are open. ` : ''}Cancel events and finish payouts first.
          </Banner>
        ) : null}
        {!isOwner ? <p className="m3-muted">Only the owner can delete the organization.</p> : null}
        <div className="oc-section">
          <Button variant="filled" danger disabled={blocked || !isOwner || Boolean(scheduledFor)} onClick={() => setOpen(true)}>
            Delete organization
          </Button>
        </div>
      </Card>
      {open ? <ConfirmDeletionDialog name={organizationName} onConfirm={onRequestDeletion} onClose={() => setOpen(false)} /> : null}
    </>
  );
}
