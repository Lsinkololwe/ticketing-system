'use client';

import { earlyBirdOn, effectivePrice, tierCap, tierState, type EventTierRow } from '@pml.tickets/shared';
import { countdown, fullDate, clock, money } from '@/lib/format';

export interface TierListProps {
  tiers: EventTierRow[];
  quantities: Record<string, number>;
  onChange: (tier: EventTierRow, next: number) => void;
  /** Largest number of tickets one booking may hold across tiers (when the platform publishes it). */
  maxTotal?: number;
  /** Ids of hidden tiers an access code opened this visit. */
  unlockedIds?: ReadonlySet<string>;
  now: number;
}

function StepBtn({ label, glyph, disabled, onClick }: { label: string; glyph: string; disabled: boolean; onClick: () => void }) {
  return (
    <button type="button" className="m3-iconbtn m3-state" data-variant="outlined" aria-label={label} title={label} disabled={disabled} onClick={onClick}>
      <span aria-hidden="true">{glyph}</span>
    </button>
  );
}

function Pill({ tone, children }: { tone?: 'warn' | 'hot' | 'ok' | 'off'; children: React.ReactNode }) {
  return (
    <span className="buyer-pill" data-tone={tone}>
      {children}
    </span>
  );
}

/** One row per ticket tier with sale windows, perks, price and a quantity stepper. */
export function TierList({ tiers, quantities, onChange, maxTotal, unlockedIds, now }: TierListProps) {
  const total = Object.values(quantities).reduce((a, b) => a + b, 0);
  if (!tiers.length) {
    return <p className="m3-muted">No tickets are on sale for this event yet.</p>;
  }
  return (
    <div className="buyer-tiers" id="tierlist">
      {tiers.map((t) => {
        const st = tierState(t, now);
        const q = quantities[t.id] ?? 0;
        const cap = maxTotal !== undefined ? Math.min(tierCap(t), maxTotal) : tierCap(t);
        const eb = earlyBirdOn(t, now);
        const low = t.availableQuantity <= 20 || t.availableQuantity <= t.quantity * 0.1;
        const full = maxTotal !== undefined && total >= maxTotal;
        const min = t.minPerOrder && t.minPerOrder > 1 ? t.minPerOrder : 1;
        return (
          <div key={t.id} className="buyer-tier" data-state={st} data-selected={q > 0 ? 'true' : undefined} role="group" aria-label={t.name}>
            <div className="buyer-tier__main">
              <div className="buyer-tier__head">
                <h4>{t.name}</h4>
                {unlockedIds?.has(t.id) ? <Pill tone="ok">Unlocked with access code</Pill> : null}
                {st === 'SOON' && t.salesStartAt ? (
                  <Pill tone="off">
                    On sale {fullDate(t.salesStartAt)}, {clock(t.salesStartAt)}
                  </Pill>
                ) : null}
                {st === 'ON' && t.salesEndAt ? (
                  <Pill tone="warn">
                    Sales close in <b>{countdown(Date.parse(t.salesEndAt) - now)}</b>
                  </Pill>
                ) : null}
                {st === 'ENDED' ? <Pill tone="off">Sales ended</Pill> : null}
                {eb && t.earlyBirdEndsAt ? (
                  <Pill tone="hot">
                    Early-bird price ends in <b>{countdown(Date.parse(t.earlyBirdEndsAt) - now)}</b>
                  </Pill>
                ) : null}
                {st === 'SOLD' ? <Pill tone="warn">Sold out</Pill> : low && st === 'ON' ? <Pill tone="warn">Only {t.availableQuantity} left</Pill> : null}
              </div>
              {t.description ? <p className="m3-muted">{t.description}</p> : null}
              {t.benefits?.length ? (
                <ul className="buyer-perks">
                  {t.benefits.map((b) => (
                    <li key={b}>{b}</li>
                  ))}
                </ul>
              ) : null}
              {st === 'ON' ? (
                <p className="m3-muted">
                  {min > 1 ? `Minimum ${min}, maximum` : 'Maximum'} {cap} per booking
                </p>
              ) : null}
            </div>
            <div className="buyer-tier__buy">
              <div className="buyer-tier__price m3-num">
                {money(effectivePrice(t, now))}
                {eb ? <s> {money(t.price)}</s> : null}
              </div>
              {st === 'ON' ? (
                <div className="buyer-stepper" role="group" aria-label={`${t.name} quantity`}>
                  <StepBtn label={`Remove one ${t.name} ticket`} glyph="−" disabled={q === 0} onClick={() => onChange(t, q <= min ? 0 : q - 1)} />
                  <output aria-live="polite">{q}</output>
                  <StepBtn
                    label={`Add one ${t.name} ticket`}
                    glyph="+"
                    disabled={q >= cap || full}
                    onClick={() => onChange(t, q === 0 ? Math.min(min, cap) : Math.min(q + 1, cap))}
                  />
                </div>
              ) : (
                <span className="m3-muted">{st === 'SOLD' ? 'Not available' : st === 'SOON' ? 'Not on sale yet' : 'Sales closed'}</span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
