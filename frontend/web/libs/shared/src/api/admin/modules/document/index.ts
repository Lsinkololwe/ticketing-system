/**
 * Document Module Exports
 *
 * Re-exports all document-related types, schemas, queries, mutations, hooks, and REST operations
 * for verification document upload, review, and management workflows.
 *
 * @example
 * ```tsx
 * import {
 *   // Types
 *   type VerificationDocument,
 *   type DocumentStatus,
 *   type DocumentType,
 *   getDocumentStatusLabel,
 *   getDocumentTypeLabel,
 *   getIndividualRequiredDocuments,
 *   getBusinessRequiredDocuments,
 *
 *   // Schemas
 *   documentUploadFormSchema,
 *   documentReviewSchema,
 *   type DocumentUploadFormData,
 *   validateFile,
 *
 *   // Hooks
 *   useMyVerificationDocuments,
 *   useDocumentUpload,
 *   useApproveVerificationDocument,
 *   useDocumentManagement,
 *
 *   // REST Operations
 *   getDocument,
 *   downloadDocument,
 *   listAllDocuments,
 *
 *   // Query keys
 *   documentQueryKeys,
 * } from '@pml.tickets/shared/api/admin/modules/document';
 * ```
 */

// Core types
export * from './document.types';

// Validation schemas
export * from './document.schemas';

// GraphQL queries
export * from './document.queries';

// GraphQL mutations
export * from './document.mutations';

// Query keys
export * from './document.keys';

// React hooks
export * from './document.hooks';

// REST API operations
export * from './document.rest';
