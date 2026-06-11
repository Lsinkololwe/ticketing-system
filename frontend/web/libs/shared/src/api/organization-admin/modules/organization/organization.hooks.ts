'use client';

/**
 * React Hooks for Organization Self-Service (Organization Admin App)
 *
 * Hooks for the organizer application workflow and organization management.
 * These hooks are used by organizers to manage their own organization.
 */

import {
  useQuery,
  useMutation,
} from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_ORGANIZATION,
  MY_ORGANIZATION_STATUS,
  IS_SLUG_AVAILABLE,
} from './organization.queries';
import {
  APPLY_TO_BE_ORGANIZER,
  UPDATE_ORGANIZATION_APPLICATION,
  SUBMIT_ORGANIZATION_FOR_REVIEW,
  UPDATE_ORGANIZATION_SETTINGS,
} from './organization.mutations';

// Import types from same module
import type {
  Organization,
  OrganizationStatusInfo,
  OrganizationApplicationInput,
  OrganizationSettingsInput,
} from './organization.types';

// ==========================================
// Current User Organization Hooks
// ==========================================

/**
 * Hook to fetch the current user's organization
 * Returns null if user hasn't started an application
 *
 * @param options.fetchPolicy - Apollo fetch policy (default: 'cache-and-network')
 * @param options.skip - Skip the query (use when user is not authenticated)
 */
export function useMyOrganization(options?: {
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}) {
  const { data, loading, error, refetch, networkStatus } = useQuery<{
    myOwnedOrganization: Organization | null;
  }>(MY_ORGANIZATION, {
    fetchPolicy: options?.fetchPolicy || 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: options?.skip ?? false,
  });

  const organization = data?.myOwnedOrganization || null;

  return {
    organization,
    hasOrganization: organization !== null,
    status: organization?.status || null,
    loading: options?.skip ? false : loading,
    error: options?.skip ? undefined : error,
    refetch,
    networkStatus,
  };
}

/**
 * Lightweight hook for just checking organization status
 * Use this for routing decisions where you don't need full organization data
 */
export function useMyOrganizationStatus() {
  const { data, loading, error, refetch } = useQuery<{
    myOwnedOrganization: OrganizationStatusInfo | null;
  }>(MY_ORGANIZATION_STATUS, {
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });

  const organization = data?.myOwnedOrganization || null;

  return {
    hasOrganization: organization !== null,
    status: organization?.status || null,
    name: organization?.name || null,
    submittedAt: organization?.submittedAt || null,
    approvedAt: organization?.approvedAt || null,
    canSubmitForReview: organization?.canSubmitForReview || false,
    isApproved: organization?.isApproved || false,
    loading,
    error,
    refetch,
  };
}

// ==========================================
// Lookup Hooks
// ==========================================

/**
 * Hook to check if a slug is available
 */
export function useIsSlugAvailable(slug: string | null) {
  const { data, loading, error, refetch } = useQuery<{
    isSlugAvailable: boolean;
  }>(IS_SLUG_AVAILABLE, {
    variables: { slug },
    skip: !slug || slug.length < 3,
    fetchPolicy: 'network-only',
  });

  return {
    isAvailable: data?.isSlugAvailable ?? null,
    loading,
    error,
    refetch,
  };
}

// ==========================================
// Organizer Self-Service Mutation Hooks
// ==========================================

/**
 * Hook to apply to become an organizer
 * Creates a new organization with status: DRAFT
 */
export function useApplyToBeOrganizer() {
  const [applyMutation, { data, loading, error }] = useMutation<{
    applyToBeOrganizer: Organization;
  }>(APPLY_TO_BE_ORGANIZER, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZATION }],
    awaitRefetchQueries: true,
  });

  const apply = async (
    input: OrganizationApplicationInput
  ): Promise<Organization | null> => {
    const result = await applyMutation({ variables: { input } });
    return result.data?.applyToBeOrganizer || null;
  };

  return {
    apply,
    organization: data?.applyToBeOrganizer || null,
    loading,
    error,
  };
}

/**
 * Hook to update organization application details
 * Only allowed when status is DRAFT or CHANGES_REQUESTED
 */
export function useUpdateOrganizationApplication() {
  const [updateMutation, { data, loading, error }] = useMutation<{
    updateOrganizationApplication: Organization;
  }>(UPDATE_ORGANIZATION_APPLICATION, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZATION }],
    awaitRefetchQueries: true,
  });

  const update = async (
    id: string,
    input: OrganizationApplicationInput
  ): Promise<Organization | null> => {
    const result = await updateMutation({ variables: { id, input } });
    return result.data?.updateOrganizationApplication || null;
  };

  return {
    update,
    organization: data?.updateOrganizationApplication || null,
    loading,
    error,
  };
}

/**
 * Hook to submit the organization application for review
 * Transitions status to PENDING_REVIEW
 */
export function useSubmitOrganizationForReview() {
  const [submitMutation, { data, loading, error }] = useMutation<{
    submitOrganizationForReview: Organization;
  }>(SUBMIT_ORGANIZATION_FOR_REVIEW, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZATION }],
    awaitRefetchQueries: true,
  });

  const submit = async (organizationId: string): Promise<Organization | null> => {
    const result = await submitMutation({ variables: { id: organizationId } });
    return result.data?.submitOrganizationForReview || null;
  };

  return {
    submit,
    organization: data?.submitOrganizationForReview || null,
    loading,
    error,
  };
}

/**
 * Hook to update organization settings (for approved organizations)
 */
export function useUpdateOrganizationSettings() {
  const [updateMutation, { data, loading, error }] = useMutation<{
    updateOrganizationSettings: Organization;
  }>(UPDATE_ORGANIZATION_SETTINGS, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_ORGANIZATION }],
    awaitRefetchQueries: true,
  });

  const update = async (
    id: string,
    input: OrganizationSettingsInput
  ): Promise<Organization | null> => {
    const result = await updateMutation({ variables: { id, input } });
    return result.data?.updateOrganizationSettings || null;
  };

  return {
    update,
    organization: data?.updateOrganizationSettings || null,
    loading,
    error,
  };
}

// ==========================================
// Combined Application Hook
// ==========================================

/**
 * Combined hook for the entire organization application flow
 * Provides all operations needed for the apply wizard
 */
export function useOrganizationApplication() {
  const organizationQuery = useMyOrganization();
  const applyMutation = useApplyToBeOrganizer();
  const updateMutation = useUpdateOrganizationApplication();
  const submitMutation = useSubmitOrganizationForReview();

  const isLoading =
    organizationQuery.loading ||
    applyMutation.loading ||
    updateMutation.loading ||
    submitMutation.loading;

  return {
    // Organization state
    organization: organizationQuery.organization,
    hasOrganization: organizationQuery.hasOrganization,
    status: organizationQuery.status,
    organizationLoading: organizationQuery.loading,
    organizationError: organizationQuery.error,
    refetchOrganization: organizationQuery.refetch,

    // Apply operation
    apply: applyMutation.apply,
    applying: applyMutation.loading,
    applyError: applyMutation.error,

    // Update operation
    update: updateMutation.update,
    updating: updateMutation.loading,
    updateError: updateMutation.error,

    // Submit operation
    submit: submitMutation.submit,
    submitting: submitMutation.loading,
    submitError: submitMutation.error,

    // Combined loading state
    isLoading,
  };
}
