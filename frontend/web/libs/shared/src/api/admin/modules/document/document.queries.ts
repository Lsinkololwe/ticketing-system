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

/**
 * List item fields for documents
 */
export const DOCUMENT_LIST_FIELDS = gql`
  fragment DocumentListFields on VerificationDocument {
    id
    documentType
    fileName
    fileSize
    status
    uploadedAt
    verifiedAt
    rejectionReason
  }
`;

// ==========================================
// Organizer Queries
// ==========================================

/**
 * Get current user's verification documents
 */
export const MY_VERIFICATION_DOCUMENTS = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  query MyVerificationDocuments($status: DocumentStatus) {
    myVerificationDocuments(status: $status) {
      ...VerificationDocumentFields
    }
  }
`;

/**
 * Get my verification document by type
 */
export const MY_VERIFICATION_DOCUMENT_BY_TYPE = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  query MyVerificationDocumentByType($documentType: String!) {
    myVerificationDocumentByType(documentType: $documentType) {
      ...VerificationDocumentFields
    }
  }
`;

/**
 * Get my verification document count
 */
export const MY_VERIFICATION_DOCUMENT_COUNT = gql`
  query MyVerificationDocumentCount {
    myVerificationDocumentCount
  }
`;

/**
 * Get my approved document count
 */
export const MY_APPROVED_DOCUMENT_COUNT = gql`
  query MyApprovedDocumentCount {
    myApprovedDocumentCount
  }
`;

// ==========================================
// Single Document Queries
// ==========================================

/**
 * Get verification document by ID
 */
export const GET_VERIFICATION_DOCUMENT = gql`
  ${VERIFICATION_DOCUMENT_FIELDS}
  query GetVerificationDocument($id: ID!) {
    verificationDocument(id: $id) {
      ...VerificationDocumentFields
    }
  }
`;

// ==========================================
// Admin Queries
// ==========================================

/**
 * Get verification documents for organization (admin)
 */
export const VERIFICATION_DOCUMENTS_BY_ORGANIZATION = gql`
  ${DOCUMENT_LIST_FIELDS}
  query VerificationDocumentsByOrganization(
    $organizationId: ID!
    $status: DocumentStatus
  ) {
    verificationDocuments(organizationId: $organizationId, status: $status) {
      ...DocumentListFields
    }
  }
`;

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

// ==========================================
// Combined Queries
// ==========================================

/**
 * Get document summary for organization
 * Includes all documents and counts by status
 */
export const ORGANIZATION_DOCUMENT_SUMMARY = gql`
  ${DOCUMENT_LIST_FIELDS}
  query OrganizationDocumentSummary($organizationId: ID!) {
    allDocuments: verificationDocuments(organizationId: $organizationId) {
      ...DocumentListFields
    }
    pendingDocuments: verificationDocuments(
      organizationId: $organizationId
      status: PENDING
    ) {
      id
    }
    approvedDocuments: verificationDocuments(
      organizationId: $organizationId
      status: APPROVED
    ) {
      id
    }
    rejectedDocuments: verificationDocuments(
      organizationId: $organizationId
      status: REJECTED
    ) {
      id
    }
  }
`;

/**
 * Get my document summary (organizer)
 * Includes all documents and counts
 */
export const MY_DOCUMENT_SUMMARY = gql`
  ${DOCUMENT_LIST_FIELDS}
  query MyDocumentSummary {
    allDocuments: myVerificationDocuments {
      ...DocumentListFields
    }
    pendingDocuments: myVerificationDocuments(status: PENDING) {
      id
    }
    approvedDocuments: myVerificationDocuments(status: APPROVED) {
      id
    }
    rejectedDocuments: myVerificationDocuments(status: REJECTED) {
      id
    }
    totalCount: myVerificationDocumentCount
    approvedCount: myApprovedDocumentCount
  }
`;
