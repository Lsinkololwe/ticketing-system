'use client';

/**
 * React Hooks for Organizations (Admin App)
 *
 * Admin-specific hooks for managing organizations including:
 * - Approvals-workbench queue (pending organizations)
 * - Single-organization lookup
 * - Application review workflow (approve / reject / request changes / suspend / reactivate)
 */

import {
  useQuery,
  useMutation,
} from '@apollo/client/react';
import {
  PENDING_ORGANIZATIONS,
  GET_ORGANIZATION,
} from './organization.queries';
import {
  APPROVE_ORGANIZATION,
  REJECT_ORGANIZATION,
  REQUEST_ORGANIZATION_CHANGES,
  SUSPEND_ORGANIZATION,
  REACTIVATE_ORGANIZATION,
} from './organization.mutations';
import type {
  PendingOrganizationsQuery,
  PendingOrganizationsQueryVariables,
  GetOrganizationQuery,
  GetOrganizationQueryVariables,
  ApproveOrganizationMutation,
  ApproveOrganizationMutationVariables,
  RejectOrganizationMutation,
  RejectOrganizationMutationVariables,
  RequestOrganizationChangesMutation,
  RequestOrganizationChangesMutationVariables,
  SuspendOrganizationMutation,
  SuspendOrganizationMutationVariables,
  UnsuspendOrganizationMutation,
  UnsuspendOrganizationMutationVariables,
} from '../../../../types/graphql';

/**
 * The organization detail shape every mutation and the single-organization
 * query return — all five select the same `AdminOrganizationFields` fragment,
 * so `GetOrganizationQuery`'s row type describes them all.
 */
export type AdminOrganizationDetail = NonNullable<GetOrganizationQuery['organization']>;

/** The row shape the approvals queue actually selects — see `PENDING_ORGANIZATIONS`. */
export type PendingOrganizationRow = PendingOrganizationsQuery['organizations']['content'][number];

// ==========================================
// Admin Query Hooks
// ==========================================

/**
 * Hook to fetch pending organizations (admin)
 */
export function usePendingOrganizations(pagination?: {
  page?: number;
  size?: number;
}) {
  const { data, loading, error, refetch } = useQuery<
    PendingOrganizationsQuery,
    PendingOrganizationsQueryVariables
  >(PENDING_ORGANIZATIONS, {
    variables: {
      pagination: {
        page: pagination?.page ?? 0,
        size: pagination?.size ?? 20,
        // Oldest first: the workbench is a queue, and the application that has
        // waited longest is the one closest to breaching its SLA.
        sortBy: 'submittedAt',
        sortDirection: 'ASC',
      },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.organizations;
  const info = page?.pageInfo;
  const size = info?.pageSize ?? 20;
  const total = info?.totalCount ?? 0;

  return {
    organizations: page?.content ?? [],
    totalElements: total,
    totalPages: size > 0 ? Math.ceil(total / size) : 0,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch a single organization by ID (admin)
 */
export function useOrganization(id: string | null) {
  const { data, loading, error, refetch } = useQuery<
    GetOrganizationQuery,
    GetOrganizationQueryVariables
  >(GET_ORGANIZATION, {
    variables: { id: id ?? '' },
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    organization: data?.organization || null,
    loading,
    error,
    refetch,
  };
}

// ==========================================
// Admin Mutation Hooks
// ==========================================

/**
 * Hook to approve an organization (admin)
 */
export function useApproveOrganization() {
  const [approveMutation, { data, loading, error }] = useMutation<
    ApproveOrganizationMutation,
    ApproveOrganizationMutationVariables
  >(APPROVE_ORGANIZATION, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_ORGANIZATIONS }],
    awaitRefetchQueries: true,
  });

  const approve = async (id: string, commissionRate?: number | null): Promise<AdminOrganizationDetail | null> => {
    const result = await approveMutation({ variables: { id, commissionRate: commissionRate ?? null } });
    return result.data?.approveOrganization || null;
  };

  return {
    approve,
    organization: data?.approveOrganization || null,
    loading,
    error,
  };
}

/**
 * Hook to reject an organization (admin)
 */
export function useRejectOrganization() {
  const [rejectMutation, { data, loading, error }] = useMutation<
    RejectOrganizationMutation,
    RejectOrganizationMutationVariables
  >(REJECT_ORGANIZATION, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_ORGANIZATIONS }],
    awaitRefetchQueries: true,
  });

  const reject = async (
    id: string,
    reason: string
  ): Promise<AdminOrganizationDetail | null> => {
    const result = await rejectMutation({ variables: { id, reason } });
    return result.data?.rejectOrganization || null;
  };

  return {
    reject,
    organization: data?.rejectOrganization || null,
    loading,
    error,
  };
}

/**
 * Hook to request changes to an organization (admin)
 */
export function useRequestOrganizationChanges() {
  const [requestMutation, { data, loading, error }] = useMutation<
    RequestOrganizationChangesMutation,
    RequestOrganizationChangesMutationVariables
  >(REQUEST_ORGANIZATION_CHANGES, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_ORGANIZATIONS }],
    awaitRefetchQueries: true,
  });

  const requestChanges = async (
    id: string,
    reason: string
  ): Promise<AdminOrganizationDetail | null> => {
    const result = await requestMutation({ variables: { id, reason } });
    return result.data?.requestOrganizationChanges || null;
  };

  return {
    requestChanges,
    organization: data?.requestOrganizationChanges || null,
    loading,
    error,
  };
}

/**
 * Hook to suspend an organization (admin)
 */
export function useSuspendOrganization() {
  const [suspendMutation, { data, loading, error }] = useMutation<
    SuspendOrganizationMutation,
    SuspendOrganizationMutationVariables
  >(SUSPEND_ORGANIZATION, {
    errorPolicy: 'all',
  });

  const suspend = async (
    id: string,
    reason: string
  ): Promise<AdminOrganizationDetail | null> => {
    const result = await suspendMutation({ variables: { id, reason } });
    return result.data?.suspendOrganization || null;
  };

  return {
    suspend,
    organization: data?.suspendOrganization || null,
    loading,
    error,
  };
}

/**
 * Hook to reactivate a suspended organization (admin)
 */
export function useReactivateOrganization() {
  const [reactivateMutation, { data, loading, error }] = useMutation<
    UnsuspendOrganizationMutation,
    UnsuspendOrganizationMutationVariables
  >(REACTIVATE_ORGANIZATION, {
    errorPolicy: 'all',
  });

  const reactivate = async (id: string): Promise<AdminOrganizationDetail | null> => {
    const result = await reactivateMutation({ variables: { id } });
    return result.data?.unsuspendOrganization || null;
  };

  return {
    reactivate,
    organization: data?.unsuspendOrganization || null,
    loading,
    error,
  };
}

// ==========================================
// Shorter Name Used by the Approvals Screen
// ==========================================

/**
 * Alias for useReactivateOrganization
 * @deprecated Use useReactivateOrganization instead
 */
export function useUnsuspendOrganization() {
  const result = useReactivateOrganization();
  return {
    ...result,
    unsuspend: result.reactivate,
  };
}
