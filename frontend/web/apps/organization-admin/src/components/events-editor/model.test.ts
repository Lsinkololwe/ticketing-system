import { describe, expect, it } from 'vitest';
import {
  blockers, checklist, commissionFor, createInput, currentPrice, emptyForm, isDateLocked, isEditLocked,
  newTier, updateInput,
} from './model';

const ready = () => {
  const f = emptyForm();
  f.title = 'Fixture Fest';
  f.categoryId = 'c1';
  f.start = '2030-01-01T10:00';
  f.end = '2030-01-01T12:00';
  f.venue = 'Hall';
  f.city = 'Lusaka';
  f.capacity = '500';
  f.tiers = [newTier({ name: 'General', price: 10000, quantity: '200' })];
  return f;
};

describe('event editor model', () => {
  it('sends the sum of the tier quantities as the event capacity (a different figure is refused)', () => {
    const f = ready();
    f.tiers = [newTier({ name: 'General', price: 15000, quantity: '100' }), newTier({ name: 'VIP', price: 40000, quantity: '40' })];
    expect(createInput(f).totalCapacity).toBe(140);
    expect(updateInput(f).totalCapacity).toBe(140);
    f.tiers = [];
    expect(createInput(f).totalCapacity).toBe(500);
  });

  it('flags all three approval blockers on an empty form', () => {
    expect(blockers(emptyForm())).toEqual(['NO_PUBLISHED_TIER', 'NO_LOCATION', 'NO_CAPACITY']);
    expect(blockers(ready())).toEqual([]);
  });
  it('accepts a virtual link as the location', () => {
    const f = ready();
    f.venue = '';
    f.isVirtual = true;
    f.virtualEventUrl = 'https://x.test';
    expect(blockers(f)).toEqual([]);
  });
  it('computes commission and net', () => {
    expect(commissionFor(150, 5)).toEqual({ fee: 7.5, net: 142.5 });
  });
  it('uses the early-bird price only while it runs', () => {
    const t = newTier({ price: 10000, earlyBirdPrice: 8000, earlyBirdEndsAt: '2030-01-10T00:00' });
    expect(currentPrice(t, new Date('2030-01-05'))).toBe(80);
    expect(currentPrice(t, new Date('2030-02-05'))).toBe(100);
  });
  it('marks approval blockers in the checklist', () => {
    const items = checklist(emptyForm());
    expect(items.filter((i) => i.blocker && !i.ok)).toHaveLength(3);
  });
  it('maps to create and update inputs', () => {
    const c = createInput(ready());
    expect(c.ticketTiers[0]).toMatchObject({ name: 'General', quantity: 200, currency: 'ZMW', sortOrder: 0 });
    expect(c.totalCapacity).toBe(200); // the tier sum, not the venue field
    const u = updateInput(ready(), { dateLocked: true });
    expect('eventDateTime' in u).toBe(false);
  });
  it('lock rules follow the lifecycle', () => {
    expect(isEditLocked('PENDING_APPROVAL')).toBe(true);
    expect(isEditLocked('CHANGES_REQUESTED')).toBe(false);
    expect(isDateLocked('PUBLISHED')).toBe(true);
    expect(isDateLocked('DRAFT')).toBe(false);
  });
});
