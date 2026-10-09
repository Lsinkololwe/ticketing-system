import { describe, expect, it } from 'vitest';
import { hrefOf, pendingFor, primaryRole } from '@/lib/pending';

const counts = {
  'pending-approvals': 6,
  'organizer-applications': 3,
  'event-reviews': 2,
  'document-verification': 1,
  'payout-requests': 4,
  'refund-requests': 0,
};

describe('pendingFor', () => {
  it('lists every queue with a count for an admin, in prototype order', () => {
    const items = pendingFor(['ADMIN'], counts);
    expect(items.map((i) => i.label)).toEqual([
      'Organizer applications',
      'Events awaiting approval',
      'Documents to verify',
      'Payout requests pending',
    ]);
    expect(items[0]).toMatchObject({ count: 3, module: 'approvals', tab: 'orgs' });
  });

  it('hides approvals from finance roles and zero-count queues', () => {
    const items = pendingFor(['FINANCE'], counts);
    expect(items.map((i) => i.label)).toEqual(['Payout requests pending']);
  });

  it('builds hrefs', () => {
    expect(hrefOf('finance', 'payouts')).toBe('/finance/payouts');
    expect(hrefOf('health')).toBe('/health');
  });
});

describe('primaryRole', () => {
  it('prefers SUPER_ADMIN > ADMIN > FINANCE_LEAD > FINANCE', () => {
    expect(primaryRole(['FINANCE', 'FINANCE_LEAD'])).toBe('FINANCE_LEAD');
    expect(primaryRole(['FINANCE', 'ADMIN'])).toBe('ADMIN');
    expect(primaryRole(['ADMIN', 'SUPER_ADMIN'])).toBe('SUPER_ADMIN');
    expect(primaryRole([])).toBeNull();
  });
});
