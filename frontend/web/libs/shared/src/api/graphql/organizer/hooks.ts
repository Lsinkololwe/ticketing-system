'use client';

/**
 * Organizer Self-Service - React Hooks
 *
 * React hooks for organizer application and profile management.
 * All types are generated from the supergraph via codegen.
 *
 * Usage:
 * ```tsx
 * import { useMyOrganizerProfile, useCreateOrganizerProfile } from '@pml.tickets/shared/api/graphql/organizer';
 *
 * function ApplyPage() {
 *   const { profile, loading, hasProfile } = useMyOrganizerProfile();
 *   const { createProfile, loading: creating } = useCreateOrganizerProfile();
 *   // ...
 * }
 * ```
 */

import { useQuery, useMutation } from '@apollo/client/react';
import { MY_ORGANIZER_PROFILE, MY_ORGANIZER_STATUS } from './queries';
import {
  CREATE_ORGANIZER_PROFILE,
  UPDATE_ORGANIZER_PROFILE,
  SUBMIT_ORGANIZER_APPLICATION,
  DELETE_ORGANIZER_PROFILE,
} from './mutations';
import type {
  OrganizerProfile,
  OrganizerStatus,
  CreateOrganizerProfileInput,
  UpdateOrganizerProfileInput,
  OrganizerMutationResponse,
} from '../../../types/graphql';

// ==================== Query Hooks ====================

/**
 * Hook to fetch the current user's organizer profile
 * Returns null if user hasn't started an application
 *
 * Features:
 * - Automatic refetch on mount
 * - Cache-first for performance
 * - hasProfile boolean for easy conditional checks
 */
export function useMyOrganizerProfile() {
  const { data, loading, error, refetch, networkStatus } = useQuery<{
    myOrganizerProfile: OrganizerProfile | null;
  }>(MY_ORGANIZER_PROFILE, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  const profile = data?.myOrganizerProfile || null;

  return {
    profile,
    hasProfile: profile !== null,
    status: profile?.status as OrganizerStatus | null,
    loading,
    error,
    refetch,
    networkStatus,
  };
}

/**
 * Lightweight hook for just checking organizer status
 * Use this for routing decisions where you don't need full profile
 */
export function useMyOrganizerStatus() {
  const { data, loading, error, refetch } = useQuery<{
    myOrganizerProfile: Pick<OrganizerProfile, 'id' | 'status' | 'statusReason' | 'companyName' | 'submittedAt' | 'approvedAt'> | null;
  }>(MY_ORGANIZER_STATUS, {
    fetchPolicy: 'network-only', // Always fresh for routing
    errorPolicy: 'all',
  });

  const profile = data?.myOrganizerProfile || null;

  return {
    hasProfile: profile !== null,
    status: profile?.status as OrganizerStatus | null,
    statusReason: profile?.statusReason || null,
    companyName: profile?.companyName || null,
    submittedAt: profile?.submittedAt || null,
    approvedAt: profile?.approvedAt || null,
    loading,
    error,
    refetch,
  };
}

// ==================== Mutation Hooks ====================

/**
 * Hook to create a new organizer profile
 * Starts the application process with status: DRAFT
 *
 * Automatically refetches myOrganizerProfile after creation
 */
export function useCreateOrganizerProfile() {
  const [createMutation, { data, loading, error }] = useMutation<{
    createOrganizerProfile: OrganizerProfile;
  }>(CREATE_ORGANIZER_PROFILE, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZER_PROFILE }],
    awaitRefetchQueries: true,
  });

  const createProfile = async (input: CreateOrganizerProfileInput) => {
    const result = await createMutation({ variables: { input } });
    return result.data?.createOrganizerProfile || null;
  };

  return {
    createProfile,
    profile: data?.createOrganizerProfile || null,
    loading,
    error,
  };
}

/**
 * Hook to update the organizer profile
 * Can be used in DRAFT, PENDING_DOCUMENTS, and CHANGES_REQUESTED statuses
 *
 * Returns success/error status from backend
 */
export function useUpdateOrganizerProfile() {
  const [updateMutation, { data, loading, error }] = useMutation<{
    updateOrganizerProfile: OrganizerMutationResponse;
  }>(UPDATE_ORGANIZER_PROFILE, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZER_PROFILE }],
    awaitRefetchQueries: true,
  });

  const updateProfile = async (input: UpdateOrganizerProfileInput) => {
    const result = await updateMutation({ variables: { input } });
    return result.data?.updateOrganizerProfile || null;
  };

  return {
    updateProfile,
    response: data?.updateOrganizerProfile || null,
    success: data?.updateOrganizerProfile?.success ?? false,
    message: data?.updateOrganizerProfile?.message || null,
    loading,
    error,
  };
}

