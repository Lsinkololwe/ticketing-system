/**
 * Organization Type Definitions (Admin)
 *
 * Re-exports GraphQL types and provides admin-specific utilities.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 * @see libs/shared/src/types/graphql/index.ts
 */

// ==========================================
// Re-export GraphQL Types
// ==========================================

export type {
  OrganizationStatus,
  OrganizationType,
  BusinessType,
  BusinessAddress,
  SocialLinks,
  KybStatus,
} from '../../../../types/graphql';

// ==========================================
// UI Helper Functions
// ==========================================

/**
 * Get color for organization status (for UI badges)
 */
export function getStatusColor(status: import('../../../../types/graphql').OrganizationStatus): string {
  const colorMap: Record<import('../../../../types/graphql').OrganizationStatus, string> = {
    DRAFT: 'amber',
    PENDING_REVIEW: 'blue',
    CHANGES_REQUESTED: 'orange',
    APPROVED: 'green',
    ACTIVE: 'green',
    REJECTED: 'red',
    SUSPENDED: 'gray',
    INACTIVE: 'gray',
    PENDING_DELETION: 'red',
  };
  return colorMap[status] || 'gray';
}

/**
 * Get display label for organization status
 */
export function getStatusLabel(status: import('../../../../types/graphql').OrganizationStatus): string {
  const labelMap: Record<import('../../../../types/graphql').OrganizationStatus, string> = {
    DRAFT: 'Draft',
    PENDING_REVIEW: 'Pending Review',
    CHANGES_REQUESTED: 'Changes Requested',
    APPROVED: 'Approved',
    ACTIVE: 'Active',
    REJECTED: 'Rejected',
    SUSPENDED: 'Suspended',
    INACTIVE: 'Inactive',
    PENDING_DELETION: 'Pending Deletion',
  };
  return labelMap[status] || status;
}
