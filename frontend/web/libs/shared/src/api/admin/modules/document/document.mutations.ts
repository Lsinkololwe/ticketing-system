/**
 * Document GraphQL Mutations
 *
 * GraphQL mutation definitions for verification document operations.
 *
 * Note: File uploads are handled via REST API, not GraphQL.
 * See `organization-admin/modules/organization/organization.rest.ts` for the
 * upload hook.
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
