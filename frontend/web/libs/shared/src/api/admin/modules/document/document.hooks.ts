'use client';

/**
 * React Hooks for Documents
 *
 * Comprehensive hooks for the verification document domain supporting
 * document upload, review, and management workflows.
 *
 * Note: File uploads use REST API for better progress tracking and
 * multipart form data support. See useDocumentUpload for details.
 */

import { useState, useCallback } from 'react';
import {
  useQuery,
  useMutation,
  useLazyQuery,
} from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_VERIFICATION_DOCUMENTS,
  MY_VERIFICATION_DOCUMENT_BY_TYPE,
  MY_VERIFICATION_DOCUMENT_COUNT,
  MY_APPROVED_DOCUMENT_COUNT,
  GET_VERIFICATION_DOCUMENT,
  VERIFICATION_DOCUMENTS_BY_ORGANIZATION,
  PENDING_VERIFICATION_DOCUMENTS,
  MY_DOCUMENT_SUMMARY,
  ORGANIZATION_DOCUMENT_SUMMARY,
} from './document.queries';
import {
  APPROVE_VERIFICATION_DOCUMENT,
  REJECT_VERIFICATION_DOCUMENT,
  DELETE_VERIFICATION_DOCUMENT,
  VERIFY_ORGANIZATION_DOCUMENTS_SIMPLE,
} from './document.mutations';
import type {
  VerificationDocument,
  DocumentStatus,
  VerificationDocumentUploadResponse,
} from './document.types';

// ==========================================
// Upload Configuration
// ==========================================

/**
 * REST API base URL for document uploads
 */
const DOCUMENT_UPLOAD_API_URL =
  process.env.NEXT_PUBLIC_IDENTITY_SERVICE_URL || 'http://localhost:8083';

/**
 * Upload progress state
 */
export interface UploadProgress {
  percent: number;
  loaded: number;
  total: number;
}

/**
 * Upload state
 */
export interface UploadState {
  isUploading: boolean;
  progress: UploadProgress;
  error: Error | null;
  document: VerificationDocument | null;
}

// ==========================================
// Organizer Query Hooks
// ==========================================

/**
 * Hook to fetch current user's verification documents
 */
