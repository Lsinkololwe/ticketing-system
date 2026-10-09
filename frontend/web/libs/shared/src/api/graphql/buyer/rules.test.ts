import { describe, expect, it } from 'vitest';
import { describeRule, hoursText, policyFor, sortedRules, type BuyerPlatformRules } from './rules';

const rules = {
  refundPolicies: [
    { code: 'MODERATE', label: 'Moderate', summary: 'Full refund until 7 days before.', rules: [{ daysBefore: 1, percent: 50 }, { daysBefore: 7, percent: 100 }] },
    { code: 'NO_REFUNDS', label: 'No refunds', summary: 'None.', rules: [] },
  ],
} as unknown as BuyerPlatformRules;

describe('platform rules wording', () => {
  it('finds the policy an event picked, or null', () => {
    expect(policyFor(rules, 'MODERATE')?.label).toBe('Moderate');
    expect(policyFor(rules, 'MISSING')).toBeNull();
    expect(policyFor(null, 'MODERATE')).toBeNull();
    expect(policyFor(rules, null)).toBeNull();
  });
  it('words each rule from its own tier, longest lead first', () => {
    const p = policyFor(rules, 'MODERATE')!;
    expect(sortedRules(p).map(describeRule)).toEqual([
      '100% refund if you ask at least 7 days before the event',
      '50% refund if you ask at least 1 day before the event',
    ]);
    expect(describeRule({ daysBefore: 0.5, percent: 25 })).toBe('25% refund if you ask at least 12 hours before the event');
  });
  it('words the refund cut-off', () => {
    expect(hoursText(48)).toBe('2 days');
    expect(hoursText(24)).toBe('24 hours');
    expect(hoursText(1)).toBe('1 hour');
  });
});
