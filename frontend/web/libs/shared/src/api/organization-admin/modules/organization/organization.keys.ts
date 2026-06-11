/**
 * Apollo Query Keys for Organizations
 *
 * Centralized query key factory for organization-related queries.
 * This ensures consistent cache management and invalidation.
 *
 * Note: Apollo Client uses query documents as cache keys, but we
 * maintain this pattern for consistency with React Query patterns
 * and for potential future migration.
 */

export const organizationQueryKeys = {
  // ==========================================
  // Base Keys
  // ==========================================

  /**
   * Base key for all organization queries
   */
  all: ['organizations'] as const,

  // ==========================================
  // Current User's Organization
  // ==========================================

  /**
   * Current user's owned organization
   */
  mine: {
    all: () => [...organizationQueryKeys.all, 'mine'] as const,

    /**
     * Full organization details for the current user
     */
    detail: () => [...organizationQueryKeys.mine.all(), 'detail'] as const,

    /**
     * Lightweight status check for the current user
     */
    status: () => [...organizationQueryKeys.mine.all(), 'status'] as const,
  },

  // ==========================================
  // Documents Keys
  // ==========================================

  /**
   * Organization documents
   */
  documents: {
    all: (organizationId: string) =>
      [...organizationQueryKeys.all, 'documents', organizationId] as const,

    list: (organizationId: string) =>
      [...organizationQueryKeys.documents.all(organizationId), 'list'] as const,

    required: (organizationId: string) =>
      [
        ...organizationQueryKeys.documents.all(organizationId),
        'required',
      ] as const,
  },
};

// ==========================================
// Invalidation Helpers
// ==========================================

/**
 * Get all keys to invalidate when an organization is created
 */
export function getOrganizationCreateInvalidationKeys(): (readonly unknown[])[] {
  return [
    organizationQueryKeys.mine.all(),
  ];
}

/**
 * Get all keys to invalidate when an organization is updated
 */
export function getOrganizationUpdateInvalidationKeys(
  organizationId: string
): (readonly unknown[])[] {
  return [
    organizationQueryKeys.mine.all(),
  ];
}

/**
 * Get all keys to invalidate when organization status changes
 */
export function getOrganizationStatusChangeInvalidationKeys(
  organizationId: string
): (readonly unknown[])[] {
  return [
    organizationQueryKeys.mine.all(),
  ];
}

/**
 * Get all keys to invalidate when documents are updated
 */
export function getOrganizationDocumentInvalidationKeys(
  organizationId: string
): (readonly unknown[])[] {
  return [
    organizationQueryKeys.documents.all(organizationId),
    organizationQueryKeys.mine.all(),
  ];
}
