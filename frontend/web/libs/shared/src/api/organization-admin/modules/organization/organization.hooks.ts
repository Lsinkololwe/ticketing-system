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
} from './organization.queries';
import {
  APPLY_TO_BE_ORGANIZER,
  UPDATE_ORGANIZATION_APPLICATION,
  SUBMIT_ORGANIZATION_FOR_REVIEW,
} from './organization.mutations';

// Import types from same module
import type { Organization, OrganizationApplicationInput } from './organization.types';
import type {
  MyOrganizationQuery,
  MyOrganizationQueryVariables,
  ApplyToBeOrganizerMutation,
  ApplyToBeOrganizerMutationVariables,
  UpdateOrganizationApplicationMutation,
  UpdateOrganizationApplicationMutationVariables,
  SubmitOrganizationForReviewMutation,
  SubmitOrganizationForReviewMutationVariables,
} from '../../../../types/graphql';

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
  const { data, loading, error, refetch, networkStatus } = useQuery<MyOrganizationQuery, MyOrganizationQueryVariables>(MY_ORGANIZATION, {
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

// ==========================================
// Organizer Self-Service Mutation Hooks
// ==========================================

/**
 * Hook to apply to become an organizer
 * Creates a new organization with status: DRAFT
 */
export function useApplyToBeOrganizer() {
  const [applyMutation, { data, loading, error }] = useMutation<ApplyToBeOrganizerMutation, ApplyToBeOrganizerMutationVariables>(APPLY_TO_BE_ORGANIZER, {
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
  const [updateMutation, { data, loading, error }] = useMutation<UpdateOrganizationApplicationMutation, UpdateOrganizationApplicationMutationVariables>(UPDATE_ORGANIZATION_APPLICATION, {
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
  const [submitMutation, { data, loading, error }] = useMutation<SubmitOrganizationForReviewMutation, SubmitOrganizationForReviewMutationVariables>(SUBMIT_ORGANIZATION_FOR_REVIEW, {
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

