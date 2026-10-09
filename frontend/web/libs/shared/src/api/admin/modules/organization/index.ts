/**
 * Organization Module (Admin App)
 *
 * Complete organization module for admin app.
 * All types, queries, mutations, and hooks for admin operations.
 *
 * @example
 * ```tsx
 * import {
 *   usePendingOrganizations,
 *   useOrganization,
 *   useApproveOrganization,
 *   useRejectOrganization,
 *   useSuspendOrganization,
 *   getStatusColor,
 *   getStatusLabel,
 * } from '@pml.tickets/shared/api/admin/modules/organization';
 * ```
 */

// Types and helper functions
export * from './organization.types';

// GraphQL operations
export * from './organization.queries';
export * from './organization.mutations';
export * from './organization.hooks';