export function useMyVerificationDocuments(
  status?: DocumentStatus,
  options?: { fetchPolicy?: FetchPolicy }
) {
  const { data, loading, error, refetch } = useQuery<{
    myVerificationDocuments: VerificationDocument[];
  }>(MY_VERIFICATION_DOCUMENTS, {
    variables: { status },
    fetchPolicy: options?.fetchPolicy || 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    documents: data?.myVerificationDocuments || [],
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch a specific document type for current user
 */
export function useMyVerificationDocumentByType(documentType: string | null) {
  const { data, loading, error, refetch } = useQuery<{
    myVerificationDocumentByType: VerificationDocument | null;
  }>(MY_VERIFICATION_DOCUMENT_BY_TYPE, {
    variables: { documentType },
    skip: !documentType,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    document: data?.myVerificationDocumentByType || null,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to get document counts for current user
 */
export function useMyDocumentCounts() {
  const { data: totalData, loading: totalLoading } = useQuery<{
    myVerificationDocumentCount: number;
  }>(MY_VERIFICATION_DOCUMENT_COUNT, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  const { data: approvedData, loading: approvedLoading } = useQuery<{
    myApprovedDocumentCount: number;
  }>(MY_APPROVED_DOCUMENT_COUNT, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    totalCount: totalData?.myVerificationDocumentCount || 0,
    approvedCount: approvedData?.myApprovedDocumentCount || 0,
    loading: totalLoading || approvedLoading,
  };
}

/**
 * Hook to get document summary for current user
 */
export function useMyDocumentSummary() {
  const { data, loading, error, refetch } = useQuery<{
    allDocuments: VerificationDocument[];
    pendingDocuments: { id: string }[];
    approvedDocuments: { id: string }[];
    rejectedDocuments: { id: string }[];
    totalCount: number;
    approvedCount: number;
  }>(MY_DOCUMENT_SUMMARY, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    documents: data?.allDocuments || [],
    counts: {
      total: data?.totalCount || 0,
      pending: data?.pendingDocuments?.length || 0,
      approved: data?.approvedCount || 0,
      rejected: data?.rejectedDocuments?.length || 0,
    },
    loading,
    error,
    refetch,
  };
}

// ==========================================
// Single Document Hooks
// ==========================================

/**
 * Hook to fetch a single verification document
 */
export function useVerificationDocument(
  id: string | null,
  options?: { fetchPolicy?: FetchPolicy }
) {
  const { data, loading, error, refetch } = useQuery<{
    verificationDocument: VerificationDocument | null;
  }>(GET_VERIFICATION_DOCUMENT, {
    variables: { id },
    skip: !id,
    fetchPolicy: options?.fetchPolicy || 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    document: data?.verificationDocument || null,
    loading,
    error,
    refetch,
  };
}

/**
 * Lazy query hook for fetching document on demand
 */
export function useLazyVerificationDocument() {
  const [fetchDocument, { data, loading, error }] = useLazyQuery<{
    verificationDocument: VerificationDocument | null;
  }>(GET_VERIFICATION_DOCUMENT, {
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });

  const getDocument = async (id: string): Promise<VerificationDocument | null> => {
    const result = await fetchDocument({ variables: { id } });
    return result.data?.verificationDocument || null;
  };

  return {
    getDocument,
    document: data?.verificationDocument || null,
    loading,
    error,
  };
}

// ==========================================
// Admin Query Hooks
// ==========================================

/**
 * Hook to fetch documents by organization (admin)
 */
export function useVerificationDocumentsByOrganization(
  organizationId: string | null,
  status?: DocumentStatus
) {
  const { data, loading, error, refetch } = useQuery<{
    verificationDocuments: VerificationDocument[];
  }>(VERIFICATION_DOCUMENTS_BY_ORGANIZATION, {
    variables: { organizationId, status },
    skip: !organizationId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    documents: data?.verificationDocuments || [],
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch pending verification documents (admin)
 */
export function usePendingVerificationDocuments() {
  const { data, loading, error, refetch } = useQuery<{
    pendingVerificationDocuments: VerificationDocument[];
  }>(PENDING_VERIFICATION_DOCUMENTS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    documents: data?.pendingVerificationDocuments || [],
    loading,
    error,
    refetch,
  };
}

/**
 * Hook to fetch document summary for an organization (admin)
 */
export function useOrganizationDocumentSummary(organizationId: string | null) {
  const { data, loading, error, refetch } = useQuery<{
    allDocuments: VerificationDocument[];
    pendingDocuments: { id: string }[];
    approvedDocuments: { id: string }[];
    rejectedDocuments: { id: string }[];
  }>(ORGANIZATION_DOCUMENT_SUMMARY, {
    variables: { organizationId },
    skip: !organizationId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });

  return {
    documents: data?.allDocuments || [],
    counts: {
      total: data?.allDocuments?.length || 0,
      pending: data?.pendingDocuments?.length || 0,
      approved: data?.approvedDocuments?.length || 0,
      rejected: data?.rejectedDocuments?.length || 0,
    },
    loading,
    error,
    refetch,
  };
}

// ==========================================
// Document Upload Hook (REST API)
// ==========================================

/**
 * Hook for uploading verification documents via REST API
 *
 * Uses REST instead of GraphQL for:
 * - Better upload progress tracking
 * - Native multipart form data support
 * - Streaming large files
 */
export function useDocumentUpload(options?: {
  onSuccess?: (document: VerificationDocument) => void;
  onError?: (error: Error) => void;
  onProgress?: (progress: UploadProgress) => void;
}) {
  const [state, setState] = useState<UploadState>({
    isUploading: false,
    progress: { percent: 0, loaded: 0, total: 0 },
    error: null,
    document: null,
  });

  const upload = useCallback(
    async (
      file: File,
      documentType: string,
      accessToken: string
    ): Promise<VerificationDocument | null> => {
      setState((prev) => ({
        ...prev,
        isUploading: true,
        progress: { percent: 0, loaded: 0, total: file.size },
        error: null,
        document: null,
      }));

      return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        const formData = new FormData();

        formData.append('file', file);
        formData.append('documentType', documentType);

        // Track upload progress
        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) {
            const progress: UploadProgress = {
              percent: Math.round((event.loaded / event.total) * 100),
              loaded: event.loaded,
              total: event.total,
            };
            setState((prev) => ({ ...prev, progress }));
            options?.onProgress?.(progress);
          }
        });

        // Handle completion
        xhr.addEventListener('load', () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              const response: VerificationDocumentUploadResponse = JSON.parse(
                xhr.responseText
              );

              if (response.success && response.document) {
                setState((prev) => ({
                  ...prev,
                  isUploading: false,
                  document: response.document!,
                }));
                options?.onSuccess?.(response.document);
                resolve(response.document);
              } else {
                const error = new Error(
                  response.message || 'Upload failed'
                );
                setState((prev) => ({
                  ...prev,
                  isUploading: false,
                  error,
                }));
                options?.onError?.(error);
                reject(error);
              }
            } catch (parseError) {
              const error = new Error('Failed to parse response');
              setState((prev) => ({
                ...prev,
                isUploading: false,
                error,
              }));
              options?.onError?.(error);
              reject(error);
            }
          } else {
            const error = new Error(`Upload failed with status ${xhr.status}`);
            setState((prev) => ({
              ...prev,
              isUploading: false,
              error,
            }));
            options?.onError?.(error);
            reject(error);
          }
        });

        // Handle errors
        xhr.addEventListener('error', () => {
          const error = new Error('Network error during upload');
          setState((prev) => ({
            ...prev,
            isUploading: false,
            error,
          }));
          options?.onError?.(error);
          reject(error);
        });

        // Handle abort
        xhr.addEventListener('abort', () => {
          const error = new Error('Upload cancelled');
          setState((prev) => ({
            ...prev,
            isUploading: false,
            error,
          }));
          options?.onError?.(error);
          reject(error);
        });

        // Send request
        xhr.open('POST', `${DOCUMENT_UPLOAD_API_URL}/api/documents/upload`);
        xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`);
        xhr.send(formData);
      });
    },
    [options]
  );

  const reset = useCallback(() => {
    setState({
      isUploading: false,
      progress: { percent: 0, loaded: 0, total: 0 },
      error: null,
      document: null,
    });
  }, []);

  return {
    upload,
    reset,
    ...state,
  };
}

// ==========================================
// Admin Mutation Hooks
// ==========================================

/**
 * Hook to approve a verification document (admin)
 */
export function useApproveVerificationDocument() {
  const [approveMutation, { data, loading, error }] = useMutation<{
    approveVerificationDocument: VerificationDocument;
  }>(APPROVE_VERIFICATION_DOCUMENT, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_VERIFICATION_DOCUMENTS }],
    awaitRefetchQueries: true,
  });

  const approve = async (
    documentId: string
  ): Promise<VerificationDocument | null> => {
    const result = await approveMutation({ variables: { documentId } });
    return result.data?.approveVerificationDocument || null;
  };

  return {
    approve,
    document: data?.approveVerificationDocument || null,
    loading,
    error,
  };
}

/**
 * Hook to reject a verification document (admin)
 */
export function useRejectVerificationDocument() {
  const [rejectMutation, { data, loading, error }] = useMutation<{
    rejectVerificationDocument: VerificationDocument;
  }>(REJECT_VERIFICATION_DOCUMENT, {
    errorPolicy: 'all',
    refetchQueries: [{ query: PENDING_VERIFICATION_DOCUMENTS }],
    awaitRefetchQueries: true,
  });

  const reject = async (
    documentId: string,
    reason: string
  ): Promise<VerificationDocument | null> => {
    const result = await rejectMutation({ variables: { documentId, reason } });
    return result.data?.rejectVerificationDocument || null;
  };

  return {
    reject,
    document: data?.rejectVerificationDocument || null,
    loading,
    error,
  };
}

/**
 * Hook to delete a verification document
 */
export function useDeleteVerificationDocument() {
  const [deleteMutation, { loading, error }] = useMutation<{
    deleteVerificationDocument: { success: boolean; message?: string };
  }>(DELETE_VERIFICATION_DOCUMENT, {
    errorPolicy: 'all',
    refetchQueries: [{ query: MY_VERIFICATION_DOCUMENTS }],
    awaitRefetchQueries: true,
  });

  const deleteDocument = async (documentId: string): Promise<boolean> => {
    const result = await deleteMutation({ variables: { documentId } });
    return result.data?.deleteVerificationDocument?.success || false;
  };

  return {
    deleteDocument,
    loading,
    error,
  };
}

/**
 * Hook to verify organization documents (admin) - simplified version
 */
export function useVerifyOrganizationDocumentsSimple() {
  const [verifyMutation, { data, loading, error }] = useMutation<{
    verifyOrganizationDocuments: {
      id: string;
      documentsVerified: boolean;
      status: string;
    };
  }>(VERIFY_ORGANIZATION_DOCUMENTS_SIMPLE, {
    errorPolicy: 'all',
  });

  const verify = async (
    organizationId: string
  ): Promise<{ documentsVerified: boolean } | null> => {
    const result = await verifyMutation({ variables: { organizationId } });
    return result.data?.verifyOrganizationDocuments || null;
  };

  return {
    verify,
    result: data?.verifyOrganizationDocuments || null,
    loading,
    error,
  };
}

// ==========================================
// Combined Hook for Document Management
// ==========================================

/**
 * Combined hook for managing verification documents
 */
export function useDocumentManagement(options?: {
  onUploadSuccess?: (document: VerificationDocument) => void;
  onUploadError?: (error: Error) => void;
}) {
  const documentsQuery = useMyDocumentSummary();
  const uploadHook = useDocumentUpload({
    onSuccess: (doc) => {
      documentsQuery.refetch();
      options?.onUploadSuccess?.(doc);
    },
    onError: options?.onUploadError,
  });
  const deleteHook = useDeleteVerificationDocument();

  return {
    // Documents
    documents: documentsQuery.documents,
    counts: documentsQuery.counts,
    documentsLoading: documentsQuery.loading,
    documentsError: documentsQuery.error,
    refetchDocuments: documentsQuery.refetch,

    // Upload
    upload: uploadHook.upload,
    uploadProgress: uploadHook.progress,
    isUploading: uploadHook.isUploading,
    uploadError: uploadHook.error,
    uploadedDocument: uploadHook.document,
    resetUpload: uploadHook.reset,

    // Delete
    deleteDocument: deleteHook.deleteDocument,
    isDeleting: deleteHook.loading,
    deleteError: deleteHook.error,
  };
}

// ==========================================
// Compatibility Aliases (for legacy code)
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
