/**
 * Admin Organizers - GraphQL Queries
 *
 * Queries for admin organizer management including:
 * - Application review queue
 * - Status-based listing (pending, approved, suspended, rejected)
 * - Search and statistics
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

// ==================== Fragments ====================

export const ORGANIZER_PROFILE_FIELDS = gql`
  fragment OrganizerProfileFields on OrganizerProfile {
    id
    userId
    companyName
    tagline
    companyDescription
    businessRegistrationNumber
    taxId
    businessType
    yearEstablished
    businessAddress
    businessEmail
    businessPhone
    website
    city
    province
    country
    postalCode
    socialLinks {
      facebook
      twitter
      instagram
      linkedin
      youtube
      tiktok
    }
    status
    statusReason
    submittedAt
    approvedAt
    reviewedAt
    reviewedById
    reviewedBy {
      id
      firstName
      lastName
      fullName
    }
    reviewNotes
    verified
    documentsVerified
    bankVerified
    verificationDocuments {
      id
      documentType
      documentUrl
      fileName
      fileSize
      mimeType
      status
      uploadedAt
    }
    commissionRate
    payoutSchedule
    totalEvents
    totalRevenue
    totalTicketsSold
    averageRating
    createdAt
    updatedAt
  }
`;

export const ORGANIZER_PROFILE_SUMMARY_FIELDS = gql`
  fragment OrganizerProfileSummaryFields on OrganizerProfile {
    id
    userId
    companyName
    tagline
    province
    city
    businessEmail
    businessPhone
    status
    statusReason
    verified
    documentsVerified
    bankVerified
    submittedAt
    approvedAt
  }
`;

// ==================== Queries ====================

/**
 * Get organizer applications with offset pagination and optional status filter
 */
export const GET_ORGANIZER_APPLICATIONS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetOrganizerApplicationsAdmin(
    $status: OrganizerStatus
    $pagination: OffsetPaginationInput
  ) {
    organizerApplicationsOffsetPagination(status: $status, pagination: $pagination) {
      content {
        ...OrganizerProfileFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        hasNext
        hasPrevious
        startCursor
        endCursor
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get pending organizer applications for review queue
 */
export const GET_PENDING_APPLICATIONS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetPendingOrganizerApplicationsAdmin($pagination: OffsetPaginationInput) {
    organizerApplicationsOffsetPagination(status: PENDING_REVIEW, pagination: $pagination) {
      content {
        ...OrganizerProfileFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        hasNext
        hasPrevious
        startCursor
        endCursor
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get approved organizers
 */
export const GET_APPROVED_ORGANIZERS = gql`
  ${ORGANIZER_PROFILE_SUMMARY_FIELDS}
  query GetApprovedOrganizersAdmin($pagination: OffsetPaginationInput) {
    organizerApplicationsOffsetPagination(status: APPROVED, pagination: $pagination) {
      content {
        ...OrganizerProfileSummaryFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        hasNext
        hasPrevious
        startCursor
        endCursor
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get suspended organizers
 */
export const GET_SUSPENDED_ORGANIZERS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetSuspendedOrganizersAdmin($pagination: OffsetPaginationInput) {
    organizerApplicationsOffsetPagination(status: SUSPENDED, pagination: $pagination) {
      content {
        ...OrganizerProfileFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        hasNext
        hasPrevious
        startCursor
        endCursor
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get rejected organizers
 */
export const GET_REJECTED_ORGANIZERS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetRejectedOrganizersAdmin($pagination: OffsetPaginationInput) {
    organizerApplicationsOffsetPagination(status: REJECTED, pagination: $pagination) {
      content {
        ...OrganizerProfileFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        hasNext
        hasPrevious
        startCursor
        endCursor
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get organizers with changes requested
 */
export const GET_CHANGES_REQUESTED_ORGANIZERS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetChangesRequestedOrganizersAdmin($pagination: OffsetPaginationInput) {
    organizerApplicationsOffsetPagination(status: CHANGES_REQUESTED, pagination: $pagination) {
      content {
        ...OrganizerProfileFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        hasNext
        hasPrevious
        startCursor
        endCursor
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get single organizer profile by ID
 */
export const GET_ORGANIZER_PROFILE = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetOrganizerProfile($id: ID!) {
    organizerProfile(id: $id) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Get organizer profile by user ID
 */
export const GET_ORGANIZER_BY_USER_ID = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  query GetOrganizerProfileByUserId($userId: ID!) {
    organizerProfileByUserId(userId: $userId) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Search organizers by company name
 */
export const SEARCH_ORGANIZERS = gql`
  ${ORGANIZER_PROFILE_SUMMARY_FIELDS}
  query SearchOrganizersAdmin($companyName: String!, $pagination: OffsetPaginationInput) {
    searchOrganizersByCompanyName(companyName: $companyName, pagination: $pagination) {
      content {
        ...OrganizerProfileSummaryFields
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        currentPage
        pageSize
        totalCount
        totalElements
        totalPages
      }
    }
  }
`;

/**
 * Get platform statistics including organizer counts
 */
export const GET_ORGANIZER_STATS = gql`
  query GetOrganizerStats {
    platformStatistics {
      totalUsers
      totalOrganizers
      totalOrganizations
      pendingOrganizerApplications
    }
  }
`;

/**
 * Get individual organizer statistics
 */
export const GET_ORGANIZER_STATISTICS = gql`
  query GetOrganizerStatistics($organizerId: ID!) {
    organizerStatistics(organizerId: $organizerId) {
      organizerId
      organizationId
      totalEvents
      activeEvents
      completedEvents
      cancelledEvents
      totalTicketsSold
      totalRevenue
      pendingPayouts
      completedPayouts
      averageRating
      totalReviews
    }
  }
`;

/**
 * Get verification documents for an organizer
 */
export const GET_VERIFICATION_DOCUMENTS = gql`
  query GetVerificationDocuments($organizerProfileId: ID!, $status: DocumentStatus) {
    verificationDocuments(organizerProfileId: $organizerProfileId, status: $status) {
      id
      documentType
      documentUrl
      fileName
      fileSize
      mimeType
      status
      uploadedAt
      verifiedAt
      verifiedById
      verifiedBy {
        id
        firstName
        lastName
        fullName
      }
      rejectionReason
    }
  }
`;
