/**
 * Organization GraphQL Mutations (Organization Admin App)
 *
 * Mutations for organization self-service operations.
 * Organizers can apply, update, and submit their organization for review.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import { ORGANIZATION_FIELDS } from './organization.queries';

// ==========================================
// Organizer Self-Service Mutations
// ==========================================

/**
 * Apply to become an organizer
 * Creates a new Organization with status: DRAFT
 */
export const APPLY_TO_BE_ORGANIZER = gql`
  ${ORGANIZATION_FIELDS}
  mutation ApplyToBeOrganizer($input: OrganizationApplicationInput!) {
    applyToBeOrganizer(input: $input) {
      ...OrganizationFields
    }
  }
`;

/**
 * Update organization application details
 * Only allowed when status is DRAFT or CHANGES_REQUESTED
 */
export const UPDATE_ORGANIZATION_APPLICATION = gql`
  ${ORGANIZATION_FIELDS}
  mutation UpdateOrganizationApplication(
    $id: ID!
    $input: OrganizationApplicationInput!
  ) {
    updateOrganizationApplication(id: $id, input: $input) {
      ...OrganizationFields
    }
  }
`;

/**
 * Submit the organization application for admin review
 * Transitions status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW
 */
export const SUBMIT_ORGANIZATION_FOR_REVIEW = gql`
  ${ORGANIZATION_FIELDS}
  mutation SubmitOrganizationForReview($id: ID!) {
    submitOrganizationForReview(id: $id) {
      ...OrganizationFields
    }
  }
`;

/**
 * Update organization settings (for approved organizations)
 */
export const UPDATE_ORGANIZATION_SETTINGS = gql`
  ${ORGANIZATION_FIELDS}
  mutation UpdateOrganizationSettings(
    $id: ID!
    $input: OrganizationSettingsInput!
  ) {
    updateOrganizationSettings(id: $id, input: $input) {
      ...OrganizationFields
    }
  }
`;
