'use client';

import { z } from 'zod';
import { Banner, Button, Card, CardHeader } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import type { OrganizerIncomingTransfersQuery } from '@pml.tickets/shared/types/graphql';
import { formatEventDate } from '@/lib/format/figure';

type Incoming = OrganizerIncomingTransfersQuery['myPendingOwnershipTransfers'][number];

const schema = z.object({
  token: z.string().trim().min(8, 'Paste the transfer token from your link'),
  code: z.string().trim().regex(/^\d{6}$/, 'Enter the 6-digit code'),
});

export interface IncomingTransferCardProps {
  transfers: Incoming[];
  /** Sends the one-time code to the nominee's verified phone. */
  onRequestCode: (token: string) => Promise<unknown>;
  /** Reject to map a server error (wrong or expired code) onto the form. */
  onAccept: (token: string, code: string) => Promise<unknown>;
  onDecline: (token: string) => Promise<unknown> | void;
}

/** Second step of an ownership transfer, shown to the person who was nominated. */
export function IncomingTransferCard({ transfers, onRequestCode, onAccept, onDecline }: IncomingTransferCardProps) {
  const form = useZodForm(schema, { defaultValues: { token: '', code: '' } });
  if (transfers.length === 0) return null;
  const t = transfers[0]!;
  return (
    <Card>
      <CardHeader title="Ownership offered to you" />
      <Banner tone="info" title={`${t.currentOwner?.fullName ?? 'The owner'} wants you to own ${t.organization?.name ?? 'the organization'}.`}>
        Offer expires {formatEventDate(t.expiresAt)}. Open the link you were sent, paste its token below and confirm with the one-time code.
      </Banner>
      <Form form={form} aria-label="Accept ownership" guardLeave={false} fieldMap={{ confirmationCode: 'code', token: 'token' }} onSubmit={async (v) => { await onAccept(v.token, v.code); }}>
        <TextFieldRHF name="token" label="Transfer token" />
        <div className="m3-row">
          <Button variant="outlined" onClick={() => { const v = form.getValues('token'); if (v) void onRequestCode(v); else form.setError('token', { message: 'Paste the transfer token from your link' }); }}>
            Send me a code
          </Button>
        </div>
        <TextFieldRHF name="code" label="One-time code" inputMode="numeric" maxLength={6} />
        <FormActions submitLabel="Accept ownership" align="start" leading={<Button variant="text" danger onClick={() => { const v = form.getValues('token'); if (v) void onDecline(v); }}>Decline</Button>} />
      </Form>
    </Card>
  );
}
