import { describe, expect, it } from 'vitest';
import { changedKeys, flattenRules, type PlatformRulesView } from './platformRules';

const rules: PlatformRulesView = {
  updatedAt: '2026-10-03T09:00:00Z', updatedBy: 'Fixture Admin', commissionRate: 5, minimumPayout: 10, escrowHoldDays: 7, currency: 'ZMW',
  reservationHoldMinutes: 10, reservationGraceMinutes: 5, maxTicketsPerBooking: 8, refundCutoffHours: 24, rescheduleLimit: 3,
  approvalSlaHours: 48, approvalWarnHours: 36, autoEscalation: true, escalationDelayHours: 12,
  requireCommentsOnChangesRequested: true, requireCommentsOnRejection: true,
  refundPolicies: [{ code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund until 24 hours before.' }],
  categories: ['Music'], provinces: ['Lusaka'], cities: ['Lusaka'], banks: ['Zanaco'], documentTypes: ['ID'], cancellationReasons: ['Other'],
};

describe('platform rules diff', () => {
  it('reports nothing without a snapshot', () => {
    expect(changedKeys(flattenRules(rules), null).size).toBe(0);
  });
  it('reports changed values only', () => {
    const seen = flattenRules(rules);
    const next = flattenRules({ ...rules, minimumPayout: 20, categories: ['Music', 'Sport'] });
    expect([...changedKeys(next, seen)].sort()).toEqual(['categories', 'minimumPayout']);
  });
});

import { toRulesView } from './platformRules';

describe('toRulesView', () => {
  const src = {
    updatedAt: null, updatedBy: null, currency: 'ZMW', commissionDefault: 7, commissionRate: null, minimumPayout: null,
    reservationHoldMinutes: 10, reservationGraceMinutes: 5, escrowHoldDays: 3, refundCutoffHours: 12, maxTicketsPerBooking: 6, rescheduleLimit: 2,
    refundPolicies: [{ code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund.' }],
    approval: { slaHours: 24, warnHours: 20, autoEscalation: false, escalationDelayHours: 4, requireCommentsOnRejection: true, requireCommentsOnChangesRequested: false },
  };
  const lists = { categories: ['Music'], provinces: [], cities: [], banks: ['Zanaco'], documentTypes: [], cancellationReasons: ['Other'] };
  it('uses the platform default commission when the organization has none and keeps unknown payout minimum empty', () => {
    const v = toRulesView(src, lists);
    expect(v.commissionRate).toBe(7);
    expect(v.minimumPayout).toBeNull();
    expect(v.approvalSlaHours).toBe(24);
    expect(v.banks).toEqual(['Zanaco']);
  });
  it('prefers the organization commission and parses the minimum payout', () => {
    const v = toRulesView({ ...src, commissionRate: 4.5, minimumPayout: '10.00' }, lists);
    expect(v.commissionRate).toBe(4.5);
    expect(v.minimumPayout).toBe(10);
  });
});