/**
 * Hook to submit the organizer application for review
 * Transitions status to PENDING_REVIEW
 *
 * Prerequisites:
 * - All required fields must be filled
 * - Status must be DRAFT or PENDING_DOCUMENTS
 */
export function useSubmitOrganizerApplication() {
  const [submitMutation, { data, loading, error }] = useMutation<{
    submitOrganizerProfileForReview: OrganizerProfile;
  }>(SUBMIT_ORGANIZER_APPLICATION, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZER_PROFILE }],
    awaitRefetchQueries: true,
  });

  const submitApplication = async () => {
    const result = await submitMutation();
    return result.data?.submitOrganizerProfileForReview || null;
  };

  return {
    submitApplication,
    profile: data?.submitOrganizerProfileForReview || null,
    loading,
    error,
  };
}

/**
 * Hook to delete the organizer profile
 * Only allowed in DRAFT status
 */
export function useDeleteOrganizerProfile() {
  const [deleteMutation, { data, loading, error }] = useMutation<{
    deleteOrganizerProfile: boolean;
  }>(DELETE_ORGANIZER_PROFILE, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZER_PROFILE }],
    awaitRefetchQueries: true,
  });

  const deleteProfile = async () => {
    const result = await deleteMutation();
    return result.data?.deleteOrganizerProfile ?? false;
  };

  return {
    deleteProfile,
    success: data?.deleteOrganizerProfile ?? false,
    loading,
    error,
  };
}

// ==================== Combined Hooks ====================

/**
 * Combined hook for the entire application flow
 * Provides all operations needed for the apply wizard
 */
export function useOrganizerApplication() {
  const profileQuery = useMyOrganizerProfile();
  const createMutation = useCreateOrganizerProfile();
  const updateMutation = useUpdateOrganizerProfile();
  const submitMutation = useSubmitOrganizerApplication();

  const isLoading =
    profileQuery.loading ||
    createMutation.loading ||
    updateMutation.loading ||
    submitMutation.loading;

  return {
    // Profile state
    profile: profileQuery.profile,
    hasProfile: profileQuery.hasProfile,
    status: profileQuery.status,
    profileLoading: profileQuery.loading,
    profileError: profileQuery.error,
    refetchProfile: profileQuery.refetch,

    // Create operation
    createProfile: createMutation.createProfile,
    creating: createMutation.loading,
    createError: createMutation.error,

    // Update operation
    updateProfile: updateMutation.updateProfile,
    updating: updateMutation.loading,
    updateError: updateMutation.error,
    updateSuccess: updateMutation.success,
    updateMessage: updateMutation.message,

    // Submit operation
    submitApplication: submitMutation.submitApplication,
    submitting: submitMutation.loading,
    submitError: submitMutation.error,

    // Combined loading state
    isLoading,
  };
}

// ==================== Helper Functions ====================

/**
 * Check if user can edit their profile based on status
 */
export function canEditProfile(status: OrganizerStatus | null): boolean {
  if (!status) return false;
  return ['DRAFT', 'PENDING_DOCUMENTS', 'CHANGES_REQUESTED', 'APPROVED'].includes(status);
}

/**
 * Check if user can submit their application for review
 */
export function canSubmitForReview(status: OrganizerStatus | null): boolean {
  if (!status) return false;
  return ['DRAFT', 'PENDING_DOCUMENTS', 'CHANGES_REQUESTED'].includes(status);
}

/**
 * Check if user can create events
 */
export function canCreateEvents(status: OrganizerStatus | null): boolean {
  return status === 'APPROVED';
}

/**
 * Get the next route based on organizer status
 * Used for post-login routing
 */
export function getRouteForStatus(status: OrganizerStatus | null, hasProfile: boolean): string {
  if (!hasProfile) {
    return '/apply';
  }

  switch (status) {
    case 'DRAFT':
    case 'PENDING_DOCUMENTS':
      return '/apply/business-info';
    case 'CHANGES_REQUESTED':
      return '/apply/business-info';
    case 'PENDING_REVIEW':
      return '/status';
    case 'APPROVED':
      return '/dashboard';
    case 'REJECTED':
      return '/status';
    case 'SUSPENDED':
      return '/status';
    default:
      return '/apply';
  }
}
