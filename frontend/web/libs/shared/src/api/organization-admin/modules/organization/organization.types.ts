/**
 * Organization Type Definitions (Organization Admin)
 *
 * Re-exports GraphQL types and provides organization-admin-specific utilities.
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
  OrganizationApplicationInput,
  UpdateOrganizationSettingsInput as OrganizationSettingsInput,
} from '../../../../types/graphql';

// ==========================================
// Organization Admin Specific Types
// ==========================================

/**
 * Lightweight organization status information
 * Used for routing decisions and status checks without full organization data
 */
export interface OrganizationStatusInfo {
  id: string;
  name: string;
  status: import('../../../../types/graphql').OrganizationStatus;
  rejectionReason?: string | null;
  documentsVerified: boolean;
  submittedAt?: string | null;
  approvedAt?: string | null;
  canSubmitForReview: boolean;
  isApproved: boolean;
  isInApprovalWorkflow: boolean;
}

// ==========================================
// UI Helper Functions
// ==========================================


/**
 * Check if organization application can be edited
 */
export function canEditApplication(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  if (!status) return false;
  return status === 'DRAFT' || status === 'CHANGES_REQUESTED';
}

/**
 * Check if organization can submit for review
 */
export function canSubmitForReview(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  if (!status) return false;
  return status === 'DRAFT' || status === 'CHANGES_REQUESTED';
}

/**
 * Check if organization is approved
 */
export function isApproved(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  if (!status) return false;
  return status === 'APPROVED' || status === 'ACTIVE';
}

/**
 * Check if organization is pending review
 */
export function isPendingReview(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  if (!status) return false;
  return status === 'PENDING_REVIEW';
}

/**
 * Check if organization can create events
 */
export function canCreateEvents(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  if (!status) return false;
  return status === 'APPROVED' || status === 'ACTIVE';
}

/**
 * Check if organization can create draft events
 */
export function canCreateDraftEvents(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  if (!status) return false;
  // Can create drafts in any status except REJECTED and SUSPENDED
  return status !== 'REJECTED' && status !== 'SUSPENDED';
}

/**
 * Get the route path for the current organization status
 */
export function getRouteForStatus(
  status: import('../../../../types/graphql').OrganizationStatus | string | null,
  organizationId?: string
): string {
  if (!status) return '/apply/start';

  switch (status) {
    case 'DRAFT':
    case 'CHANGES_REQUESTED':
      return '/apply/business-info';
    case 'PENDING_REVIEW':
      return '/status';
    case 'APPROVED':
    case 'ACTIVE':
      return '/dashboard';
    case 'REJECTED':
      return '/status';
    case 'SUSPENDED':
      return '/status';
    default:
      return '/apply/start';
  }
}

// ==========================================
// Permission Helpers (replaces Context)
// ==========================================

/**
 * Check if user can view analytics (requires approved status)
 */
export function canViewAnalytics(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return isApproved(status);
}

/**
 * Check if user can edit organization settings
 */
export function canEditOrganization(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return isApproved(status) || canEditApplication(status);
}

/**
 * Check if user can edit events
 */
export function canEditEvents(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return isApproved(status);
}

/**
 * Check if user can manage team
 */
export function canManageTeam(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return isApproved(status);
}

/**
 * Check if user can request payouts
 */
export function canRequestPayouts(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return isApproved(status);
}

/**
 * Check if organization is in draft status
 */
export function isDraft(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return status === 'DRAFT';
}

/**
 * Check if organization needs changes
 */
export function needsChanges(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return status === 'CHANGES_REQUESTED';
}

/**
 * Check if organization is rejected
 */
export function isRejected(status: import('../../../../types/graphql').OrganizationStatus | string | null): boolean {
  return status === 'REJECTED';
}
