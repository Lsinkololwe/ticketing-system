import { describe, expect, expectTypeOf, it } from 'vitest';
import {
  PERMISSION_CODES,
  PERMISSION_SCOPE,
  isPermissionCode,
  type PermissionCode,
} from '../generated/permission-codes';
import type { PermissionGateProps } from '../components/auth/PermissionGate';

/**
 * The permission codes are generated from the backend catalogue. These tests pin what the
 * frontend relies on: the codes follow the one grammar, every code has a scope, a code outside
 * the catalogue is refused by the type system, and no code is wildcard-shaped.
 */
describe('generated permission codes', () => {
  it('every code is module:action with exactly one colon and no wildcard or dot', () => {
    for (const code of PERMISSION_CODES) {
      expect(code).toMatch(/^[a-z][a-z0-9_]+:[a-z][a-z0-9_]+$/);
      expect(code.split(':')).toHaveLength(2);
      expect(code).not.toContain('*');
    }
  });

  it('is sorted and unique, so a regeneration produces a stable diff', () => {
    const sorted = [...PERMISSION_CODES].sort();
    expect([...PERMISSION_CODES]).toEqual(sorted);
    expect(new Set(PERMISSION_CODES).size).toBe(PERMISSION_CODES.length);
  });

  it('gives every code a scope', () => {
    for (const code of PERMISSION_CODES) {
      expect(['PLATFORM', 'ORGANIZATION', 'EVENT']).toContain(PERMISSION_SCOPE[code]);
    }
  });

  it('narrows a string to a code only when the catalogue has it', () => {
    expect(isPermissionCode('event:view')).toBe(true);
    expect(isPermissionCode('event:*')).toBe(false);
    expect(isPermissionCode('escrow:read')).toBe(false);
  });

  it('types a gate by the catalogue, so a code outside it is a compile error', () => {
    expectTypeOf<PermissionGateProps['permission']>().toEqualTypeOf<PermissionCode | undefined>();
    expectTypeOf<'event:view'>().toExtend<PermissionCode>();
    expectTypeOf<'escrow:read'>().not.toExtend<PermissionCode>();
  });
});
