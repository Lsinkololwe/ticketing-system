import { describe, expect, it } from 'vitest';
import { z } from 'zod';
import {
  dateRange,
  email,
  endAfterStart,
  files,
  isoDate,
  isoTime,
  minorToKwachaString,
  money,
  moneyMinor,
  nonEmptyTrimmed,
  normalisePhone,
  otp6,
  parseKwachaToMinor,
  percent,
  phoneE164,
  phoneE164Optional,
  slug,
  url,
  zodIssuesToFieldErrors,
} from '../schemas';

const msg = (r: { success: boolean; error?: z.ZodError }) => r.error?.issues[0]?.message;

describe('phoneE164', () => {
  const s = phoneE164();
  it.each([
    ['0971234567', '+260971234567'],
    ['097 123 4567', '+260971234567'],
    ['+260 97 123 4567', '+260971234567'],
    ['+260971234567', '+260971234567'],
    ['+44 7911 123456', '+447911123456'],
    ['0044 7911 123456', '+447911123456'],
  ])('normalises %s', (input, out) => {
    expect(s.parse(input)).toBe(out);
  });
  it('rejects junk and empty with messages', () => {
    expect(msg(s.safeParse('12'))).toBe('Enter a valid phone number');
    expect(msg(s.safeParse(''))).toBe('Enter a phone number');
    expect(msg(s.safeParse(undefined))).toBe('Enter a phone number');
  });
  it('honours the region hint', () => {
    expect(phoneE164({ region: 'GB' }).parse('07911 123456')).toBe('+447911123456');
    expect(normalisePhone('abc')).toBeUndefined();
  });
  it('optional variant turns empty into undefined', () => {
    const o = phoneE164Optional();
    expect(o.parse('')).toBeUndefined();
    expect(o.parse('0971234567')).toBe('+260971234567');
    expect(o.safeParse('12').success).toBe(false);
  });
});

describe('primitives', () => {
  it('email lowercases and validates', () => {
    expect(email().parse('  A@B.com ')).toBe('a@b.com');
    expect(msg(email().safeParse('nope'))).toBe('Enter a valid email address');
    expect(msg(email().safeParse(''))).toBe('Enter an email address');
  });
  it('otp6 needs exactly six digits', () => {
    expect(otp6().parse('123456')).toBe('123456');
    expect(msg(otp6().safeParse('12345'))).toBe('Enter the 6-digit code');
    expect(otp6().safeParse('12a456').success).toBe(false);
  });
  it('slug', () => {
    expect(slug().parse(' My-Event ')).toBe('my-event');
    expect(slug().safeParse('-bad').success).toBe(false);
    expect(slug().safeParse('a--b').success).toBe(false);
    expect(msg(slug().safeParse('ab'))).toBe('Use at least 3 characters');
  });
  it('url allows only http(s)', () => {
    expect(url().safeParse('https://x.zm/a').success).toBe(true);
    expect(msg(url().safeParse('javascript:alert(1)'))).toMatch(/valid web address/);
    expect(msg(url().safeParse('x'))).toMatch(/valid web address/);
  });
  it('nonEmptyTrimmed', () => {
    expect(nonEmptyTrimmed().parse('  hi ')).toBe('hi');
    expect(msg(nonEmptyTrimmed().safeParse('   '))).toBe('Required');
  });
  it('percent bounds and decimals', () => {
    expect(percent().parse('12.5')).toBe(12.5);
    expect(msg(percent().safeParse(101))).toBe('Enter a value between 0 and 100');
    expect(msg(percent().safeParse(1.234))).toBe('Use at most 2 decimal places');
    expect(percent().safeParse('abc').success).toBe(false);
  });
});

