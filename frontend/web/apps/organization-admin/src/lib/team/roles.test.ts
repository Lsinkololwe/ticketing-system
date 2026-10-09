import { describe, expect, it } from 'vitest';
import { CAPABILITIES, EVENT_CAPABILITIES, lastSeen } from './roles';

describe('team reference data', () => {
  it('never lets anyone but the owner bill or delete', () => {
    const billing = CAPABILITIES.find((c) => c.key === 'billing');
    expect(billing?.always).toEqual(['OWNER']);
  });
  it('only the event owner may cancel an event', () => {
    expect(EVENT_CAPABILITIES.find(([a]) => a.startsWith('Cancel'))?.[1]).toEqual(['EVENT_OWNER']);
  });
  it('words last-seen', () => {
    const now = new Date('2026-10-04T12:00:00Z').getTime();
    expect(lastSeen('2026-10-04T08:00:00Z', now)).toBe('Today');
    expect(lastSeen('2026-10-03T08:00:00Z', now)).toBe('Yesterday');
    expect(lastSeen('2026-09-30T08:00:00Z', now)).toBe('4 days ago');
    expect(lastSeen(null, now)).toBe('—');
  });
});
