'use client';

/**
 * React Hooks for Organizations (Admin App)
 *
 * Admin-specific hooks for managing organizations including:
 * - Organization list views with pagination
 * - Application review workflow
 * - Organization status management
 * - Document verification
 */

import { useState, useEffect } from 'react';
import {
  useQuery,
  useMutation,
} from '@apollo/client/react';
import {
  ORGANIZATIONS_LIST,
  PENDING_ORGANIZATIONS,
  GET_ORGANIZATION,
  ORGANIZATION_STATISTICS,
} from './organization.queries';
import {
  APPROVE_ORGANIZATION,
  REJECT_ORGANIZATION,
  REQUEST_ORGANIZATION_CHANGES,
  SUSPEND_ORGANIZATION,
  REACTIVATE_ORGANIZATION,
  VERIFY_ORGANIZATION_DOCUMENTS,
  VERIFY_PAYOUT_ACCOUNT,
} from './organization.mutations';
import type {
  Organization,
  OrganizationListItem,
  OrganizationFilters,
  OrganizationStatistics,
} from './organization.types';

// ==========================================
// Result Types
// ==========================================

interface PaginatedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

// ==========================================
// Admin Query Hooks
// ==========================================

/**
 * Hook to fetch organizations list with pagination (admin)
 */
export function useOrganizationsList(
  filter?: OrganizationFilters,
  pagination?: { page?: number; size?: number }
) {
  const { data, loading, error, refetch, fetchMore } = useQuery<{
    organizationsOffsetPagination: PaginatedResult<OrganizationListItem>;
  }>(ORGANIZATIONS_LIST, {
    variables: { filter, pagination },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.organizationsOffsetPagination || {
    content: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 20,
    hasNext: false,
    hasPrevious: false,
  };

  const loadPage = async (pageNumber: number) => {
    return refetch({
      filter,
      pagination: { ...pagination, page: pageNumber },
    });
  };

  return {
    organizations: page.content,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    currentPage: page.page,
    pageSize: page.size,
    hasNext: page.hasNext,
    hasPrevious: page.hasPrevious,
    loading,
    error,
    refetch,
    loadPage,
    fetchMore,
  };
}

/**
 * Hook to fetch pending organizations (admin)
 */
export function usePendingOrganizations(pagination?: {
  page?: number;
  size?: number;
}) {
  const { data, loading, error, refetch } = useQuery<{
    pendingOrganizations: PaginatedResult<OrganizationListItem>;
  }>(PENDING_ORGANIZATIONS, {
    variables: { pagination },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const page = data?.pendingOrganizations || {
    content: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 20,
    hasNext: false,
    hasPrevious: false,
  };

  return {
    organizations: page.content,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch a single organization by ID (admin)
 */
export function useOrganization(id: string | null) {
  const { data, loading, error, refetch } = useQuery<{
    organization: Organization | null;
  }>(GET_ORGANIZATION, {
    variables: { id },
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

/**
 * Hook to fetch organization statistics (admin dashboard)
 */
export function useOrganizationStatistics() {
  const { data, loading, error, refetch } = useQuery<{
    organizationStatistics: OrganizationStatistics;
  }>(ORGANIZATION_STATISTICS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    statistics: data?.organizationStatistics || null,
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
  const [approveMutation, { data, loading, error }] = useMutation<{
    approveOrganization: Organization;
  }>(APPROVE_ORGANIZATION, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: PENDING_ORGANIZATIONS },
      { query: ORGANIZATION_STATISTICS },
    ],
    awaitRefetchQueries: true,
  });

  const approve = async (
    id: string,
    comments?: string
  ): Promise<Organization | null> => {
    const result = await approveMutation({ variables: { id, comments } });
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
  const [rejectMutation, { data, loading, error }] = useMutation<{
    rejectOrganization: Organization;
  }>(REJECT_ORGANIZATION, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: PENDING_ORGANIZATIONS },
      { query: ORGANIZATION_STATISTICS },
    ],
    awaitRefetchQueries: true,
  });

  const reject = async (
    id: string,
    reason: string
  ): Promise<Organization | null> => {
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
  const [requestMutation, { data, loading, error }] = useMutation<{
    requestOrganizationChanges: Organization;
  }>(REQUEST_ORGANIZATION_CHANGES, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: PENDING_ORGANIZATIONS },
      { query: ORGANIZATION_STATISTICS },
    ],
    awaitRefetchQueries: true,
  });

  const requestChanges = async (
    id: string,
    reason: string
  ): Promise<Organization | null> => {
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
  const [suspendMutation, { data, loading, error }] = useMutation<{
    suspendOrganization: Organization;
  }>(SUSPEND_ORGANIZATION, {
    errorPolicy: 'all',
    refetchQueries: [{ query: ORGANIZATION_STATISTICS }],
    awaitRefetchQueries: true,
  });

  const suspend = async (
    id: string,
    reason: string
  ): Promise<Organization | null> => {
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
  const [reactivateMutation, { data, loading, error }] = useMutation<{
    reactivateOrganization: Organization;
  }>(REACTIVATE_ORGANIZATION, {
    errorPolicy: 'all',
    refetchQueries: [{ query: ORGANIZATION_STATISTICS }],
    awaitRefetchQueries: true,
  });

  const reactivate = async (id: string): Promise<Organization | null> => {
    const result = await reactivateMutation({ variables: { id } });
    return result.data?.reactivateOrganization || null;
  };

  return {
    reactivate,
    organization: data?.reactivateOrganization || null,
    loading,
    error,
  };
}

/**
 * Hook to fetch approved organizations (admin)
 */
export function useApprovedOrganizations(pagination?: {
  page?: number;
  size?: number;
}) {
  return useOrganizationsList(
    { status: ['APPROVED', 'ACTIVE'] },
    pagination
  );
}

/**
 * Hook to fetch suspended organizations (admin)
 */
export function useSuspendedOrganizations(pagination?: {
  page?: number;
  size?: number;
}) {
  return useOrganizationsList(
    { status: ['SUSPENDED'] },
    pagination
  );
}

/**
 * Hook to search organizations (admin)
 * Provides a search function and results state
 */
export function useSearchOrganizations() {
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [results, setResults] = useState<OrganizationListItem[]>([]);

  const { data, loading, error, refetch } = useQuery<{
    organizationsOffsetPagination: PaginatedResult<OrganizationListItem>;
  }>(ORGANIZATIONS_LIST, {
    variables: {
      filter: searchQuery ? { search: searchQuery } : undefined,
      pagination: { page: 0, size: 50 },
    },
    skip: !searchQuery,
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });

  useEffect(() => {
    if (data) {
      setResults(data?.organizationsOffsetPagination?.content || []);
    }
  }, [data]);

  const search = async (query: string) => {
    setSearchQuery(query);
    if (query.trim()) {
      await refetch({
        filter: { search: query.trim() },
        pagination: { page: 0, size: 50 },
      });
    } else {
      setResults([]);
    }
  };

  const clearSearch = () => {
    setSearchQuery('');
    setResults([]);
  };

  return {
    search,
    clearSearch,
    results,
    loading,
    error,
    searchQuery,
  };
}

// ==========================================
// Verification Mutation Hooks
// ==========================================

/**
 * Hook to verify organization documents (admin)
 */
export function useVerifyOrganizationDocuments() {
  const [verifyMutation, { data, loading, error }] = useMutation<{
    verifyOrganizationDocuments: Organization;
  }>(VERIFY_ORGANIZATION_DOCUMENTS, {
    errorPolicy: 'all',
  });

  const verify = async (id: string): Promise<Organization | null> => {
    const result = await verifyMutation({ variables: { id } });
    return result.data?.verifyOrganizationDocuments || null;
  };

  return {
    verify,
    organization: data?.verifyOrganizationDocuments || null,
    loading,
    error,
  };
}

/**
 * Hook to verify payout account (admin)
 */
export function useVerifyPayoutAccount() {
  const [verifyMutation, { data, loading, error }] = useMutation<{
    verifyPayoutAccount: Organization;
  }>(VERIFY_PAYOUT_ACCOUNT, {
    errorPolicy: 'all',
  });

  const verify = async (id: string): Promise<Organization | null> => {
    const result = await verifyMutation({ variables: { id } });
    return result.data?.verifyPayoutAccount || null;
  };

  return {
    verify,
    organization: data?.verifyPayoutAccount || null,
    loading,
    error,
  };
}

// ==========================================
// Compatibility Aliases (for legacy code)
// ==========================================

/**
 * Alias for useOrganizationsList
 * @deprecated Use useOrganizationsList instead
 */
export function useOrganizationApplications(
  filter?: OrganizationFilters,
  pagination?: { page?: number; size?: number }
) {
  const result = useOrganizationsList(filter, pagination);
  return {
    ...result,
    applications: result.organizations,
  };
}

/**
 * Alias for usePendingOrganizations
 * @deprecated Use usePendingOrganizations instead
 */
export function usePendingApplications(pagination?: {
  page?: number;
  size?: number;
}) {
  const result = usePendingOrganizations(pagination);
  return {
    ...result,
    applications: result.organizations,
  };
}

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