describe('money', () => {
  it('parses to ngwee without float error', () => {
    expect(parseKwachaToMinor('1,250.50')).toBe(125050);
    expect(parseKwachaToMinor('0.1')).toBe(10);
    expect(parseKwachaToMinor('19.99')).toBe(1999);
    expect(parseKwachaToMinor('K 5')).toBe(500);
    expect(parseKwachaToMinor('1.234')).toBeUndefined();
    expect(parseKwachaToMinor('-1')).toBeUndefined();
    expect(minorToKwachaString(125050)).toBe('1250.50');
    expect(minorToKwachaString(5)).toBe('0.05');
  });
  it('money(): positive, 2dp, min', () => {
    const m = money({ minMinor: 5000 });
    expect(m.parse('50')).toBe(5000);
    expect(msg(m.safeParse('49.99'))).toBe('Minimum amount is K 50.00');
    expect(msg(m.safeParse('1.999'))).toBe('Enter an amount with at most 2 decimal places');
    expect(msg(money().safeParse('0'))).toBe('Amount must be more than zero');
    expect(msg(money().safeParse(''))).toBe('Enter an amount');
    expect(money({ allowZero: true }).parse(0)).toBe(0);
    expect(msg(money({ maxMinor: 1000 }).safeParse('11'))).toBe('Maximum amount is K 10.00');
  });
  it('moneyMinor(): integer ngwee', () => {
    const m = moneyMinor({ minMinor: 100 });
    expect(m.parse(100)).toBe(100);
    expect(msg(m.safeParse(99))).toBe('Minimum amount is K 1.00');
    expect(msg(m.safeParse(1.5))).toBe('Enter an amount with at most 2 decimal places');
    expect(msg(m.safeParse(undefined))).toBe('Enter an amount');
    expect(msg(moneyMinor({ maxMinor: 500 }).safeParse(501))).toBe('Maximum amount is K 5.00');
  });
});

describe('dates', () => {
  it('isoDate rejects impossible dates and range', () => {
    expect(isoDate().parse('2026-02-28')).toBe('2026-02-28');
    expect(msg(isoDate().safeParse('2026-02-30'))).toBe('Enter a valid date');
    expect(msg(isoDate().safeParse(''))).toBe('Choose a date');
    expect(isoDate({ min: '2026-01-01' }).safeParse('2025-12-31').success).toBe(false);
    expect(isoDate({ max: '2026-01-01' }).safeParse('2026-01-02').success).toBe(false);
  });
  it('isoTime', () => {
    expect(isoTime().safeParse('23:59').success).toBe(true);
    expect(isoTime().safeParse('24:00').success).toBe(false);
  });
  it('dateRange puts the error on end', () => {
    const r = dateRange().safeParse({ start: '2026-05-02', end: '2026-05-01' });
    expect(r.success).toBe(false);
    expect(zodIssuesToFieldErrors(r.error!)).toEqual({ end: 'End must be after start' });
    expect(dateRange().safeParse({ start: '2026-05-01', end: '2026-05-02' }).success).toBe(true);
    expect(dateRange({ allowSame: true }).safeParse({ start: '2026-05-01', end: '2026-05-01' }).success).toBe(true);
    expect(dateRange().safeParse({ start: '2026-05-01', end: '2026-05-01' }).success).toBe(false);
  });
  it('endAfterStart works on flat schemas with custom keys', () => {
    const s = z.object({ startsAt: isoDate(), endsAt: isoDate() }).superRefine(endAfterStart('startsAt', 'endsAt'));
    const r = s.safeParse({ startsAt: '2026-05-02', endsAt: '2026-05-01' });
    expect(zodIssuesToFieldErrors(r.error!)).toEqual({ endsAt: 'End must be after start' });
  });
});

describe('files', () => {
  const f = (name: string, type: string, size = 10) => new File([new Uint8Array(size)], name, { type });
  it('enforces count, size and type', () => {
    const s = files({ max: 1, maxBytes: 1024 * 1024, types: ['application/pdf'] });
    expect(s.safeParse([f('a.pdf', 'application/pdf')]).success).toBe(true);
    expect(msg(s.safeParse([]))).toBe('Choose a file');
    expect(msg(s.safeParse([f('a.pdf', 'application/pdf'), f('b.pdf', 'application/pdf')]))).toBe('Choose at most 1 files');
    expect(msg(s.safeParse([f('a.png', 'image/png')]))).toBe('That file type is not supported');
    expect(msg(s.safeParse([f('a.pdf', 'application/pdf', 2 * 1024 * 1024)]))).toBe('Each file must be 1 MB or smaller');
  });
});

describe('zodIssuesToFieldErrors', () => {
  it('flattens nested paths, first message wins, accepts issues array', () => {
    const s = z.object({ tiers: z.array(z.object({ name: z.string().min(1, 'Required').min(3, 'Short') })) });
    const r = s.safeParse({ tiers: [{ name: '' }] });
    expect(zodIssuesToFieldErrors(r.error!)).toEqual({ 'tiers.0.name': 'Required' });
    expect(zodIssuesToFieldErrors(r.error!.issues)).toEqual({ 'tiers.0.name': 'Required' });
  });
});
