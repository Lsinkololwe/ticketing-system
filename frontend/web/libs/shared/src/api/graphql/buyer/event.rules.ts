import type { EventPageRow, EventTierRow } from './event';

/* ---------------------------------------------------------------- tier rules */
export type TierState = 'ON' | 'SOLD' | 'SOON' | 'ENDED';

export function tierState(t: EventTierRow, now = Date.now()): TierState {
  if (t.availableQuantity <= 0) return 'SOLD';
  if (t.salesStartAt && now < Date.parse(t.salesStartAt)) return 'SOON';
  if (t.salesEndAt && now > Date.parse(t.salesEndAt)) return 'ENDED';
  return 'ON';
}
export function earlyBirdOn(t: EventTierRow, now = Date.now()): boolean {
  return t.earlyBirdPrice !== null && t.earlyBirdEndsAt !== null && now < Date.parse(t.earlyBirdEndsAt);
}
export function effectivePrice(t: EventTierRow, now = Date.now()): number {
  return earlyBirdOn(t, now) ? Number(t.earlyBirdPrice) : Number(t.price);
}
/** Tiers a buyer may see: active and not hidden, plus any hidden tier an access code has opened this visit. */
export function visibleTiers(e: Pick<EventPageRow, 'ticketTiers'>, unlocked: EventTierRow[] = []): EventTierRow[] {
  const open = new Set(unlocked.map((t) => t.id));
  const list = [...(e.ticketTiers ?? [])].filter((t) => t.isActive && (!t.isHidden || open.has(t.id)));
  for (const t of unlocked) if (!list.some((x) => x.id === t.id)) list.push(t);
  return list.sort((a, b) => a.sortOrder - b.sortOrder);
}
export function allSold(tiers: EventTierRow[], now = Date.now()): boolean {
  return tiers.length > 0 && tiers.every((t) => tierState(t, now) === 'SOLD');
}
/** Most tickets the buyer may add for one tier. */
export function tierCap(t: EventTierRow): number {
  return Math.min(t.maxPerOrder ?? t.availableQuantity, t.availableQuantity);
}
