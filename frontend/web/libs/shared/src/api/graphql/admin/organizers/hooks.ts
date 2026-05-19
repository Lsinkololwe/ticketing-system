'use client';

/**
 * Admin Organizers - React Hooks
 *
 * React hooks for organizer management in admin dashboard.
 * All types are generated from the supergraph via codegen.
 *
 * Usage:
 * ```tsx
 * import { useOrganizerApplications, useApproveOrganizer } from '@pml.tickets/shared/api/admin';
 *
 * function OrganizersPage() {
 *   const { applications, loading, refetch } = useOrganizerApplications('PENDING_REVIEW');
 *   const { approveOrganizer, loading: approving } = useApproveOrganizer();
 *   // ...
 * }
 * ```
 */

import { useQuery, useMutation, useLazyQuery } from '@apollo/client/react';
import {
  GET_ORGANIZER_APPLICATIONS,
  GET_PENDING_APPLICATIONS,
  GET_APPROVED_ORGANIZERS,
  GET_SUSPENDED_ORGANIZERS,
  GET_REJECTED_ORGANIZERS,
  GET_CHANGES_REQUESTED_ORGANIZERS,
  GET_ORGANIZER_PROFILE,
  GET_ORGANIZER_BY_USER_ID,
  SEARCH_ORGANIZERS,
  GET_ORGANIZER_STATS,
  GET_ORGANIZER_STATISTICS,
  GET_VERIFICATION_DOCUMENTS,
} from './queries';
import {
  APPROVE_ORGANIZER,
  REJECT_ORGANIZER,
  REQUEST_ORGANIZER_CHANGES,
  SUSPEND_ORGANIZER,
  REACTIVATE_ORGANIZER,
  VERIFY_ORGANIZER_BUSINESS,
  VERIFY_ORGANIZER_DOCUMENTS,
  VERIFY_ORGANIZER_BANK_ACCOUNT,
  UPDATE_ORGANIZER_ADMIN_NOTES,
} from './mutations';
import type {
  OrganizerProfile,
  OrganizerApplicationOffsetPage,
  OrganizerStatus,
  OffsetPaginationInput,
  PlatformStatistics,
  OrganizerStatistics,
  VerificationDocument,
  DocumentStatus,
} from '../../../../types/graphql';

// ==================== Query Hooks ====================

/**
 * Hook to fetch organizer applications with offset pagination
 * Supports filtering by status
 */
