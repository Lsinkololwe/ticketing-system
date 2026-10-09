import { describe, expect, it } from 'vitest';
import { makePayFormSchema, toLocalNumber } from '../payForm';

const OPERATORS = [
  { code: 'MTN', label: 'MTN Mobile Money', prefixes: ['096', '076'] },
  { code: 'AIRTEL', label: 'Airtel Money', prefixes: ['097', '077'] },
];

describe('payForm schema', () => {
  const schema = makePayFormSchema(OPERATORS);
  it('normalises a typed number', () => {
    expect(toLocalNumber('96 123 4567')).toBe('0961234567');
    expect(toLocalNumber('+260 97 123 4567')).toBe('0971234567');
  });
  it('accepts a number of the chosen operator', () => {
    expect(schema.safeParse({ provider: 'AIRTEL', number: '97 123 4567' }).success).toBe(true);
  });
  it('refuses a provider the platform does not list', () => {
    expect(schema.safeParse({ provider: 'VODAFONE', number: '0961234567' }).success).toBe(false);
  });
  it('refuses a number from another operator, naming the chosen one', () => {
    const r = schema.safeParse({ provider: 'MTN', number: '0971234567' });
    expect(r.success).toBe(false);
    expect(JSON.stringify(r.error?.issues)).toContain('Enter a valid MTN Mobile Money number');
  });
});
