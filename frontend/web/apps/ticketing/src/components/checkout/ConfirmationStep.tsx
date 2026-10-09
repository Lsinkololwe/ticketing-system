'use client';

import { QrCode, StatusPill, WalletTicketCard } from '@pml.tickets/shared/components/m3';
import { LinkBtn } from '@/components/LinkBtn';
import type { MyTicketRow } from '@pml.tickets/shared';
import type { EventPageRow } from '@pml.tickets/shared';
import { clock, fullDate, money } from '@/lib/format';
import { calendarHref } from '@/components/events/EventHero';
import { useMobileOperators } from '@/hooks/useMobileOperators';

/** Step 5: booking confirmed, the buyer's tickets with their QR codes, and what happens next. */
export function ConfirmationStep({
  reservationId,
  bookingNumber,
  total,
  provider,
  phone,
  event,
  tickets,
  holder,
}: {
  reservationId: string;
  /** The booking's own number, once the booking exists. */
  bookingNumber: string | null;
  total: string | number;
  provider: string | null;
  phone: string | null;
  event: EventPageRow | null;
  tickets: MyTicketRow[];
  holder: string | null;
}) {
  const { labelOf } = useMobileOperators();
  return (
    <div className="m3-stack">
      <section className="buyer-ok-head" aria-labelledby="ok-title">
        <StatusPill tone="success">Confirmed</StatusPill>
        <h2 id="ok-title" className="m3-page-title">
          Your booking is confirmed!
        </h2>
        <p className="buyer-lead">
          {bookingNumber ? 'Booking number' : 'Reservation'} <b className="m3-mono">{bookingNumber ?? reservationId}</b> · {money(total)} paid{provider ? ` with ${labelOf(provider)}` : ''}
        </p>
        <p className="m3-muted">We have sent your tickets to {phone ?? 'your verified contact'} by message. You can always find them in My tickets.</p>
      </section>
      <h3 className="m3-card__title">Your tickets{event ? ` for ${event.title}` : ''}</h3>
      {tickets.length ? (
        <div className="buyer-tix">
          {tickets.map((t) => (
            <WalletTicketCard
              key={t.id}
              eventTitle={t.eventTitle}
              dateLine={t.eventDate ? `${fullDate(t.eventDate)} · ${clock(t.eventDate)}` : ''}
              venue={t.eventLocationName ?? ''}
              tier={t.ticketCategoryName ?? 'Ticket'}
              holder={holder ?? undefined}
              code={t.ticketNumber}
              qr={t.qrCode ? <QrCode value={t.qrCode} label={`QR code for ticket ${t.ticketNumber}`} /> : undefined}
            />
          ))}
        </div>
      ) : (
        <p className="m3-muted" role="status">
          Your tickets are being issued. They appear in My tickets within a minute.
        </p>
      )}
      <section className="m3-panel">
        <h3 className="m3-card__title">What happens next</h3>
        <ol className="buyer-next">
          <li>
            <b>Check your phone</b>
            <span>Your tickets arrive by message. They are also saved in My tickets.</span>
          </li>
          <li>
            <b>Get there early</b>
            <span>Allow time for security checks at the gate.</span>
          </li>
          <li>
            <b>Show your QR code</b>
            <span>Staff scan one code per ticket at the gate. Turn your screen brightness up.</span>
          </li>
        </ol>
      </section>
      <div className="m3-row buyer-center">
        <LinkBtn href="/my-tickets" variant="accent">
          View my tickets
        </LinkBtn>
        {event ? (
          <a className="m3-btn m3-state" data-variant="outlined" target="_blank" rel="noopener noreferrer" href={calendarHref(event)}>
            Add to calendar
          </a>
        ) : null}
        <LinkBtn href="/" variant="outlined">
          Back to events
        </LinkBtn>
      </div>
    </div>
  );
}
