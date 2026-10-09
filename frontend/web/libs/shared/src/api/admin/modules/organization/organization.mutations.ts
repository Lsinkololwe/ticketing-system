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
  mutation ApproveOrganization($id: ID!, $commissionRate: Float) {
    approveOrganization(id: $id, commissionRate: $commissionRate) {
      ...AdminOrganizationFields
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
      ...AdminOrganizationFields
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
      ...AdminOrganizationFields
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
      ...AdminOrganizationFields
    }
  }
`;

/**
 * Reverse a suspension (admin)
 */
export const REACTIVATE_ORGANIZATION = gql`
  ${ORGANIZATION_FIELDS}
  mutation UnsuspendOrganization($id: ID!) {
    unsuspendOrganization(id: $id) {
      ...AdminOrganizationFields
    }
  }
`;
