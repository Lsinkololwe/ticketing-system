/**
 * Apollo Query Keys for Documents
 *
 * Centralized query key factory for document-related queries.
 * This ensures consistent cache management and invalidation.
 *
 * Note: Apollo Client uses query documents as cache keys, but we
 * maintain this pattern for consistency with React Query patterns
 * and for potential future migration.
 */

import type { DocumentStatus } from './document.types';

export const documentQueryKeys = {
  // ==========================================
  // Base Keys
  // ==========================================

  /**
   * Base key for all document queries
   */
  all: ['documents'] as const,

  /**
   * All document lists (for bulk invalidation)
   */
  lists: () => [...documentQueryKeys.all, 'list'] as const,

  /**
   * All document details (for bulk invalidation)
   */
  details: () => [...documentQueryKeys.all, 'detail'] as const,

  // ==========================================
  // Current User's Documents (Organizer)
  // ==========================================

  /**
   * Current user's verification documents
   */
  mine: {
    all: () => [...documentQueryKeys.all, 'mine'] as const,

    /**
     * All my documents
     */
    list: (status?: DocumentStatus) =>
      [...documentQueryKeys.mine.all(), 'list', status] as const,

    /**
     * My document by type
     */
    byType: (documentType: string) =>
      [...documentQueryKeys.mine.all(), 'type', documentType] as const,

    /**
     * My document count
     */
    count: () => [...documentQueryKeys.mine.all(), 'count'] as const,

    /**
     * My approved document count
     */
    approvedCount: () =>
      [...documentQueryKeys.mine.all(), 'approved-count'] as const,
  },

  // ==========================================
  // Single Document Keys
  // ==========================================

  /**
   * Single document by ID
   */
  detail: (id: string) => [...documentQueryKeys.details(), id] as const,

  // ==========================================
  // Admin Keys
  // ==========================================

  /**
   * Admin document management keys
   */
  admin: {
    all: () => [...documentQueryKeys.all, 'admin'] as const,

    /**
     * Documents by organization (admin view)
     */
    byOrganization: (organizationId: string, status?: DocumentStatus) =>
      [...documentQueryKeys.admin.all(), 'organization', organizationId, status] as const,

    /**
     * Pending documents queue (admin)
     */
    pending: () => [...documentQueryKeys.admin.all(), 'pending'] as const,
  },

  // ==========================================
  // Upload Keys
  // ==========================================

  /**
   * Upload-related keys
   */
  upload: {
    all: () => [...documentQueryKeys.all, 'upload'] as const,

    /**
     * Upload progress tracking
     */
    progress: (uploadId: string) =>
      [...documentQueryKeys.upload.all(), 'progress', uploadId] as const,
  },
};

// ==========================================
// Invalidation Helpers
// ==========================================

/**
 * Get all keys to invalidate when a document is uploaded
 */
export function getDocumentUploadInvalidationKeys(
  organizationId?: string
): (readonly unknown[])[] {
  const keys: (readonly unknown[])[] = [
    documentQueryKeys.mine.all(),
    documentQueryKeys.admin.pending(),
  ];

  if (organizationId) {
    keys.push(documentQueryKeys.admin.byOrganization(organizationId));
  }

  return keys;
}

/**
 * Get all keys to invalidate when a document is reviewed
 */
export function getDocumentReviewInvalidationKeys(
  documentId: string,
  organizationId?: string
): (readonly unknown[])[] {
  const keys: (readonly unknown[])[] = [
    documentQueryKeys.detail(documentId),
    documentQueryKeys.mine.all(),
    documentQueryKeys.admin.pending(),
  ];

  if (organizationId) {
    keys.push(documentQueryKeys.admin.byOrganization(organizationId));
  }

  return keys;
}

/**
 * Get all keys to invalidate when a document is deleted
 */
export function getDocumentDeleteInvalidationKeys(
  organizationId?: string
): (readonly unknown[])[] {
  const keys: (readonly unknown[])[] = [
    documentQueryKeys.lists(),
    documentQueryKeys.mine.all(),
  ];

  if (organizationId) {
    keys.push(documentQueryKeys.admin.byOrganization(organizationId));
  }

  return keys;
}