export function useOrganizerApplications(
  status?: OrganizerStatus,
  pagination?: Partial<OffsetPaginationInput>
) {
  const paginationInput: OffsetPaginationInput = {
    page: pagination?.page ?? 0,
    size: pagination?.size ?? 20,
    sortBy: pagination?.sortBy ?? 'createdAt',
    sortDirection: pagination?.sortDirection ?? 'DESC',
  };

  const { data, loading, error, refetch, networkStatus } = useQuery<{
    organizerApplicationsOffsetPagination: OrganizerApplicationOffsetPage;
  }>(GET_ORGANIZER_APPLICATIONS, {
    variables: { status, pagination: paginationInput },
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  return {
    applications: data?.organizerApplicationsOffsetPagination?.content || [],
    pageInfo: data?.organizerApplicationsOffsetPagination || null,
    totalElements: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalElements ?? 0,
    totalPages: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalPages ?? 0,
    loading,
    error,
    refetch,
    networkStatus,
  };
}

/**
 * Hook to fetch pending organizer applications
 */
export function usePendingApplications(pagination?: Partial<OffsetPaginationInput>) {
  const paginationInput: OffsetPaginationInput = {
    page: pagination?.page ?? 0,
    size: pagination?.size ?? 20,
    sortBy: pagination?.sortBy ?? 'submittedAt',
    sortDirection: pagination?.sortDirection ?? 'ASC', // Oldest first (FIFO)
  };

  const { data, loading, error, refetch, networkStatus } = useQuery<{
    organizerApplicationsOffsetPagination: OrganizerApplicationOffsetPage;
  }>(GET_PENDING_APPLICATIONS, {
    variables: { pagination: paginationInput },
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  return {
    applications: data?.organizerApplicationsOffsetPagination?.content || [],
    pageInfo: data?.organizerApplicationsOffsetPagination || null,
    totalElements: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalElements ?? 0,
    totalPages: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalPages ?? 0,
    loading,
    error,
    refetch,
    networkStatus,
  };
}

/**
 * Hook to fetch approved organizers
 */
export function useApprovedOrganizers(pagination?: Partial<OffsetPaginationInput>) {
  const paginationInput: OffsetPaginationInput = {
    page: pagination?.page ?? 0,
    size: pagination?.size ?? 20,
    sortBy: pagination?.sortBy ?? 'approvedAt',
    sortDirection: pagination?.sortDirection ?? 'DESC',
  };

  const { data, loading, error, refetch, networkStatus } = useQuery<{
    organizerApplicationsOffsetPagination: OrganizerApplicationOffsetPage;
  }>(GET_APPROVED_ORGANIZERS, {
    variables: { pagination: paginationInput },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  return {
    organizers: data?.organizerApplicationsOffsetPagination?.content || [],
    pageInfo: data?.organizerApplicationsOffsetPagination || null,
    totalElements: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalElements ?? 0,
    totalPages: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalPages ?? 0,
    loading,
    error,
    refetch,
    networkStatus,
  };
}

/**
 * Hook to fetch suspended organizers
 */
export function useSuspendedOrganizers(pagination?: Partial<OffsetPaginationInput>) {
  const paginationInput: OffsetPaginationInput = {
    page: pagination?.page ?? 0,
    size: pagination?.size ?? 20,
    sortBy: pagination?.sortBy ?? 'updatedAt',
    sortDirection: pagination?.sortDirection ?? 'DESC',
  };

  const { data, loading, error, refetch, networkStatus } = useQuery<{
    organizerApplicationsOffsetPagination: OrganizerApplicationOffsetPage;
  }>(GET_SUSPENDED_ORGANIZERS, {
    variables: { pagination: paginationInput },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  return {
    organizers: data?.organizerApplicationsOffsetPagination?.content || [],
    pageInfo: data?.organizerApplicationsOffsetPagination || null,
    totalElements: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalElements ?? 0,
    totalPages: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalPages ?? 0,
    loading,
    error,
    refetch,
    networkStatus,
  };
}

/**
 * Hook to fetch rejected organizers
 */
export function useRejectedOrganizers(pagination?: Partial<OffsetPaginationInput>) {
  const paginationInput: OffsetPaginationInput = {
    page: pagination?.page ?? 0,
    size: pagination?.size ?? 20,
    sortBy: pagination?.sortBy ?? 'rejectedAt',
    sortDirection: pagination?.sortDirection ?? 'DESC',
  };

  const { data, loading, error, refetch, networkStatus } = useQuery<{
    organizerApplicationsOffsetPagination: OrganizerApplicationOffsetPage;
  }>(GET_REJECTED_ORGANIZERS, {
    variables: { pagination: paginationInput },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  return {
    organizers: data?.organizerApplicationsOffsetPagination?.content || [],
    pageInfo: data?.organizerApplicationsOffsetPagination || null,
    totalElements: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalElements ?? 0,
    totalPages: data?.organizerApplicationsOffsetPagination?.pageInfo?.totalPages ?? 0,
    loading,
    error,
    refetch,
    networkStatus,
  };
}

/**
 * Hook to fetch a single organizer profile by ID
 */
export function useOrganizerProfile(id: string) {
  const { data, loading, error, refetch } = useQuery<{
    organizerProfile: OrganizerProfile;
  }>(GET_ORGANIZER_PROFILE, {
    variables: { id },
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
    skip: !id,
  });

  return {
    profile: data?.organizerProfile || null,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch organizer profile by user ID
 */
export function useOrganizerByUserId(userId: string) {
  const { data, loading, error, refetch } = useQuery<{
    organizerProfileByUserId: OrganizerProfile;
  }>(GET_ORGANIZER_BY_USER_ID, {
    variables: { userId },
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
    skip: !userId,
  });

  return {
    profile: data?.organizerProfileByUserId || null,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch organizer statistics for dashboard
 */
export function useOrganizerStats() {
  const { data, loading, error, refetch } = useQuery<{
    platformStatistics: PlatformStatistics;
  }>(GET_ORGANIZER_STATS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    stats: data?.platformStatistics || null,
    totalOrganizers: data?.platformStatistics?.totalOrganizers ?? 0,
    pendingApplications: data?.platformStatistics?.pendingOrganizerApplications ?? 0,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch individual organizer statistics
 */
export function useOrganizerStatistics(organizerId: string) {
  const { data, loading, error, refetch } = useQuery<{
    organizerStatistics: OrganizerStatistics;
  }>(GET_ORGANIZER_STATISTICS, {
    variables: { organizerId },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    skip: !organizerId,
  });

  return {
    statistics: data?.organizerStatistics || null,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch verification documents
 */
export function useVerificationDocuments(
  organizerProfileId: string,
  status?: DocumentStatus
) {
  const { data, loading, error, refetch } = useQuery<{
    verificationDocuments: VerificationDocument[];
  }>(GET_VERIFICATION_DOCUMENTS, {
    variables: { organizerProfileId, status },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    skip: !organizerProfileId,
  });

  return {
    documents: data?.verificationDocuments || [],
    loading,
    error,
    refetch,
  };
}

/**
 * Lazy query hook for searching organizers
 */
export function useSearchOrganizers() {
  const [searchQuery, { data, loading, error, called }] = useLazyQuery<{
    searchOrganizersByCompanyName: OrganizerApplicationOffsetPage;
  }>(SEARCH_ORGANIZERS, {
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });

  const search = async (
    companyName: string,
    pagination?: Partial<OffsetPaginationInput>
  ) => {
    const paginationInput: OffsetPaginationInput = {
      page: pagination?.page ?? 0,
      size: pagination?.size ?? 20,
      sortBy: pagination?.sortBy ?? 'companyName',
      sortDirection: pagination?.sortDirection ?? 'ASC',
    };

    return searchQuery({
      variables: { companyName, pagination: paginationInput },
    });
  };

  return {
    search,
    results: data?.searchOrganizersByCompanyName?.content || [],
    totalElements: data?.searchOrganizersByCompanyName?.pageInfo?.totalElements ?? 0,
    loading,
    error,
    called,
  };
}

// ==================== Mutation Hooks ====================

/**
 * Hook to approve an organizer application
 */
export function useApproveOrganizer() {
  const [approveMutation, { data, loading, error }] = useMutation<{
    approveOrganizer: OrganizerProfile;
  }>(APPROVE_ORGANIZER, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: GET_ORGANIZER_APPLICATIONS },
      { query: GET_PENDING_APPLICATIONS },
      { query: GET_APPROVED_ORGANIZERS },
      { query: GET_ORGANIZER_STATS },
    ],
  });

  const approveOrganizer = async (profileId: string) => {
    const result = await approveMutation({ variables: { profileId } });
    return result.data?.approveOrganizer || null;
  };

  return {
    approveOrganizer,
    data: data?.approveOrganizer || null,
    loading,
    error,
  };
}

/**
 * Hook to reject an organizer application
 */
export function useRejectOrganizer() {
  const [rejectMutation, { data, loading, error }] = useMutation<{
    rejectOrganizer: OrganizerProfile;
  }>(REJECT_ORGANIZER, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: GET_ORGANIZER_APPLICATIONS },
      { query: GET_PENDING_APPLICATIONS },
      { query: GET_REJECTED_ORGANIZERS },
      { query: GET_ORGANIZER_STATS },
    ],
  });

  const rejectOrganizer = async (profileId: string, reason: string) => {
    const result = await rejectMutation({ variables: { profileId, reason } });
    return result.data?.rejectOrganizer || null;
  };

  return {
    rejectOrganizer,
    data: data?.rejectOrganizer || null,
    loading,
    error,
  };
}

/**
 * Hook to request changes from an organizer
 */
export function useRequestOrganizerChanges() {
  const [requestChangesMutation, { data, loading, error }] = useMutation<{
    requestOrganizerChanges: OrganizerProfile;
  }>(REQUEST_ORGANIZER_CHANGES, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: GET_ORGANIZER_APPLICATIONS },
      { query: GET_PENDING_APPLICATIONS },
      { query: GET_CHANGES_REQUESTED_ORGANIZERS },
      { query: GET_ORGANIZER_STATS },
    ],
  });

  const requestChanges = async (profileId: string, reason: string) => {
    const result = await requestChangesMutation({ variables: { profileId, reason } });
    return result.data?.requestOrganizerChanges || null;
  };

  return {
    requestChanges,
    data: data?.requestOrganizerChanges || null,
    loading,
    error,
  };
}

/**
 * Hook to suspend an approved organizer
 */
export function useSuspendOrganizer() {
  const [suspendMutation, { data, loading, error }] = useMutation<{
    suspendOrganizer: OrganizerProfile;
  }>(SUSPEND_ORGANIZER, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: GET_ORGANIZER_APPLICATIONS },
      { query: GET_APPROVED_ORGANIZERS },
      { query: GET_SUSPENDED_ORGANIZERS },
      { query: GET_ORGANIZER_STATS },
    ],
  });

  const suspendOrganizer = async (profileId: string, reason: string) => {
    const result = await suspendMutation({ variables: { profileId, reason } });
    return result.data?.suspendOrganizer || null;
  };

  return {
    suspendOrganizer,
    data: data?.suspendOrganizer || null,
    loading,
    error,
  };
}

/**
 * Hook to reactivate a suspended organizer
 */
export function useReactivateOrganizer() {
  const [reactivateMutation, { data, loading, error }] = useMutation<{
    reactivateOrganizer: OrganizerProfile;
  }>(REACTIVATE_ORGANIZER, {
    errorPolicy: 'all',
    refetchQueries: [
      { query: GET_ORGANIZER_APPLICATIONS },
      { query: GET_APPROVED_ORGANIZERS },
      { query: GET_SUSPENDED_ORGANIZERS },
      { query: GET_ORGANIZER_STATS },
    ],
  });

  const reactivateOrganizer = async (profileId: string) => {
    const result = await reactivateMutation({ variables: { profileId } });
    return result.data?.reactivateOrganizer || null;
  };

  return {
    reactivateOrganizer,
    data: data?.reactivateOrganizer || null,
    loading,
    error,
  };
}

// ==================== Verification Hooks ====================

/**
 * Hook to verify organizer business registration
 */
export function useVerifyOrganizerBusiness() {
  const [verifyMutation, { data, loading, error }] = useMutation<{
    verifyOrganizerBusiness: OrganizerProfile;
  }>(VERIFY_ORGANIZER_BUSINESS, {
    errorPolicy: 'all',
    refetchQueries: [{ query: GET_ORGANIZER_STATS }],
  });

  const verifyBusiness = async (profileId: string) => {
    const result = await verifyMutation({ variables: { profileId } });
    return result.data?.verifyOrganizerBusiness || null;
  };

  return {
    verifyBusiness,
    data: data?.verifyOrganizerBusiness || null,
    loading,
    error,
  };
}

/**
 * Hook to verify organizer documents
 */
export function useVerifyOrganizerDocuments() {
  const [verifyMutation, { data, loading, error }] = useMutation<{
    verifyOrganizerDocuments: OrganizerProfile;
  }>(VERIFY_ORGANIZER_DOCUMENTS, {
    errorPolicy: 'all',
    refetchQueries: [{ query: GET_ORGANIZER_STATS }],
  });

  const verifyDocuments = async (profileId: string) => {
    const result = await verifyMutation({ variables: { profileId } });
    return result.data?.verifyOrganizerDocuments || null;
  };

  return {
    verifyDocuments,
    data: data?.verifyOrganizerDocuments || null,
    loading,
    error,
  };
}

/**
 * Hook to verify organizer bank account
 */
export function useVerifyOrganizerBankAccount() {
  const [verifyMutation, { data, loading, error }] = useMutation<{
    verifyOrganizerBankAccount: OrganizerProfile;
  }>(VERIFY_ORGANIZER_BANK_ACCOUNT, {
    errorPolicy: 'all',
    refetchQueries: [{ query: GET_ORGANIZER_STATS }],
  });

  const verifyBankAccount = async (profileId: string) => {
    const result = await verifyMutation({ variables: { profileId } });
    return result.data?.verifyOrganizerBankAccount || null;
  };

  return {
    verifyBankAccount,
    data: data?.verifyOrganizerBankAccount || null,
    loading,
    error,
  };
}

/**
 * Hook to update admin notes for an organizer
 */
export function useUpdateOrganizerAdminNotes() {
  const [updateNotesMutation, { data, loading, error }] = useMutation<{
    updateOrganizerAdminNotes: OrganizerProfile;
  }>(UPDATE_ORGANIZER_ADMIN_NOTES, {
    errorPolicy: 'all',
  });

  const updateAdminNotes = async (profileId: string, notes: string) => {
    const result = await updateNotesMutation({ variables: { profileId, notes } });
    return result.data?.updateOrganizerAdminNotes || null;
  };

  return {
    updateAdminNotes,
    data: data?.updateOrganizerAdminNotes || null,
    loading,
    error,
  };
}

// ==================== Combined Hooks ====================

/**
 * Combined hook for all organizer review actions
 * Useful for the approval dialog component
 */
export function useOrganizerReviewActions() {
  const { approveOrganizer, loading: approving, error: approveError } = useApproveOrganizer();
  const { rejectOrganizer, loading: rejecting, error: rejectError } = useRejectOrganizer();
  const { requestChanges, loading: requesting, error: requestError } = useRequestOrganizerChanges();

  return {
    approveOrganizer,
    rejectOrganizer,
    requestChanges,
    loading: approving || rejecting || requesting,
    error: approveError || rejectError || requestError,
  };
}

/**
 * Combined hook for all verification actions
 */
export function useOrganizerVerificationActions() {
  const { verifyBusiness, loading: verifyingBusiness } = useVerifyOrganizerBusiness();
  const { verifyDocuments, loading: verifyingDocs } = useVerifyOrganizerDocuments();
  const { verifyBankAccount, loading: verifyingBank } = useVerifyOrganizerBankAccount();

  return {
    verifyBusiness,
    verifyDocuments,
    verifyBankAccount,
    loading: verifyingBusiness || verifyingDocs || verifyingBank,
  };
}
