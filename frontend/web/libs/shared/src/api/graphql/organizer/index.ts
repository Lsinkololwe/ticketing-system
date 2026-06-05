/**
 * Organizer Self-Service API Layer
 *
 * All GraphQL operations and React hooks for organizer self-service operations.
 * Used by the organization-admin app for:
 * - Application flow (apply to become an organizer)
 * - Profile management
 * - Status tracking
 *
 * Usage:
 * ```tsx
 * import {
 *   useMyOrganizerProfile,
 *   useCreateOrganizerProfile,
 *   useOrganizerApplication,
 *   getRouteForStatus,
 * } from '@pml.tickets/shared/api/graphql/organizer';
 * ```
 */

// Queries
export {
  MY_ORGANIZER_PROFILE_FIELDS,
  ORGANIZER_STATUS_FIELDS,
  MY_ORGANIZER_PROFILE,
  MY_ORGANIZER_STATUS,
} from './queries';

// Mutations
export {
  CREATE_ORGANIZER_PROFILE,
  UPDATE_ORGANIZER_PROFILE,
  SUBMIT_ORGANIZER_APPLICATION,
  DELETE_ORGANIZER_PROFILE,
} from './mutations';

// Hooks
export {
  // Query hooks
  useMyOrganizerProfile,
  useMyOrganizerStatus,
  // Mutation hooks
  useCreateOrganizerProfile,
  useUpdateOrganizerProfile,
  useSubmitOrganizerApplication,
  useDeleteOrganizerProfile,
  // Combined hooks
  useOrganizerApplication,
  // Helper functions
  canEditProfile,
  canSubmitForReview,
  canCreateEvents,
  getRouteForStatus,
} from './hooks';
