/**
 * Admin Organizers - GraphQL Mutations
 *
 * Admin-only mutations for organizer management including:
 * - Application review (approve, reject, request changes)
 * - Account management (suspend, reactivate)
 * - Verification (business, documents, bank account)
 * - Admin notes
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';
import { ORGANIZER_PROFILE_FIELDS } from './queries';

// ==================== Application Review ====================

/**
 * Approve an organizer application
 */
export const APPROVE_ORGANIZER = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation ApproveOrganizer($profileId: ID!) {
    approveOrganizer(profileId: $profileId) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Reject an organizer application
 */
export const REJECT_ORGANIZER = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation RejectOrganizer($profileId: ID!, $reason: String!) {
    rejectOrganizer(profileId: $profileId, reason: $reason) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Request changes from an organizer
 */
export const REQUEST_ORGANIZER_CHANGES = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation RequestOrganizerChanges($profileId: ID!, $reason: String!) {
    requestOrganizerChanges(profileId: $profileId, reason: $reason) {
      ...OrganizerProfileFields
    }
  }
`;

// ==================== Account Management ====================

/**
 * Suspend an approved organizer
 */
export const SUSPEND_ORGANIZER = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation SuspendOrganizer($profileId: ID!, $reason: String!) {
    suspendOrganizer(profileId: $profileId, reason: $reason) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Reactivate a suspended organizer
 */
export const REACTIVATE_ORGANIZER = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation ReactivateOrganizer($profileId: ID!) {
    reactivateOrganizer(profileId: $profileId) {
      ...OrganizerProfileFields
    }
  }
`;

// ==================== Verification ====================

/**
 * Verify organizer business registration
 */
export const VERIFY_ORGANIZER_BUSINESS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation VerifyOrganizerBusiness($profileId: ID!) {
    verifyOrganizerBusiness(profileId: $profileId) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Verify organizer documents
 */
export const VERIFY_ORGANIZER_DOCUMENTS = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation VerifyOrganizerDocuments($profileId: ID!) {
    verifyOrganizerDocuments(profileId: $profileId) {
      ...OrganizerProfileFields
    }
  }
`;

/**
 * Verify organizer bank account
 */
export const VERIFY_ORGANIZER_BANK_ACCOUNT = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation VerifyOrganizerBankAccount($profileId: ID!) {
    verifyOrganizerBankAccount(profileId: $profileId) {
      ...OrganizerProfileFields
    }
  }
`;

// ==================== Admin Notes ====================

/**
 * Update admin notes for an organizer
 */
export const UPDATE_ORGANIZER_ADMIN_NOTES = gql`
  ${ORGANIZER_PROFILE_FIELDS}
  mutation UpdateOrganizerAdminNotes($profileId: ID!, $notes: String!) {
    updateOrganizerAdminNotes(profileId: $profileId, notes: $notes) {
      ...OrganizerProfileFields
    }
  }
`;
