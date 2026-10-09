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
  fragment AdminOrganizationFields on Organization {
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
      addressLine1
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
 * Organizations awaiting review — the approvals workbench queue.
 *
 * There is no dedicated `pendingOrganizations` field on the schema, so this
 * filters the general `organizations` query by `PENDING_REVIEW` status
 * instead.
 */
export const PENDING_ORGANIZATIONS = gql`
  ${ORGANIZATION_LIST_FIELDS}
  query PendingOrganizations($pagination: OffsetPaginationInput) {
    organizations(
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
      ...AdminOrganizationFields
    }
  }
`;
