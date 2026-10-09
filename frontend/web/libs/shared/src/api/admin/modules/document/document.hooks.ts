'use client';

/**
 * React Hooks for Documents
 *
 * Hooks for the admin verification-document review queue: list pending
 * documents, approve, and reject.
 *
 * Note: File uploads use REST API for better progress tracking and
 * multipart form data support — see
 * `organization-admin/modules/organization/organization.rest.ts` for the
 * hook that apps actually use.
 */

import {
  useQuery,
  useMutation,
} from '@apollo/client/react';
import {
  PENDING_VERIFICATION_DOCUMENTS,
} from './document.queries';
import {
  APPROVE_VERIFICATION_DOCUMENT,
  REJECT_VERIFICATION_DOCUMENT,
} from './document.mutations';
import type {
  ApproveVerificationDocumentMutation,
  ApproveVerificationDocumentMutationVariables,
  PendingVerificationDocumentsQuery,
  RejectVerificationDocumentMutation,
  RejectVerificationDocumentMutationVariables,
} from '../../../../types/graphql';

// ==========================================
// Admin Query Hooks
// ==========================================

/**
 * Hook to fetch pending verification documents (admin)
 */
export function usePendingVerificationDocuments() {
  const { data, loading, error, refetch } = useQuery<PendingVerificationDocumentsQuery>(
    PENDING_VERIFICATION_DOCUMENTS,
    {
      fetchPolicy: 'cache-and-network',
      errorPolicy: 'all',
    }
  );

  return {
    documents: data?.pendingVerificationDocuments ?? [],
    loading,
    error,
    refetch,
  };
}

// ==========================================
// Admin Mutation Hooks
// ==========================================

/**
 * Hook to approve a verification document (admin)
 */
export function useApproveVerificationDocument() {
  const [approveMutation, { data, loading, error }] = useMutation<
    ApproveVerificationDocumentMutation,
    ApproveVerificationDocumentMutationVariables
  >(APPROVE_VERIFICATION_DOCUMENT, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_VERIFICATION_DOCUMENTS }],
    awaitRefetchQueries: true,
  });

  const approve = async (documentId: string) => {
    const result = await approveMutation({ variables: { documentId } });
    return result.data?.approveVerificationDocument ?? null;
  };

  return {
    approve,
    document: data?.approveVerificationDocument ?? null,
    loading,
    error,
  };
}

/**
 * Hook to reject a verification document (admin)
 */
export function useRejectVerificationDocument() {
  const [rejectMutation, { data, loading, error }] = useMutation<
    RejectVerificationDocumentMutation,
    RejectVerificationDocumentMutationVariables
  >(REJECT_VERIFICATION_DOCUMENT, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_VERIFICATION_DOCUMENTS }],
    awaitRefetchQueries: true,
  });

  const reject = async (documentId: string, reason: string) => {
    const result = await rejectMutation({ variables: { documentId, reason } });
    return result.data?.rejectVerificationDocument ?? null;
  };

  return {
    reject,
    document: data?.rejectVerificationDocument ?? null,
    loading,
    error,
  };
}

// ==========================================
// Shorter Names Used by the Approvals Screen
// ==========================================

/**
 * Alias for usePendingVerificationDocuments
 * @deprecated Use usePendingVerificationDocuments instead
 */
export function usePendingDocuments() {
  return usePendingVerificationDocuments();
}

/**
 * Alias for useApproveVerificationDocument
 * @deprecated Use useApproveVerificationDocument instead
 */
export function useApproveDocument() {
  return useApproveVerificationDocument();
}

/**
 * Alias for useRejectVerificationDocument
 * @deprecated Use useRejectVerificationDocument instead
 */
export function useRejectDocument() {
  return useRejectVerificationDocument();
}
