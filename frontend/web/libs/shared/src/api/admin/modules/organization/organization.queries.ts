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
    # city/province are not fields on Organization — they live under
    # businessAddress. Asking for them flat made every query using this
    # fragment fail validation with a 400, which nothing noticed because no
    # screen had been wired to it yet.
    businessAddress {
      city
      province
    }
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
 * Organizations awaiting review — the approvals workbench queue.
 *
 * <h2>This used to query a field that does not exist</h2>
 * It asked for `pendingOrganizations(pagination: PaginationInput)`. No subgraph
 * declares that field and it is absent from the composed supergraph, so every
 * call failed validation with "Cannot query field". Nothing noticed because no
 * screen called the hook — the approvals pages were all placeholders.
 *
 * <p>The real query is `organizationsOffsetPagination`, filtered by status.
 */
export const PENDING_ORGANIZATIONS = gql`
  ${ORGANIZATION_LIST_FIELDS}
  query PendingOrganizations($pagination: OffsetPaginationInput) {
    organizationsOffsetPagination(
      status: PENDING_REVIEW
      pagination: $pagination
    ) {
      content {
        ...OrganizationListFields
      }
      pageInfo {
        currentPage
        pageSize
        totalCount
        hasNext
        hasPrevious
      }
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
