'use client';

import { useEffect, useState } from 'react';
import { z } from 'zod';
import { Button, Dialog } from '@pml.tickets/shared/components/m3';
import { resolveServerError, useTicketTransferActions, useTransferRecipient, type MyTicketRow } from '@pml.tickets/shared';
import { Form, FormActions, TextFieldRHF, useZodForm } from '@pml.tickets/shared/forms';
import { fmtZmPhone, parseZmMobile } from '@/lib/format';

const schema = z.object({ phone: z.string().trim().min(1, 'Enter a valid Zambian mobile number, for example 96 123 4567.') });

/** Transfer a ticket: 1 recipient's mobile number, 2 confirm. The recipient must accept; until then the ticket stays with the sender. */
export function TransferDialog({ ticket, ownPhone, onClose, onDone }: { ticket: MyTicketRow | null; ownPhone?: string | null; onClose: () => void; onDone: () => void }) {
  const form = useZodForm(schema, { defaultValues: { phone: '' } });
  const recipient = useTransferRecipient();
  const actions = useTicketTransferActions();
  const [to, setTo] = useState<{ value: string; shown: string; name: string | null } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (ticket) {
      form.reset({ phone: '' });
      setTo(null);
      setError(null);
    }
    // reset only when a different ticket opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticket?.id]);

  const lookup = async ({ phone }: { phone: string }) => {
    setError(null);
    const d = parseZmMobile(phone);
    if (!d) return form.setError('phone', { message: 'Enter a valid Zambian mobile number, for example 96 123 4567.' });
    const shown = fmtZmPhone(d);
    if (ownPhone && shown.replace(/\s/g, '') === ownPhone.replace(/\s/g, '')) return form.setError('phone', { message: 'You cannot transfer a ticket to yourself.' });
    try {
      const r = await recipient.lookup('WHATSAPP', `+260${d}`);
      if (!r) return form.setError('phone', { message: 'There is no Showstop account for that number. Ask them to sign in once, then try again.' });
      setTo({ value: `+260${d}`, shown, name: r.displayName ?? null });
    } catch (e) {
      setError(resolveServerError(e).message);
    }
  };

  const send = async () => {
    if (!ticket || !to) return;
    setError(null);
    try {
      await actions.initiate(ticket.id, 'WHATSAPP', to.value);
      onDone();
    } catch (e) {
      setError(resolveServerError(e).message);
    }
  };

  return (
    <Dialog
      open={ticket !== null}
      onClose={onClose}
      title="Transfer ticket"
      actions={
        to ? (
          <>
            <Button variant="text" onClick={() => setTo(null)}>Back</Button>
            <Button variant="filled" loading={actions.busy} onClick={() => void send()}>Transfer ticket</Button>
          </>
        ) : undefined
      }
    >
      {ticket ? (
        to ? (
          <div className="m3-stack">
            <p>
              Transfer <b>{ticket.ticketCategoryName}</b> ticket <b className="m3-mono">{ticket.ticketNumber}</b> to <b>{to.name ? `${to.name} (${to.shown})` : to.shown}</b>?
            </p>
            <ul className="buyer-plain">
              <li>They must accept the transfer first. Until then the ticket stays yours and you can cancel the transfer.</li>
              <li>Once they accept, the ticket is theirs. Refunds still go to whoever paid.</li>
            </ul>
            {error ? <p role="alert" className="buyer-err">{error}</p> : null}
          </div>
        ) : (
          <Form form={form} guardLeave={false} onSubmit={lookup}>
            <p className="m3-muted">
              Send {ticket.ticketCategoryName} ticket <b className="m3-mono">{ticket.ticketNumber}</b> to another Showstop user. They must have a Showstop account.
            </p>
            <TextFieldRHF name="phone" label="Recipient's mobile number" prefix="+260" inputMode="tel" autoComplete="off" />
            {error ? <p role="alert" className="buyer-err">{error}</p> : null}
            <FormActions submitLabel="Continue" submittingLabel="Checking…" cancelLabel="Cancel" onCancel={onClose} />
          </Form>
        )
      ) : null}
    </Dialog>
  );
}
