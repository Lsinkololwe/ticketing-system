import { describe, expect, it } from 'vitest';
import { isCurrent, NAV_SECTIONS, visibleSections } from './navigation';

const all = NAV_SECTIONS.flatMap((s) => s.items);

describe('console navigation', () => {
  it('lists the designed destinations in order', () => {
    expect(all.map((i) => i.label)).toEqual([
      'Overview', 'Events', 'Bookings', 'Media', 'Onboarding', 'Payouts', 'Banks', 'Transactions', 'Team', 'Settings',
    ]);
  });
  it('shows Onboarding only while the organization is not active', () => {
    expect(visibleSections(true).flatMap((s) => s.items).some((i) => i.id === 'onboarding')).toBe(false);
    expect(visibleSections(false).flatMap((s) => s.items).some((i) => i.id === 'onboarding')).toBe(true);
  });
  it('marks the current entry by path', () => {
    const cur = (p: string) => all.filter((i) => isCurrent(i, p)).map((i) => i.id);
    expect(cur('/events/abc/edit')).toEqual(['events']);
    expect(cur('/finance')).toEqual(['payouts']);
    expect(cur('/finance/bank-accounts')).toEqual(['banks']);
    expect(cur('/apply/documents')).toEqual(['onboarding']);
    expect(cur('/settings/profile')).toEqual(['settings']);
  });
});
