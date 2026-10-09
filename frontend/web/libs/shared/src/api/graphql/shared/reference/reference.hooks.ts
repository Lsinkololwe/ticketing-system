'use client';

/**
 * Reference lists for dropdowns, radio groups, chips and filters.
 *
 * Reads catalog `referenceData` through Apollo's cache (`cache-first`, so a list is fetched once per
 * session however many fields use it). There is deliberately no built-in fallback list: while a list
 * loads the hook says so (`loading`), and when it cannot be read it says that too (`error`, `empty`),
 * so the caller renders the designed loading, empty and error states instead of a guess.
 *
 * Which types a signed-out visitor may read is the backend's decision (ET-PLT-014-R11); the
 * storefront reads only those.
 */
import { useMemo } from 'react';
import { useQuery } from '@apollo/client/react';
import type {
  ReferenceOptionsQuery,
  ReferenceOptionsQueryVariables,
  ReferenceType,
} from '../../../../types/graphql';
import { REFERENCE_OPTIONS } from './reference.queries';

export type ReferenceRow = ReferenceOptionsQuery['referenceData'][number];

/** A row shaped for a select, radio group or chip set. */
export interface ReferenceOption<M = Record<string, unknown>> {
  value: string;
  label: string;
  description: string | null;
  parentCode: string | null;
  metadata: M;
}

export interface UseReferenceOptions<M = Record<string, unknown>> {
  /** Rows in the platform's display order. Empty while loading and when the list could not be read. */
  items: ReferenceRow[];
  options: ReferenceOption<M>[];
  /** code -> option, for rendering a stored code as its label. */
  byCode: ReadonlyMap<string, ReferenceOption<M>>;
  /** True until the first answer arrives. */
  loading: boolean;
  error: Error | undefined;
  /** The list loaded and is empty, or could not be read: show the designed empty/unavailable state. */
  empty: boolean;
  /** The list is loaded and usable for validating a submitted code. */
  ready: boolean;
  /** The label for a stored code, or the code itself when the platform no longer lists it. */
  labelOf: (code: string | null | undefined) => string;
}

function sortRows(rows: ReferenceRow[]): ReferenceRow[] {
  return [...rows].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
}

/** Rows of `type`, optionally only the children of `parentCode` (a city's province, a genre's category). */
export function useReferenceOptions<M = Record<string, unknown>>(
  type: ReferenceType,
  settings?: { skip?: boolean; parentCode?: string | null },
): UseReferenceOptions<M> {
  const { data, dataState, loading, error } = useQuery<ReferenceOptionsQuery, ReferenceOptionsQueryVariables>(
    REFERENCE_OPTIONS,
    { variables: { type }, skip: settings?.skip, fetchPolicy: 'cache-first', errorPolicy: 'all' },
  );
  const parent = settings?.parentCode;
  const rows = dataState === 'complete' ? data?.referenceData : undefined;

  return useMemo(() => {
    const all = rows ? sortRows(rows) : [];
    const items = parent ? all.filter((row) => row.parentCode === parent) : all;
    const options = items.map<ReferenceOption<M>>((row) => ({
      value: row.code,
      label: row.name,
      description: row.description ?? null,
      parentCode: row.parentCode ?? null,
      metadata: (row.metadata ?? {}) as M,
    }));
    const byCode = new Map(options.map((o) => [o.value, o]));
    const ready = rows !== undefined && !loading;
    return {
      items,
      options,
      byCode,
      loading: loading && !rows,
      error,
      empty: !loading && options.length === 0,
      ready,
      labelOf: (code) => (code ? byCode.get(code)?.label ?? code : ''),
    };
  }, [rows, parent, loading, error]);
}

/** A country as the phone field needs it. */
export interface CountryOption {
  code: string;
  name: string;
  /** Calling code without the plus ("260"). */
  dial: string;
}

/**
 * Every country the platform takes a phone number from: the backend's `COUNTRY` list, seeded from
 * libphonenumber's supported regions. Zambia first, then its neighbours, then A to Z. Readable signed out.
 */
export function useCountryOptions(): { countries: CountryOption[]; loading: boolean; error: Error | undefined; empty: boolean } {
  const list = useReferenceOptions<{ dialCode?: string }>('COUNTRY');
  const countries = useMemo(
    () =>
      list.options
        .filter((o) => typeof o.metadata.dialCode === 'string' && o.metadata.dialCode.length > 1)
        .map((o) => ({ code: o.value, name: o.label, dial: String(o.metadata.dialCode).replace(/^\+/, '') })),
    [list.options],
  );
  return { countries, loading: list.loading, error: list.error, empty: list.empty };
}
