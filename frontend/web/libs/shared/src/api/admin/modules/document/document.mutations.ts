/**
 * Document GraphQL Mutations
 *
 * GraphQL mutation definitions for verification document operations.
 *
 * Note: File uploads are handled via REST API, not GraphQL.
 * See document.hooks.ts for the useDocumentUpload hook.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import { VERIFICATION_DOCUMENT_FIELDS } from './document.queries';

// ==========================================
// Admin Review Mutations
// ==========================================

/**
 * Approve a verification document (admin)
 */
export const APPROVE_VERIFICATION_DOCUMENT = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  mutation ApproveVerificationDocument($documentId: ID!) {
    approveVerificationDocument(documentId: $documentId) {
      ...VerificationDocumentFields
    }
  }
`;

/**
 * Reject a verification document (admin)
 */
export const REJECT_VERIFICATION_DOCUMENT = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  mutation RejectVerificationDocument($documentId: ID!, $reason: String!) {
    rejectVerificationDocument(documentId: $documentId, reason: $reason) {
      ...VerificationDocumentFields
    }
  }
`;

// ==========================================
// Document Management Mutations
// ==========================================

/**
 * Delete a verification document
 * Only allowed for pending/rejected documents
 */
export const DELETE_VERIFICATION_DOCUMENT = gql`
  mutation DeleteVerificationDocument($documentId: ID!) {
    deleteVerificationDocument(documentId: $documentId) {
      success
      message
    }
  }
`;

/**
 * Replace a verification document
 * Used when re-uploading after rejection
 */
export const REPLACE_VERIFICATION_DOCUMENT = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  mutation ReplaceVerificationDocument(
    $documentId: ID!
    $newDocumentUrl: String!
    $fileName: String
    $fileSize: Int
    $mimeType: String
  ) {
    replaceVerificationDocument(
      documentId: $documentId
      newDocumentUrl: $newDocumentUrl
      fileName: $fileName
      fileSize: $fileSize
      mimeType: $mimeType
    ) {
      ...VerificationDocumentFields
    }
  }
`;

// ==========================================
// Organization Document Verification
// ==========================================

/**
 * Mark organization documents as verified (admin)
 * This is a bulk operation that updates the organization's documentsVerified flag
 */
export const VERIFY_ORGANIZATION_DOCUMENTS_SIMPLE = gql`
  mutation VerifyOrganizationDocuments($organizationId: ID!) {
    verifyOrganizationDocuments(id: $organizationId) {
      id
      documentsVerified
      status
    }
  }
`;

// ==========================================
// Bulk Operations (Admin)
// ==========================================

/**
 * Bulk approve documents (admin)
 */
export const BULK_APPROVE_DOCUMENTS = gql`
  mutation BulkApproveDocuments($documentIds: [ID!]!) {
    bulkApproveDocuments(documentIds: $documentIds) {
      success
      message
      approvedCount
      failedCount
      errors
    }
  }
`;

/**
 * Bulk reject documents (admin)
 */
export const BULK_REJECT_DOCUMENTS = gql`
  mutation BulkRejectDocuments($documentIds: [ID!]!, $reason: String!) {
    bulkRejectDocuments(documentIds: $documentIds, reason: $reason) {
      success
      message
      rejectedCount
      failedCount
      errors
    }
  }
`;
