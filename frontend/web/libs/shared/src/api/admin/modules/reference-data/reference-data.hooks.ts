'use client';

/**
 * React hooks for the Reference Data platform.
 *
 * Reads (`useReferenceData`, `useReferenceTypes`) are public — safe for storefront dropdowns.
 * Admin table + mutation hooks power the reference-data management screen. Mutations refetch the
 * affected type's admin page so the table stays consistent.
 */

import { useCallback, useEffect, useState } from 'react';
import {
  useApolloClient,
  useQuery,
  useMutation,
} from '@apollo/client/react';
import {
  REFERENCE_DATA,
  REFERENCE_TYPES,
  REFERENCE_DATA_ALL,
} from './reference-data.queries';
import {
  CREATE_REFERENCE_DATA,
  UPDATE_REFERENCE_DATA,
  DELETE_REFERENCE_DATA,
  SET_REFERENCE_DATA_ACTIVE,
} from './reference-data.mutations';
import type {
  CreateReferenceDataInput,
  ReferenceData,
  ReferenceType,
  UpdateReferenceDataInput,
} from './reference-data.types';
import type {
  ReferenceDataQuery,
  ReferenceDataQueryVariables,
  ReferenceTypesQuery,
  ReferenceDataAllQuery,
  ReferenceDataAllQueryVariables,
  CreateReferenceDataMutation,
  CreateReferenceDataMutationVariables,
  UpdateReferenceDataMutation,
  UpdateReferenceDataMutationVariables,
  DeleteReferenceDataMutation,
  DeleteReferenceDataMutationVariables,
  SetReferenceDataActiveMutation,
  SetReferenceDataActiveMutationVariables,
} from '../../../../types/graphql';

// ==========================================
// Read Hooks (public — dropdowns)
// ==========================================

/**
 * The dropdown workhorse: active rows of a type (banks, operators, currencies, genres…).
 */
export function useReferenceData(
  type: ReferenceType,
  options?: { activeOnly?: boolean; skip?: boolean }
) {
  const { data, loading, error, refetch } = useQuery<ReferenceDataQuery, ReferenceDataQueryVariables>(
    REFERENCE_DATA,
    {
    variables: { type, activeOnly: options?.activeOnly ?? true },
    skip: options?.skip,
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });

  return {
    items: data?.referenceData ?? [],
    loading,
    error,
    refetch,
  };
}

/** The type registry — drives the admin picker and dynamic metadata forms. */
export function useReferenceTypes() {
  const { data, loading, error } = useQuery<ReferenceTypesQuery>(REFERENCE_TYPES, {
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });

  return {
    types: data?.referenceTypes ?? [],
    loading,
    error,
  };
}

// ==========================================
// Admin Table Hook
// ==========================================

