import { describe, expect, it } from 'vitest';
import { emptyForm, newTier, type EditorForm } from './model';
import { errorPaths, makeEventSchema, tabOfField, tabsWithErrors, tiersAreValid } from './schema';

const NOW = () => new Date('2030-01-01T00:00:00');
const valid = (): EditorForm => ({
  ...emptyForm(),
  title: 'Fixture Fest',
  categoryId: 'c1',
  start: '2030-02-01T10:00',
  end: '2030-02-01T12:00',
  venue: 'Hall',
  city: 'Lusaka',
  capacity: '500',
  refundPolicy: 'FLEXIBLE',
  tiers: [newTier({ category: 'GENERAL', name: 'General', price: 10000, quantity: '200' })],
});
const issues = (f: EditorForm, o: Parameters<typeof makeEventSchema>[0] = {}) => {
  const r = makeEventSchema({ now: NOW, ...o }).safeParse(f);
  return r.success ? [] : r.error.issues.map((i) => ({ path: i.path.join('.'), message: i.message }));
};

describe('event editor schema', () => {
  it('accepts a complete form', () => expect(issues(valid())).toEqual([]));

  it('requires title and category with the designed copy', () => {
    const list = issues(emptyForm());
    expect(list).toContainEqual({ path: 'title', message: 'Required' });
    expect(list).toContainEqual({ path: 'categoryId', message: 'Choose a category.' });
    expect(list).toContainEqual({ path: 'start', message: 'Set a start date and time.' });
    expect(issues({ ...valid(), title: 'ab' })).toContainEqual({ path: 'title', message: 'Add an event title of at least 3 characters.' });
  });

  it('checks the end is after the start and the start is in the future', () => {
    expect(issues({ ...valid(), end: '2030-02-01T09:00' })).toContainEqual({ path: 'end', message: 'The end must be after the start.' });
    expect(issues({ ...valid(), start: '2020-01-01T10:00', end: '2020-01-01T12:00' })).toContainEqual({ path: 'start', message: 'The start must be in the future.' });
    expect(issues({ ...valid(), start: '2020-01-01T10:00', end: '2020-01-01T12:00' }, { dateLocked: true })).toEqual([]);
  });

  it('needs a virtual link for virtual events', () => {
    expect(issues({ ...valid(), isVirtual: true })).toContainEqual({ path: 'virtualEventUrl', message: 'Enter the virtual event link starting with https://' });
  });

  it('validates tier rules: early bird, sold floor, hidden code, platform limit', () => {
    const f = valid();
    f.tiers = [newTier({ category: 'GENERAL', name: 'X', price: 5000, earlyBirdPrice: 6000, quantity: '5', sold: 9, isHidden: true, accessCode: 'ab', maxPerOrder: '20' })];
    const paths = issues(f, { maxPerOrder: 8 }).map((i) => i.path);
    expect(paths).toEqual(expect.arrayContaining(['tiers.0.earlyBirdPrice', 'tiers.0.earlyBirdEndsAt', 'tiers.0.quantity', 'tiers.0.accessCode', 'tiers.0.maxPerOrder']));
  });

  it('has no built-in platform limit: without rules a tier can ask for any maximum', () => {
    const f = valid();
    f.tiers = [newTier({ category: 'GENERAL', name: 'X', price: 5000, quantity: '5', maxPerOrder: '20' })];
    expect(issues(f).map((i) => i.path)).not.toContain('tiers.0.maxPerOrder');
  });

  it('keeps the extras optional and validates their rows', () => {
    const f = { ...valid(), faqs: [{ question: '', answer: 'a' }], runningOrder: [{ time: '', title: 'Doors' }], checkout: { maxTicketsPerOrder: '9', collectHolderNames: false, extraQuestion: '' } };
    const paths = issues(f, { maxPerOrder: 8 }).map((i) => i.path);
    expect(paths).toEqual(expect.arrayContaining(['faqs.0.question', 'runningOrder.0.time', 'checkout.maxTicketsPerOrder']));
    expect(tabOfField('faqs.0.question')).toBe('policy');
    expect(tabOfField('runningOrder.0.time')).toBe('when');
  });

  it('flags active tiers that exceed the capacity', () => {
    const f = valid();
    f.tiers = [newTier({ category: 'GENERAL', name: 'Big', quantity: '900' })];
    expect(issues(f).some((i) => /more than the capacity of 500/.test(i.message))).toBe(true);
  });

  it('maps fields to tabs', () => {
    expect(tabOfField('title')).toBe('basics');
    expect(tabOfField('end')).toBe('when');
    expect(tabOfField('accessibility.additionalNotes')).toBe('venue');
    expect(tabOfField('tiers.2.name')).toBe('tiers');
    expect(tabOfField('refundPolicy')).toBe('policy');
    expect(tabsWithErrors({ end: { message: 'x', type: 'custom' }, tiers: [{ name: { message: 'y', type: 'custom' } }] })).toEqual(['when', 'tiers']);
    expect(errorPaths({ tiers: [{ name: { message: 'y' } }] })).toEqual(['tiers.0.name']);
  });

  it('checks a tier category against the platform list once it is loaded', () => {
    const listed = ['GENERAL', 'VIP'];
    expect(issues(valid(), { tierCategories: listed })).toEqual([]);
    const f = valid();
    f.tiers = [newTier({ category: 'LEGACY', name: 'General', price: 10000, quantity: '200' })];
    expect(issues(f, { tierCategories: listed })).toContainEqual({ path: 'tiers.0.category', message: 'Choose one of the listed tier categories' });
    // list unavailable: the server decides; but a tier with no category is never valid
    expect(issues(f)).toEqual([]);
    f.tiers = [newTier({ category: '', name: 'General', price: 10000, quantity: '200' })];
    expect(issues(f)).toContainEqual({ path: 'tiers.0.category', message: 'Choose a tier category' });
  });

  it('reports whether every tier row is valid', () => {
    expect(tiersAreValid(valid().tiers)).toBe(true);
    expect(tiersAreValid([newTier({ category: 'GENERAL', name: '' })])).toBe(false);
  });
});
