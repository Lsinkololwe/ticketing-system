/**
 * Document Module Exports
 *
 * Re-exports document-related validation schemas, GraphQL queries/mutations/
 * hooks, and REST operations for verification document upload, review, and
 * management workflows. Types come from codegen (`@pml.tickets/shared/types/graphql`)
 * or from the individual query/mutation/REST files that select them — never
 * hand-declared here.
 *
 * @example
 * ```tsx
 * import {
 *   // Schemas
 *   documentUploadFormSchema,
 *   documentReviewSchema,
 *   type DocumentUploadFormData,
 *   validateFile,
 *
 *   // Hooks
 *   usePendingVerificationDocuments,
 *   useApproveVerificationDocument,
 *   useRejectVerificationDocument,
 *
 *   // REST Operations
 *   getDocument,
 *   downloadDocument,
 *   listAllDocuments,
 * } from '@pml.tickets/shared/api/admin/modules/document';
 * ```
 */

// Validation schemas
export * from './document.schemas';

// GraphQL queries
export * from './document.queries';

// GraphQL mutations
export * from './document.mutations';

// React hooks
export * from './document.hooks';

// REST API operations
export * from './document.rest';
