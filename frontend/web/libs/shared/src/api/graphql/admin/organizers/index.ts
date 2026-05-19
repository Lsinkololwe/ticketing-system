/**
 * Admin Organizers API
 *
 * Complete admin organizer management API including:
 * - GraphQL queries (applications, search, statistics)
 * - GraphQL mutations (approve, reject, suspend, verify)
 * - React hooks for all operations
 *
 * Usage:
 * ```tsx
 * import {
 *   useOrganizerApplications,
 *   usePendingApplications,
 *   useApproveOrganizer,
 *   useRejectOrganizer,
 * } from '@pml.tickets/shared/api/admin/organizers';
 * ```
 */

// GraphQL Queries
export * from './queries';

// GraphQL Mutations
export * from './mutations';

// React Hooks
export * from './hooks';
