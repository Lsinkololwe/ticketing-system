/**
 * Organization GraphQL Queries (Organization Admin App)
 *
 * Queries for organization self-service operations.
 * Organizers can view and manage their own organization.
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
      addressLine1
      addressLine2
      city
      province
      country
      countryCode
      postalCode
      formattedAddress
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
 * Minimal fields for status checks and navigation decisions
 */
export const ORGANIZATION_STATUS_FIELDS = gql`
  fragment OrganizationStatusFields on Organization {
    id
    name
    status
    rejectionReason
    documentsVerified
    submittedAt
    approvedAt
    canSubmitForReview
    isApproved
    isInApprovalWorkflow
  }
`;

// ==========================================
// Current User Queries
// ==========================================

/**
 * Get the current authenticated user's owned organization
 * Returns null if user hasn't applied yet
 */
export const MY_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  query MyOrganization {
    myOwnedOrganization {
      ...OrganizationFields
    }
  }
`;

/**
 * Lightweight query just for status check
 * Used for routing decisions without fetching full organization data
 */
export const MY_ORGANIZATION_STATUS = gql`
  ${ORGANIZATION_STATUS_FIELDS}
  query MyOrganizationStatus {
    myOwnedOrganization {
      ...OrganizationStatusFields
    }
  }
`;

// ==========================================
// Lookup Queries
// ==========================================

/**
 * Check if a slug is available for a new organization
 */
export const IS_SLUG_AVAILABLE = gql`
  query IsSlugAvailable($slug: String!) {
    isSlugAvailable(slug: $slug)
  }
`;
