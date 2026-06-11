/**
 * Organization GraphQL Queries (Admin App)
 *
 * Admin-specific queries for managing organizations.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

// ==========================================
// Fragments
// ==========================================

/**
 * Full organization fields for detail views and application flow
 */
export const ORGANIZATION_FIELDS = gql`
  fragment OrganizationFields on Organization {
    id
    name
    slug
    description
    tagline
    logoUrl
    bannerUrl
    website
    socialLinks {
      facebook
      instagram
      twitter
      linkedin
      youtube
      tiktok
    }

    # Organization type and status
    type
    status
    kybStatus

    # Contact information
    businessEmail
    businessPhone
    businessAddress {
      street
      city
      province
      country
      postalCode
    }

    # Business registration (optional)
    businessType
    businessRegistrationNumber
    taxId

    # Verification status
    verified
    documentsVerified
    payoutAccountVerified
    verifiedAt

    # Application workflow
    submittedAt
    approvedAt
    rejectionReason
    reviewedAt

    # Capabilities
    canCreateDraftEvents
    canPublishEvents
    canReceivePayouts
    canBeEdited
    canSubmitForReview
    isApproved
    isInApprovalWorkflow

    # Timestamps
    createdAt
    updatedAt
  }
`;

/**
 * List item fields for admin views
 */
export const ORGANIZATION_LIST_FIELDS = gql`
  fragment OrganizationListFields on Organization {
    id
    name
    slug
    type
    status
    logoUrl
    businessEmail
    businessPhone
    city
    province
    verified
    documentsVerified
    submittedAt
    approvedAt
    createdAt
  }
`;

// ==========================================
// Admin Queries
// ==========================================

/**
 * Get all organizations with pagination (admin view)
 */
export const ORGANIZATIONS_LIST = gql`
  ${ORGANIZATION_LIST_FIELDS}
  query OrganizationsList(
    $filter: OrganizationFilterInput
    $pagination: PaginationInput
  ) {
    organizationsOffsetPagination(filter: $filter, pagination: $pagination) {
      content {
        ...OrganizationListFields
      }
      totalElements
      totalPages
      page
      size
      hasNext
      hasPrevious
    }
  }
`;

/**
 * Get organizations pending review (admin view)
 */
export const PENDING_ORGANIZATIONS = gql`
  ${ORGANIZATION_LIST_FIELDS}
  query PendingOrganizations($pagination: PaginationInput) {
    pendingOrganizations(pagination: $pagination) {
      content {
        ...OrganizationListFields
      }
      totalElements
      totalPages
      page
      size
      hasNext
      hasPrevious
    }
  }
`;

/**
 * Get a single organization by ID (admin view)
 */
export const GET_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  query GetOrganization($id: ID!) {
    organization(id: $id) {
      ...OrganizationFields
    }
  }
`;

/**
 * Get organization statistics for admin dashboard
 */
export const ORGANIZATION_STATISTICS = gql`
  query OrganizationStatistics {
    organizationStatistics {
      totalCount
      pendingReviewCount
      approvedCount
      activeCount
      rejectedCount
      suspendedCount
    }
  }
`;
