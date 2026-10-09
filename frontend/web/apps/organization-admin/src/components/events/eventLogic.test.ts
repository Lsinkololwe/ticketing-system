import { describe, expect, it } from 'vitest';
import { actionsFor, blockersOf, DEFAULT_FILTERS, filterEvents, whyCannotPublish } from './eventLogic';
import { promoDefaults, promoSchema, promoToInput, tierDefaults, tierSchema, tierToInput } from './schemas';

describe('eventLogic', () => {
  it('computes blockers', () => {
    expect(blockersOf({ locationName: null, totalCapacity: 0, ticketTiers: [] })).toEqual(['NO_PUBLISHED_TIER', 'NO_LOCATION', 'NO_CAPACITY']);
    expect(blockersOf({ locationName: 'x', totalCapacity: 1, ticketTiers: [{ id: 't', isActive: true }] as never })).toEqual([]);
  });
  it('offers actions per status', () => {
    expect(actionsFor('DRAFT').map((a) => a.id)).toEqual(['edit', 'submit', 'duplicate', 'delete']);
    expect(actionsFor('PUBLISHED').map((a) => a.id)).toContain('unpublish');
    expect(actionsFor('CANCELLED').map((a) => a.id)).toEqual(['duplicate']);
  });
  it('explains why publishing is blocked', () => {
    expect(whyCannotPublish({ status: 'DRAFT', locationName: 'x', totalCapacity: 1, ticketTiers: [{ id: 't', isActive: true }] as never })[0]).toMatch(/approved/);
  });
  it('filters and sorts', () => {
    const mk = (id: string, t: string, sold: number) => ({ id, title: id, status: 'PUBLISHED', eventDateTime: t, soldTickets: sold, locationName: null, cityName: null, category: null, revenue: '0' }) as never;
    const out = filterEvents([mk('b', '2026-12-01', 1), mk('a', '2026-11-01', 5)], { ...DEFAULT_FILTERS, sort: 'sold' }, new Date('2026-10-01'));
    expect(out.map((e: { id: string }) => e.id)).toEqual(['a', 'b']);
  });
});

const issues = (r: { success: boolean; error?: { issues: Array<{ path: PropertyKey[]; message: string }> } }) =>
  Object.fromEntries((r.error?.issues ?? []).map((x) => [String(x.path[0]), x.message]));

describe('tier and promo schemas', () => {
  const tier = { ...tierDefaults(null), name: 'VIP', price: 50000, quantity: 5 };
  it('validates tiers against sold quantity and builds input in Kwacha', () => {
    expect(issues(tierSchema(10).safeParse(tier)).quantity).toMatch(/already sold/);
    expect(issues(tierSchema(0).safeParse({ ...tier, earlyBirdPrice: 60000, earlyBirdEndsAt: '2026-10-01T10:00' })).earlyBirdPrice).toBeTruthy();
    expect(issues(tierSchema(0).safeParse({ ...tier, isHidden: true })).accessCode).toBeTruthy();
    const ok = tierSchema(0).parse(tier);
    expect(tierToInput(ok)).toMatchObject({ name: 'VIP', price: '500.00', quantity: 5, accessCode: null });
  });
  it('validates promo codes', () => {
    const f = { ...promoDefaults(null), code: 'ab' };
    expect(issues(promoSchema([], false).safeParse(f)).code).toBeTruthy();
    expect(issues(promoSchema(['sunset10'], false).safeParse({ ...f, code: 'SUNSET10' })).code).toMatch(/already/);
    expect(issues(promoSchema([], false).safeParse({ ...f, code: 'SUNSET10', discountValue: 150 })).discountValue).toBeTruthy();
    const ok = promoSchema([], false).parse({ ...f, code: 'sunset10' });
    expect(promoToInput(ok)).toMatchObject({ code: 'SUNSET10', discountValue: '10', maxDiscountAmount: null });
  });
});
