import { describe, expect, it } from 'vitest';
import { allSold, effectivePrice, tierCap, tierState, visibleTiers, type EventTierRow } from './event';

const tier = (o: Partial<EventTierRow> = {}): EventTierRow => ({
  id: 't1', name: 'General', code: 'GEN', description: null, price: 150, originalPrice: null, earlyBirdPrice: null, earlyBirdEndsAt: null,
  salesStartAt: null, salesEndAt: null, currency: 'ZMW', quantity: 100, soldQuantity: 10, availableQuantity: 90, minPerOrder: 1, maxPerOrder: 8,
  benefits: null, isActive: true, isHidden: false, sortOrder: 0, ...o,
});
const NOW = Date.parse('2026-10-02T09:00:00Z');

describe('tier rules', () => {
  it('derives the sales state', () => {
    expect(tierState(tier(), NOW)).toBe('ON');
    expect(tierState(tier({ availableQuantity: 0 }), NOW)).toBe('SOLD');
    expect(tierState(tier({ salesStartAt: '2026-10-15T08:00:00Z' }), NOW)).toBe('SOON');
    expect(tierState(tier({ salesEndAt: '2026-10-01T08:00:00Z' }), NOW)).toBe('ENDED');
  });
  it('uses the early-bird price until it ends', () => {
    const t = tier({ earlyBirdPrice: 120, earlyBirdEndsAt: '2026-10-05T00:00:00Z' });
    expect(effectivePrice(t, NOW)).toBe(120);
    expect(effectivePrice(t, Date.parse('2026-10-06T00:00:00Z'))).toBe(150);
  });
  it('hides inactive and hidden tiers, and sorts', () => {
    const list = visibleTiers({ ticketTiers: [tier({ id: 'b', sortOrder: 2 }), tier({ id: 'h', isHidden: true }), tier({ id: 'i', isActive: false }), tier({ id: 'a', sortOrder: 1 })] });
    expect(list.map((t) => t.id)).toEqual(['a', 'b']);
  });
  it('caps by per-order limit and stock', () => {
    expect(tierCap(tier())).toBe(8);
    expect(tierCap(tier({ availableQuantity: 3 }))).toBe(3);
    expect(tierCap(tier({ maxPerOrder: null }))).toBe(90);
  });
  it('detects an all sold-out event', () => {
    expect(allSold([tier({ availableQuantity: 0 })], NOW)).toBe(true);
    expect(allSold([], NOW)).toBe(false);
  });
  it('opens a hidden tier once its access code was accepted', () => {
    const hidden = tier({ id: 'h', isHidden: true, sortOrder: 0 });
    const list = visibleTiers({ ticketTiers: [tier({ id: 'a', sortOrder: 1 }), hidden] }, [hidden]);
    expect(list.map((t) => t.id)).toEqual(['h', 'a']);
    expect(visibleTiers({ ticketTiers: [tier({ id: 'a' })] }, [hidden]).map((t) => t.id)).toContain('h');
  });
});
