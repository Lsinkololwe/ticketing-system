'use client';

import { SummaryLine } from '@pml.tickets/shared/components/m3';
import type { EventPageRow } from '@pml.tickets/shared';
import { subtotalOf, type ReservationRow } from '@pml.tickets/shared';
import { money, mmss } from '@/lib/format';
import { MiniEvent } from '@/components/events/OrderPanel';

/** "Order summary" panel: event, held tier lines, discount and the total to pay, with the live hold timer. */
export function SummaryAside({ reservation, event, now, showHold }: { reservation: ReservationRow; event: EventPageRow | null; now: number; showHold: boolean }) {
  const sub = subtotalOf(reservation);
  const discount = Number(reservation.discountAmount ?? 0);
  return (
    <aside className="m3-panel buyer-side" aria-label="Order summary">
      {event ? <MiniEvent event={event} /> : null}
      {showHold ? (
        <div className="buyer-hold" role="timer">
          Tickets held for <b>{mmss(Date.parse(reservation.expiresAt) - now)}</b>
        </div>
      ) : null}
      <h2 className="m3-card__title">Order summary</h2>
      <div>
        {reservation.items.map((i) => (
          <SummaryLine key={i.ticketTierId} label={`${i.tierName} × ${i.quantity}`} value={<b className="m3-num">{money(i.subtotal)}</b>} />
        ))}
        <SummaryLine label="Subtotal" value={<span className="m3-num">{money(sub)}</span>} />
        {discount ? <SummaryLine label={`Promo ${reservation.promoCodeApplied ?? ''}`} value={<span className="m3-num m3-pos">− {money(discount)}</span>} /> : null}
        <SummaryLine label={<b>Total to pay</b>} value={<b className="m3-total">{money(reservation.totalAmount)}</b>} strong />
      </div>
      <p className="m3-muted buyer-secure">Secure checkout. No extra fees are added, and you pay only when you approve the prompt on your phone.</p>
    </aside>
  );
}
