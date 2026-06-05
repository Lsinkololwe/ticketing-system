/**
 * Organizer Self-Service - GraphQL Mutations
 *
 * Mutations for organizer application and profile management:
 * - Create organizer profile (start application)
 * - Update profile details
 * - Submit application for review
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import { MY_ORGANIZER_PROFILE_FIELDS } from './queries';

// ==================== Mutations ====================

/**
 * Create a new organizer profile
 * This starts the application process
 * Creates profile in DRAFT status
 */
export const CREATE_ORGANIZER_PROFILE = gql`
  ${MY_ORGANIZER_PROFILE_FIELDS}
  mutation CreateOrganizerProfile($input: CreateOrganizerProfileInput!) {
    createOrganizerProfile(input: $input) {
      ...MyOrganizerProfileFields
    }
  }
`;

/**
 * Update organizer profile details
 * Can be used during DRAFT, PENDING_DOCUMENTS, and CHANGES_REQUESTED statuses
 * Returns mutation response with success/error info
 */
export const UPDATE_ORGANIZER_PROFILE = gql`
  ${MY_ORGANIZER_PROFILE_FIELDS}
  mutation UpdateOrganizerProfile($input: UpdateOrganizerProfileInput!) {
    updateOrganizerProfile(input: $input) {
      success
      message
      organizerProfile {
        ...MyOrganizerProfileFields
      }
    }
  }
`;

/**
 * Submit the organizer profile for admin review
 * Transitions status from DRAFT/PENDING_DOCUMENTS to PENDING_REVIEW
 * Requires all required fields to be filled
 */
export const SUBMIT_ORGANIZER_APPLICATION = gql`
  ${MY_ORGANIZER_PROFILE_FIELDS}
  mutation SubmitOrganizerApplication {
    submitOrganizerProfileForReview {
      ...MyOrganizerProfileFields
    }
  }
`;

/**
 * Delete organizer profile
 * Only allowed in DRAFT status
 */
export const DELETE_ORGANIZER_PROFILE = gql`
  mutation DeleteOrganizerProfile {
    deleteOrganizerProfile
  }
`;
