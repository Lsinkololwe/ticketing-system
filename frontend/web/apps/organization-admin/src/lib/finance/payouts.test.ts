import { describe, expect, it } from 'vitest';
import { blockedMessage, currentEventName, escrowEligibilityLabel, maskAccount, sumAmounts } from './payouts';

describe('current event name', () => {
  it('is shown only when the event has been renamed since the record was made', () => {
    expect(currentEventName('Jazz Night', { title: 'Jazz Night (Late Show)' })).toBe('Jazz Night (Late Show)');
    expect(currentEventName('Jazz Night', { title: ' Jazz Night ' })).toBeNull();
    expect(currentEventName('Jazz Night', null)).toBeNull();
    expect(currentEventName(null, { title: 'Jazz Night' })).toBe('Jazz Night');
  });
});

describe('payout helpers', () => {
  it('masks account numbers', () => {
    expect(maskAccount('0123456784821')).toBe('****4821');
    expect(maskAccount('')).toBe('—');
  });
  it('words every blocked reason', () => {
    expect(blockedMessage('BELOW_MINIMUM', '10')).toContain('K 10');
    expect(blockedMessage('PAYOUT_ALREADY_REQUESTED')).toMatch(/already open/);
    expect(blockedMessage('OTHER')).toMatch(/not eligible/);
  });
  it('labels eligibility from escrow status', () => {
    expect(escrowEligibilityLabel('PAYOUT_ELIGIBLE').eligible).toBe(true);
    expect(escrowEligibilityLabel('HOLD').text).toBe('Hold not elapsed');
  });
  it('sums', () => expect(sumAmounts(['1.5', 2, null])).toBe(3.5));
});
