'use client';

/**
 * React hooks for the Reference Data platform.
 *
 * Reads (`useReferenceData`, `useReferenceDataByParent`) are public — safe for storefront dropdowns.
 * Admin table + mutation hooks power the reference-data management screen. Mutations refetch the
 * affected type's admin page so the table stays consistent.
 */

import {
  useQuery,
  useMutation,
} from '@apollo/client/react';
import {
  REFERENCE_DATA,
  REFERENCE_DATA_BY_PARENT,
  REFERENCE_TYPES,
  REFERENCE_DATA_OFFSET,
} from './reference-data.queries';
import {
  CREATE_REFERENCE_DATA,
  UPDATE_REFERENCE_DATA,
  DELETE_REFERENCE_DATA,
  SET_REFERENCE_DATA_ACTIVE,
} from './reference-data.mutations';
import type {
  CreateReferenceDataInput,
  DeleteMutationResponse,
  ReferenceData,
  ReferenceDataMutationResponse,
  ReferenceDataOffsetPage,
  ReferenceType,
  ReferenceTypeInfo,
  UpdateReferenceDataInput,
} from './reference-data.types';

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
  const { data, loading, error, refetch } = useQuery<{
    referenceData: ReferenceData[];
  }>(REFERENCE_DATA, {
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

/** Child rows within a hierarchy, e.g. genres under a category code. */
export function useReferenceDataByParent(
  type: ReferenceType,
  parentCode: string | null
) {
  const { data, loading, error, refetch } = useQuery<{
    referenceDataByParent: ReferenceData[];
  }>(REFERENCE_DATA_BY_PARENT, {
    variables: { type, parentCode },
    skip: !parentCode,
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });

  return {
    items: data?.referenceDataByParent ?? [],
    loading,
    error,
    refetch,
  };
}

/** The type registry — drives the admin picker and dynamic metadata forms. */
export function useReferenceTypes() {
  const { data, loading, error } = useQuery<{
    referenceTypes: ReferenceTypeInfo[];
  }>(REFERENCE_TYPES, {
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
  const { data, loading, error, refetch } = useQuery<{
    referenceDataOffsetPagination: ReferenceDataOffsetPage;
  }>(REFERENCE_DATA_OFFSET, {
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

  const page = data?.referenceDataOffsetPagination;

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

// ==========================================
// Mutation Hooks (admin)
// ==========================================

function adminRefetch(type: ReferenceType) {
  return {
    refetchQueries: [
      {
        query: REFERENCE_DATA_OFFSET,
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
  const [mutate, { loading, error }] = useMutation<{
    createReferenceData: ReferenceDataMutationResponse;
  }>(CREATE_REFERENCE_DATA, adminRefetch(type));

  const create = async (
    input: CreateReferenceDataInput
  ): Promise<ReferenceDataMutationResponse | null> => {
    const result = await mutate({ variables: { input } });
    return result.data?.createReferenceData ?? null;
  };

  return { create, loading, error };
}

export function useUpdateReferenceData(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<{
    updateReferenceData: ReferenceDataMutationResponse;
  }>(UPDATE_REFERENCE_DATA, adminRefetch(type));

  const update = async (
    id: string,
    input: UpdateReferenceDataInput
  ): Promise<ReferenceDataMutationResponse | null> => {
    const result = await mutate({ variables: { id, input } });
    return result.data?.updateReferenceData ?? null;
  };

  return { update, loading, error };
}

export function useDeleteReferenceData(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<{
    deleteReferenceData: DeleteMutationResponse;
  }>(DELETE_REFERENCE_DATA, adminRefetch(type));

  const remove = async (id: string): Promise<DeleteMutationResponse | null> => {
    const result = await mutate({ variables: { id } });
    return result.data?.deleteReferenceData ?? null;
  };

  return { remove, loading, error };
}

export function useToggleReferenceDataActive(type: ReferenceType) {
  const [mutate, { loading, error }] = useMutation<{
    setReferenceDataActive: ReferenceDataMutationResponse;
  }>(SET_REFERENCE_DATA_ACTIVE, adminRefetch(type));

  const setActive = async (
    id: string,
    active: boolean
  ): Promise<ReferenceDataMutationResponse | null> => {
    const result = await mutate({ variables: { id, active } });
    return result.data?.setReferenceDataActive ?? null;
  };

  return { setActive, loading, error };
}
