'use client';

import { Button, Dialog, GateFallback, QrCode, StatusPill } from '@pml.tickets/shared/components/m3';
import type { MyTicketRow } from '@pml.tickets/shared';
import { clock, fullDate, money } from '@/lib/format';

/** Ticket detail: the QR the gate scans, the ticket code and the ID fallback line. */
export function TicketDialog({ ticket, holder, onClose }: { ticket: MyTicketRow | null; holder: string | null; onClose: () => void }) {
  return (
    <Dialog open={ticket !== null} onClose={onClose} title="Your ticket" actions={<Button variant="text" onClick={onClose}>Close</Button>}>
      {ticket ? (
        <div className="buyer-qrd">
          <div className="m3-row">
            <StatusPill tone="info">{ticket.ticketCategoryName ?? 'Ticket'}</StatusPill>
            <StatusPill status={ticket.status} />
          </div>
          <h3 className="m3-card__title">{ticket.eventTitle}</h3>
          <dl className="m3-kv-grid">
            <div>
              <dt>Date</dt>
              <dd>{ticket.eventDate ? fullDate(ticket.eventDate) : '—'}</dd>
            </div>
            <div>
              <dt>Starts</dt>
              <dd>{ticket.eventDate ? clock(ticket.eventDate) : '—'}</dd>
            </div>
            <div>
              <dt>Venue</dt>
              <dd>{ticket.eventLocationName ?? '—'}</dd>
            </div>
            <div>
              <dt>Price paid</dt>
              <dd className="m3-num">{money(ticket.price)}</dd>
            </div>
            {holder ? (
              <div>
                <dt>Holder</dt>
                <dd>{holder}</dd>
              </div>
            ) : null}
          </dl>
          {ticket.qrCode ? <QrCode value={ticket.qrCode} label={`QR code for ticket ${ticket.ticketNumber}`} /> : <p className="m3-muted" role="status">The QR code is not ready yet. Check back in a minute.</p>}
          <GateFallback code={ticket.ticketNumber} />
          <p className="m3-muted">Show this code at the gate. It admits one person, once.</p>
        </div>
      ) : null}
    </Dialog>
  );
}