/** Offset-paginated admin table for one reference type. */
export function useReferenceDataAdmin(
  type: ReferenceType,
  pagination?: { page?: number; size?: number; sortBy?: string; sortDirection?: 'ASC' | 'DESC' }
) {
  const { data, loading, error, refetch } = useQuery<
    ReferenceDataAllQuery,
    ReferenceDataAllQueryVariables
  >(REFERENCE_DATA_ALL, {
    variables: {
      type,
      pagination: {
        page: pagination?.page ?? 0,
        size: pagination?.size ?? 50,
        sortBy: pagination?.sortBy ?? 'displayOrder',
        sortDirection: pagination?.sortDirection ?? 'ASC',
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.referenceDataAll;

  return {
    items: page?.content ?? [],
    totalElements: page?.totalElements ?? 0,
    totalPages: page?.totalPages ?? 0,
    currentPage: page?.pageNumber ?? 0,
    pageSize: page?.pageSize ?? 50,
    hasNext: page?.hasNext ?? false,
    hasPrevious: page?.hasPrevious ?? false,
    loading,
    error,
    refetch,
  };
}

/** The API caps a page at 100 rows; a type can be longer (about 245 countries). */
const ADMIN_PAGE = 50;
/** A bound on the walk so a misbehaving server cannot loop it: 40 pages of 50 is far beyond any list. */
const ADMIN_MAX_PAGES = 40;

/**
 * Every row of one type, including inactive ones, read page by page until the server says there is no next
 * page. The management screen filters and searches over the whole list, so it needs all of it; `refetch`
 * reads it again (after a write).
 */
export function useReferenceDataAdminAll(type: ReferenceType | null) {
  const client = useApolloClient();
  const [nonce, setNonce] = useState(0);
  const [state, setState] = useState<{ type: ReferenceType | null; items: ReferenceData[]; error: Error | undefined; done: boolean }>({
    type: null,
    items: [],
    error: undefined,
    done: false,
  });

  useEffect(() => {
    if (type === null) return undefined;
    let cancelled = false;
    void (async () => {
      const collected: ReferenceData[] = [];
      try {
        for (let page = 0; page < ADMIN_MAX_PAGES; page++) {
          const res = await client.query<ReferenceDataAllQuery, ReferenceDataAllQueryVariables>({
            query: REFERENCE_DATA_ALL,
            variables: { type, pagination: { page, size: ADMIN_PAGE, sortBy: 'displayOrder', sortDirection: 'ASC' } },
            fetchPolicy: 'network-only',
            errorPolicy: 'all',
          });
          const result = res.data?.referenceDataAll;
          if (!result) throw res.error ?? new Error('The list could not be loaded');
          collected.push(...(result.content as ReferenceData[]));
          if (!result.hasNext) break;
        }
        if (!cancelled) setState({ type, items: collected, error: undefined, done: true });
      } catch (e) {
        if (!cancelled) setState({ type, items: collected, error: e instanceof Error ? e : new Error(String(e)), done: true });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [client, type, nonce]);

  const current = state.type === type;
  const refetch = useCallback(() => {
    setState((prev) => ({ ...prev, done: false }));
    setNonce((n) => n + 1);
  }, []);
  return {
    items: current ? state.items : [],
    loading: type !== null && (!current || !state.done),
    error: current ? state.error : undefined,
    refetch,
  };
}

// ==========================================
// Mutation Hooks (admin)
// ==========================================

function adminRefetch(type: ReferenceType) {
  return {
    refetchQueries: [
      {
        query: REFERENCE_DATA_ALL,
        variables: {
          type,
          pagination: { page: 0, size: 50, sortBy: 'displayOrder', sortDirection: 'ASC' },
        },
      },
    ],
    awaitRefetchQueries: true,
    errorPolicy: 'all' as const,
  };
}

export function useCreateReferenceData(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<
    CreateReferenceDataMutation,
    CreateReferenceDataMutationVariables
  >(CREATE_REFERENCE_DATA, adminRefetch(type));

  const create = async (
    input: CreateReferenceDataInput
  ): Promise<ReferenceData | null> => {
    const result = await mutate({ variables: { input } });
    return result.data?.createReferenceData ?? null;
  };

  return { create, loading, error };
}

export function useUpdateReferenceData(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<
    UpdateReferenceDataMutation,
    UpdateReferenceDataMutationVariables
  >(UPDATE_REFERENCE_DATA, adminRefetch(type));

  const update = async (
    id: string,
    input: UpdateReferenceDataInput
  ): Promise<ReferenceData | null> => {
    const result = await mutate({ variables: { id, input } });
    return result.data?.updateReferenceData ?? null;
  };

  return { update, loading, error };
}

export function useDeleteReferenceData(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<
    DeleteReferenceDataMutation,
    DeleteReferenceDataMutationVariables
  >(DELETE_REFERENCE_DATA, adminRefetch(type));

  const remove = async (id: string): Promise<string | null> => {
    const result = await mutate({ variables: { id } });
    return result.data?.deleteReferenceData ?? null;
  };

  return { remove, loading, error };
}

export function useToggleReferenceDataActive(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<
    SetReferenceDataActiveMutation,
    SetReferenceDataActiveMutationVariables
  >(SET_REFERENCE_DATA_ACTIVE, adminRefetch(type));

  const setActive = async (
    id: string,
    active: boolean
  ): Promise<ReferenceData | null> => {
    const result = await mutate({ variables: { id, active } });
    return result.data?.setReferenceDataActive ?? null;
  };

  return { setActive, loading, error };
}
