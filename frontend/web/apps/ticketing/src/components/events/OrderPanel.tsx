'use client';

import { Button, SummaryLine } from '@pml.tickets/shared/components/m3';
import type { EventTierRow } from '@pml.tickets/shared';
import { effectivePrice } from '@pml.tickets/shared';
import { clock, fullDate, money } from '@/lib/format';
import type { EventPageRow } from '@pml.tickets/shared';

export interface OrderLine {
  tier: EventTierRow;
  qty: number;
  unit: number;
  amount: number;
}

export function buildLines(tiers: EventTierRow[], quantities: Record<string, number>, now: number): OrderLine[] {
  return tiers
    .filter((t) => (quantities[t.id] ?? 0) > 0)
    .map((t) => {
      const qty = quantities[t.id];
      const unit = effectivePrice(t, now);
      return { tier: t, qty, unit, amount: qty * unit };
    });
}

export function MiniEvent({ event }: { event: Pick<EventPageRow, 'title' | 'eventDateTime' | 'locationName' | 'cityName' | 'bannerImageUrl'> }) {
  return (
    <div className="buyer-mini">
      {event.bannerImageUrl ? <img src={event.bannerImageUrl} alt="" /> : <span className="buyer-mini__ph" aria-hidden="true" />}
      <div>
        <b>{event.title}</b>
        <span className="m3-muted">
          {fullDate(event.eventDateTime)} · {clock(event.eventDateTime)}
        </span>
        <span className="m3-muted">{[event.locationName, event.cityName].filter(Boolean).join(', ')}</span>
      </div>
    </div>
  );
}

export interface OrderPanelProps {
  event: EventPageRow;
  lines: OrderLine[];
  discount: number;
  promoCode: string | null;
  busy: boolean;
  signedIn: boolean;
  onReserve: () => void;
  error?: string | null;
  /** How long a reservation is held, from the platform rules. Null until they load. */
  holdMinutes?: number | null;
}

/** Sticky "Your tickets" summary with the single Reserve action. */
export function OrderPanel({ event, lines, discount, promoCode, busy, signedIn, onReserve, error, holdMinutes }: OrderPanelProps) {
  const sub = lines.reduce((a, l) => a + l.amount, 0);
  const count = lines.reduce((a, l) => a + l.qty, 0);
  return (
    <aside className="m3-panel buyer-side" id="sum" aria-label="Your tickets">
      <MiniEvent event={event} />
      <h2 className="m3-card__title">Your tickets</h2>
      {lines.length ? (
        <div>
          {lines.map((l) => (
            <SummaryLine key={l.tier.id} label={`${l.tier.name} × ${l.qty}`} value={<b className="m3-num">{money(l.amount)}</b>} />
          ))}
          <SummaryLine label="Subtotal" value={<span className="m3-num">{money(sub)}</span>} />
          {discount ? <SummaryLine label={`Promo ${promoCode ?? ''}`} value={<span className="m3-num m3-pos">− {money(discount)}</span>} /> : null}
          <SummaryLine label={<b>Total</b>} value={<b className="m3-total">{money(sub - discount)}</b>} strong />
        </div>
      ) : (
        <p className="m3-muted">Choose ticket quantities to see your total. All prices are in Zambian Kwacha with no added fees.</p>
      )}
      {error ? (
        <p role="alert" className="buyer-err">
          {error}
        </p>
      ) : null}
      <Button variant="accent" fullWidth disabled={count === 0} loading={busy} onClick={onReserve}>
        Reserve tickets
      </Button>
      <p className="m3-muted">
        {holdMinutes ? `Your tickets are held for ${holdMinutes} minutes once reserved, and the countdown shows on the next screen.` : 'Your tickets are held for a short time once reserved, and the countdown shows on the next screen.'}
        {signedIn ? '' : ' You will confirm your WhatsApp number or email to reserve.'}
      </p>
    </aside>
  );
}
