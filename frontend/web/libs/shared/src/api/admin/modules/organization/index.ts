/**
 * Organization Module (Admin App)
 *
 * Complete organization module for admin app.
 * All types, queries, mutations, and hooks for admin operations.
 *
 * @example
 * ```tsx
 * import {
 *   useOrganizationsList,
 *   usePendingOrganizations,
 *   useApproveOrganization,
 *   useRejectOrganization,
 *   useSuspendOrganization,
 *   type Organization,
 *   type OrganizationListItem,
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
