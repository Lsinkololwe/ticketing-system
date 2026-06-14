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
  Organization,
  OrganizationStatus,
  OrganizationType,
  BusinessType,
  BusinessAddress,
  SocialLinks,
  KybStatus,
} from '../../../../types/graphql';

// ==========================================
// Admin-Specific Types
// ==========================================

/**
 * Organization list item for admin views
 */
export interface OrganizationListItem {
  id: string;
  name: string | null;
  slug: string | null;
  type: import('../../../../types/graphql').OrganizationType | null;
  status: import('../../../../types/graphql').OrganizationStatus;
  logoUrl: string | null;
  businessEmail: string | null;
  businessPhone: string | null;
  city: string | null;
  province: string | null;
  verified: boolean;
  documentsVerified: boolean;
  submittedAt: string | null;
  approvedAt: string | null;
  createdAt: string | null;
}

/**
 * Admin review decision input
 */
export interface ReviewDecisionInput {
  approved: boolean;
  rejectionReason?: string | null;
  comments?: string | null;
}

/**
 * Admin request changes input
 */
export interface RequestChangesInput {
  reason: string;
  fieldsToUpdate?: string[];
}

/**
 * Organization list filters
 */
export interface OrganizationFilters {
  status?: import('../../../../types/graphql').OrganizationStatus[];
  type?: import('../../../../types/graphql').OrganizationType[];
  verified?: boolean;
  search?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
  sortBy?: 'createdAt' | 'updatedAt' | 'name' | 'status' | 'submittedAt';
  sortOrder?: 'asc' | 'desc';
}

/**
 * Paginated organizations response
 */
export interface PaginatedOrganizationsResponse {
  content: OrganizationListItem[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/**
 * Organization statistics (for admin dashboard)
 */
export interface OrganizationStatistics {
  totalCount: number;
  pendingReviewCount: number;
  approvedCount: number;
  activeCount: number;
  rejectedCount: number;
  suspendedCount: number;
  byType: Record<import('../../../../types/graphql').OrganizationType, number>;
  byStatus: Record<import('../../../../types/graphql').OrganizationStatus, number>;
}

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
