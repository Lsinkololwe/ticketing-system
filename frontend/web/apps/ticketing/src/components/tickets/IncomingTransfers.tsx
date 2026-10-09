'use client';

import { useState } from 'react';
import { Button, useSnackbar } from '@pml.tickets/shared/components/m3';
import { resolveServerError, useTicketTransferActions, type TicketTransferRow } from '@pml.tickets/shared';
import { clock, fullDate } from '@/lib/format';

/** One offer made to the buyer, with Accept and Decline. Only the recipient can act on it. */
export function IncomingTransferCard({ transfer: t, onSettled }: { transfer: TicketTransferRow; onSettled?: (accepted: boolean) => void }) {
  const snack = useSnackbar();
  const actions = useTicketTransferActions();
  const [error, setError] = useState<string | null>(null);
  const run = async (accept: boolean) => {
    setError(null);
    try {
      if (accept) await actions.accept(t.id);
      else await actions.decline(t.id);
      snack.show(accept ? 'Transfer accepted. The ticket is now in My tickets.' : 'Transfer declined');
      onSettled?.(accept);
    } catch (e) {
      setError(resolveServerError(e).message);
    }
  };
  return (
    <article className="m3-panel buyer-bk" aria-label={`Transfer of ticket ${t.ticketNumber}`}>
      <header className="buyer-bk__head">
        <div>
          <span className="m3-muted m3-mono">{t.ticketNumber}</span>
          <h3 className="m3-card__title">{t.eventTitle ?? 'Ticket transfer'}</h3>
          <div className="m3-muted">
            {t.fromDisplayName ? `${t.fromDisplayName} wants to send you this ticket.` : 'Someone wants to send you this ticket.'}
            {t.expiresAt ? ` Offer ends ${fullDate(t.expiresAt)}, ${clock(t.expiresAt)}.` : ''}
          </div>
          {t.note ? <p className="m3-muted">&ldquo;{t.note}&rdquo;</p> : null}
        </div>
      </header>
      {error ? <p role="alert" className="buyer-err">{error}</p> : null}
      <footer className="buyer-bk__foot">
        <span className="m3-muted">Until you accept, the ticket stays with the sender.</span>
        <div className="m3-row">
          <Button loading={actions.busy} onClick={() => void run(false)}>Decline</Button>
          <Button variant="filled" loading={actions.busy} onClick={() => void run(true)}>Accept ticket</Button>
        </div>
      </footer>
    </article>
  );
}
