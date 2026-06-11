/**
 * Organization GraphQL Mutations (Admin App)
 *
 * Admin-only mutations for managing organizations.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import { ORGANIZATION_FIELDS } from './organization.queries';

// ==========================================
// Admin Review Mutations
// ==========================================

/**
 * Approve an organization application (admin)
 */
export const APPROVE_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  mutation ApproveOrganization($id: ID!, $comments: String) {
    approveOrganization(id: $id, comments: $comments) {
      ...OrganizationFields
    }
  }
`;

/**
 * Reject an organization application (admin)
 */
export const REJECT_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  mutation RejectOrganization($id: ID!, $reason: String!) {
    rejectOrganization(id: $id, reason: $reason) {
      ...OrganizationFields
    }
  }
`;

/**
 * Request changes to an organization application (admin)
 */
export const REQUEST_ORGANIZATION_CHANGES = gql`
  ${ORGANIZATION_FIELDS}
  mutation RequestOrganizationChanges($id: ID!, $reason: String!) {
    requestOrganizationChanges(id: $id, reason: $reason) {
      ...OrganizationFields
    }
  }
`;

/**
 * Suspend an organization (admin)
 */
export const SUSPEND_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  mutation SuspendOrganization($id: ID!, $reason: String!) {
    suspendOrganization(id: $id, reason: $reason) {
      ...OrganizationFields
    }
  }
`;

/**
 * Reactivate a suspended organization (admin)
 */
export const REACTIVATE_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  mutation ReactivateOrganization($id: ID!) {
    reactivateOrganization(id: $id) {
      ...OrganizationFields
    }
  }
`;

// ==========================================
// Document Verification Mutations
// ==========================================

/**
 * Mark organization documents as verified (admin)
 */
export const VERIFY_ORGANIZATION_DOCUMENTS = gql`
  ${ORGANIZATION_FIELDS}
  mutation VerifyOrganizationDocuments($id: ID!) {
    verifyOrganizationDocuments(id: $id) {
      ...OrganizationFields
    }
  }
`;

/**
 * Mark organization payout account as verified (admin)
 */
export const VERIFY_PAYOUT_ACCOUNT = gql`
  ${ORGANIZATION_FIELDS}
  mutation VerifyPayoutAccount($id: ID!) {
    verifyPayoutAccount(id: $id) {
      ...OrganizationFields
    }
  }
`;
