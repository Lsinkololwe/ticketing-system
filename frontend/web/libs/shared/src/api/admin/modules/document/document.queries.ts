/**
 * Document GraphQL Queries
 *
 * GraphQL query definitions for verification document operations.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

// ==========================================
// Fragments
// ==========================================

/**
 * Full verification document fields
 */
export const VERIFICATION_DOCUMENT_FIELDS = gql`
  fragment VerificationDocumentFields on VerificationDocument {
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
`;

// ==========================================
// Admin Queries
// ==========================================

/**
 * Get pending verification documents queue (admin)
 */
export const PENDING_VERIFICATION_DOCUMENTS = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  query PendingVerificationDocuments {
    pendingVerificationDocuments {
      ...VerificationDocumentFields
    }
  }
`;
