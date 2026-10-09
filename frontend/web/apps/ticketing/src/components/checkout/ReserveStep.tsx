'use client';

import { Banner, Button } from '@pml.tickets/shared/components/m3';
import { ticketCount, type ReservationRow } from '@pml.tickets/shared';
import { mmss } from '@/lib/format';

/** Step 1: the hold is in place; continue to contact or release it. */
export function ReserveStep({ reservation, eventTitle, now, onContinue, onRelease }: { reservation: ReservationRow; eventTitle: string; now: number; onContinue: () => void; onRelease: () => void }) {
  const n = ticketCount(reservation);
  return (
    <section className="m3-panel m3-stack" aria-labelledby="res-title">
      <h2 className="m3-card__title" id="res-title">
        Your tickets are reserved
      </h2>
      <p className="buyer-lead">
        We are holding {n} ticket{n === 1 ? '' : 's'} for <b>{eventTitle}</b>. Pay within the time shown or they go back on sale.
      </p>
      <Banner tone="info">
        Reservation <b className="m3-mono">{reservation.id}</b> is held for {mmss(Date.parse(reservation.expiresAt) - now)} more. Closing this page does not release it: resume from the top bar.
      </Banner>
      <div className="m3-row">
        <Button variant="accent" onClick={onContinue}>
          Continue
        </Button>
        <Button onClick={onRelease}>Release reservation</Button>
      </div>
    </section>
  );
}
