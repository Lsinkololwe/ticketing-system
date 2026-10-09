import { describe, expect, it } from 'vitest';
import { deadlineFrom, durStr, slaOf } from '../sla';

const now = new Date('2026-01-10T12:00:00Z');
describe('sla', () => {
  it('formats durations', () => {
    expect(durStr(0.4)).toBe('24 min');
    expect(durStr(30)).toBe('30 h');
    expect(durStr(-72)).toBe('3 d');
  });
  it('computes the deadline from submission and target hours', () => {
    expect(deadlineFrom('2026-01-10T00:00:00Z', 72)).toBe('2026-01-13T00:00:00.000Z');
    expect(deadlineFrom(null, 72)).toBeNull();
    expect(deadlineFrom('2026-01-10T00:00:00Z', undefined)).toBeNull();
  });
  it('classifies ok / warn / overdue / paused', () => {
    expect(slaOf('2026-01-14T12:00:00Z', 24, { now })?.kind).toBe('ok');
    expect(slaOf('2026-01-11T00:00:00Z', 24, { now })).toMatchObject({ kind: 'warn', label: 'Due in 12 h', tone: 'warning' });
    expect(slaOf('2026-01-10T00:00:00Z', 24, { now })).toMatchObject({ kind: 'overdue', label: 'Overdue by 12 h', tone: 'error' });
    expect(slaOf('2026-01-10T00:00:00Z', 24, { now, paused: true })?.label).toBe('Clock paused');
    expect(slaOf(null, 24, { now })).toBeNull();
  });
});
