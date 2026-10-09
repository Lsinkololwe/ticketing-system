// @vitest-environment jsdom
import { renderHook } from '@testing-library/react';
import { print } from 'graphql';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const useQuery = vi.fn();
vi.mock('@apollo/client/react', () => ({ useQuery: (...a: unknown[]) => useQuery(...a) }));

import { REFERENCE_OPTIONS } from '../reference.queries';
import { useCountryOptions, useReferenceOptions } from '../reference.hooks';
import { optionalReferenceCode, referenceCode } from '../reference.schemas';

// Fixtures belong to the test. The hook under test has no list of its own.
const rows = [
  { __typename: 'ReferenceData', id: '2', code: 'LIMITED_COMPANY', name: 'Limited company', description: 'PACRA company', parentCode: null, displayOrder: 2, metadata: { requiredDocuments: ['NATIONAL_ID'] } },
  { __typename: 'ReferenceData', id: '1', code: 'SOLE_PROPRIETORSHIP', name: 'Sole proprietorship', description: null, parentCode: null, displayOrder: 1, metadata: null },
  { __typename: 'ReferenceData', id: '3', code: 'KITWE', name: 'Kitwe', description: null, parentCode: 'CB', displayOrder: 0, metadata: {} },
];

describe('useReferenceOptions', () => {
  beforeEach(() => useQuery.mockReset());

  it('asks for one reference type, cache-first, with a unique operation name', () => {
    useQuery.mockReturnValue({ data: undefined, dataState: 'empty', loading: true, error: undefined });
    renderHook(() => useReferenceOptions('BUSINESS_TYPE'));
    expect(print(REFERENCE_OPTIONS)).toContain('query ReferenceOptions($type: ReferenceType!)');
    expect(print(REFERENCE_OPTIONS)).not.toContain('${');
    expect(useQuery.mock.calls[0][1]).toMatchObject({ variables: { type: 'BUSINESS_TYPE' }, fetchPolicy: 'cache-first' });
  });

  it('reports loading, with no invented rows, until the platform answers', () => {
    useQuery.mockReturnValue({ data: undefined, dataState: 'empty', loading: true, error: undefined });
    const { result } = renderHook(() => useReferenceOptions('BUSINESS_TYPE'));
    expect(result.current).toMatchObject({ items: [], options: [], loading: true, ready: false });
  });

  it('orders by the platform display order and exposes code, label, description and metadata', () => {
    useQuery.mockReturnValue({ data: { referenceData: rows }, dataState: 'complete', loading: false, error: undefined });
    const { result } = renderHook(() => useReferenceOptions<{ requiredDocuments?: string[] }>('BUSINESS_TYPE'));
    expect(result.current.options.map((o) => o.value)).toEqual(['KITWE', 'SOLE_PROPRIETORSHIP', 'LIMITED_COMPANY']);
    expect(result.current.ready).toBe(true);
    expect(result.current.byCode.get('LIMITED_COMPANY')?.metadata.requiredDocuments).toEqual(['NATIONAL_ID']);
    expect(result.current.byCode.get('SOLE_PROPRIETORSHIP')?.metadata).toEqual({});
    expect(result.current.labelOf('LIMITED_COMPANY')).toBe('Limited company');
    expect(result.current.labelOf('GONE')).toBe('GONE');
    expect(result.current.labelOf(null)).toBe('');
  });

  it('narrows to the children of a parent', () => {
    useQuery.mockReturnValue({ data: { referenceData: rows }, dataState: 'complete', loading: false, error: undefined });
    const { result } = renderHook(() => useReferenceOptions('CITY', { parentCode: 'CB' }));
    expect(result.current.options.map((o) => o.value)).toEqual(['KITWE']);
  });

  it('says empty when the list loads empty, and when it could not be read', () => {
    useQuery.mockReturnValue({ data: { referenceData: [] }, dataState: 'complete', loading: false, error: undefined });
    expect(renderHook(() => useReferenceOptions('BANK')).result.current).toMatchObject({ empty: true, ready: true });
    const failure = new Error('network');
    useQuery.mockReturnValue({ data: undefined, dataState: 'empty', loading: false, error: failure });
    expect(renderHook(() => useReferenceOptions('BANK')).result.current).toMatchObject({ empty: true, ready: false, error: failure, items: [] });
  });

  it('does not read when skipped', () => {
    useQuery.mockReturnValue({ data: undefined, dataState: 'empty', loading: false, error: undefined });
    renderHook(() => useReferenceOptions('BANK', { skip: true }));
    expect(useQuery.mock.calls[0][1]).toMatchObject({ skip: true });
  });
});

describe('useCountryOptions', () => {
  beforeEach(() => useQuery.mockReset());
  it('maps COUNTRY rows to the phone field shape, dropping the plus, and ignores rows without a calling code', () => {
    useQuery.mockReturnValue({
      dataState: 'complete', loading: false, error: undefined,
      data: { referenceData: [
        { id: '1', code: 'ZM', name: 'Zambia', description: null, parentCode: null, displayOrder: 0, metadata: { dialCode: '+260', iso3: 'ZMB' } },
        { id: '2', code: 'XX', name: 'Nowhere', description: null, parentCode: null, displayOrder: 1, metadata: {} },
      ] },
    });
    const { result } = renderHook(() => useCountryOptions());
    expect(result.current.countries).toEqual([{ code: 'ZM', name: 'Zambia', dial: '260' }]);
    expect(useQuery.mock.calls[0][1]).toMatchObject({ variables: { type: 'COUNTRY' } });
  });
});

describe('referenceCode', () => {
  it('accepts only listed codes once the list is known', () => {
    const schema = referenceCode(['MTN', 'AIRTEL']);
    expect(schema.safeParse('MTN').success).toBe(true);
    expect(schema.safeParse('VODAFONE').success).toBe(false);
    expect(schema.safeParse('').success).toBe(false);
  });
  it('lets the server judge while the list is unavailable', () => {
    expect(referenceCode([]).safeParse('MTN').success).toBe(true);
    expect(referenceCode([]).safeParse('').success).toBe(false);
  });
  it('optional variant allows empty', () => {
    expect(optionalReferenceCode(['A']).safeParse('').success).toBe(true);
    expect(optionalReferenceCode(['A']).safeParse('B').success).toBe(false);
  });
});
