/**
 * Organization Module (Organization Admin App)
 *
 * Complete organization module for organization-admin app.
 * All types, schemas, queries, mutations, hooks, and REST operations in one place.
 *
 * @example
 * ```tsx
 * import {
 *   useMyOrganization,
 *   useApplyToBeOrganizer,
 *   useSubmitOrganizationForReview,
 *   type Organization,
 *   type OrganizationApplicationInput,
 *   businessInfoFormSchema,
 *   getRouteForStatus,
 *   canEditApplication,
 *   useDocumentUpload,
 *   uploadDocument,
 * } from '@pml.tickets/shared/api/organization-admin/modules/organization';
 * ```
 */

// Types and helper functions
export * from './organization.types';

// Validation schemas
export * from './organization.schemas';

// Query keys
export * from './organization.keys';

// GraphQL operations
export * from './organization.queries';
export * from './organization.mutations';
export * from './organization.hooks';

// REST API operations
export * from './organization.rest';
