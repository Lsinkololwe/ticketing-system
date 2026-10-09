import { describe, expect, it } from 'vitest';
import { detectOperator, prefixHint, toLocalPrefix, validateForOperator, type MobileOperator } from '../useMobileOperators';

// Fixtures belong to the test; the module under test has no list of operators.
const OPERATORS: MobileOperator[] = [
  { code: 'MTN', label: 'MTN Mobile Money', prefixes: ['096', '076'] },
  { code: 'AIRTEL', label: 'Airtel Money', prefixes: ['097', '077'] },
  { code: 'ZAMTEL', label: 'Zamtel Kwacha', prefixes: ['095', '055'] },
];

describe('mobile operator helpers', () => {
  it('turns the platform prefixes into local ones', () => {
    expect(toLocalPrefix('26096')).toBe('096');
    expect(toLocalPrefix('26055')).toBe('055');
  });
  it('derives the hint from the prefixes', () => {
    expect(prefixHint(OPERATORS[0])).toBe('Numbers starting 096 / 076');
    expect(prefixHint({ prefixes: [] })).toBe('');
  });
  it('detects the operator from a typed number', () => {
    expect(detectOperator(OPERATORS, '0971234567')).toBe('AIRTEL');
    expect(detectOperator(OPERATORS, '0551234567')).toBe('ZAMTEL');
    expect(detectOperator(OPERATORS, '0991234567')).toBeNull();
    expect(detectOperator(OPERATORS, '09')).toBeNull();
    expect(detectOperator([], '0971234567')).toBeNull();
  });
  it('validates a number against its operator only', () => {
    expect(validateForOperator(OPERATORS, 'MTN', '0961234567')).toBe(true);
    expect(validateForOperator(OPERATORS, 'MTN', '0971234567')).toBe(false);
    expect(validateForOperator(OPERATORS, 'MTN', '096123456')).toBe(false);
    expect(validateForOperator(OPERATORS, 'VODAFONE', '0961234567')).toBe(false);
  });
});
