/**
 * Organizer Self-Service - GraphQL Queries
 *
 * Queries for organizer's own profile operations:
 * - Get current user's organizer profile
 * - Check application status
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

// ==================== Fragments ====================

/**
 * Full organizer profile fields for detail views
 */
export const MY_ORGANIZER_PROFILE_FIELDS = gql`
  fragment MyOrganizerProfileFields on OrganizerProfile {
    id
    userId
    companyName
    tagline
    companyDescription
    logoUrl
    bannerUrl
    website
    taxId
    businessRegistrationNumber
    businessType
    businessAddress
    businessEmail
    businessPhone
    city
    province
    country
    postalCode
    status
    statusReason
    submittedAt
    approvedAt
    reviewedAt
    verified
    documentsVerified
    bankVerified
    commissionRate
    payoutSchedule
    createdAt
    updatedAt
  }
`;

/**
 * Minimal fields for status checks and navigation decisions
 */
export const ORGANIZER_STATUS_FIELDS = gql`
  fragment OrganizerStatusFields on OrganizerProfile {
    id
    status
    statusReason
    companyName
    submittedAt
    approvedAt
  }
`;

// ==================== Queries ====================

/**
 * Get the current authenticated user's organizer profile
 * Returns null if user hasn't applied yet
 *
 * Used for:
 * - Post-login routing decisions
 * - Application status checks
 * - Profile editing
 */
export const MY_ORGANIZER_PROFILE = gql`
  ${MY_ORGANIZER_PROFILE_FIELDS}
  query MyOrganizerProfile {
    myOrganizerProfile {
      ...MyOrganizerProfileFields
    }
  }
`;

/**
 * Lightweight query just for status check
 * Used for routing decisions without fetching full profile
 */
export const MY_ORGANIZER_STATUS = gql`
  ${ORGANIZER_STATUS_FIELDS}
  query MyOrganizerStatus {
    myOrganizerProfile {
      ...OrganizerStatusFields
    }
  }
`;
