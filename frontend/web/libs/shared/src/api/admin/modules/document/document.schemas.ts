/**
 * Zod Validation Schemas for Documents
 *
 * These schemas provide client-side validation for document upload forms
 * and ensure data integrity before API calls.
 *
 * OWASP A03:2021 - Injection Prevention:
 * All text fields that accept user input include sanitization transforms
 * to strip potentially dangerous HTML content and prevent XSS attacks.
 *
 * @see document.types.ts for type definitions
 */

import { z } from 'zod';
import type { DocumentStatus } from '../../../../types/graphql';

// ==========================================
// Sanitization Utilities (OWASP A03:2021)
// ==========================================

/**
 * Strip HTML tags from a string to prevent XSS.
 */
function stripHtmlTags(str: string): string {
  return str.replace(/<[^>]*>/g, '');
}

/**
 * Sanitize user input by stripping HTML and trimming whitespace.
 */
function sanitizeText(str: string): string {
  return stripHtmlTags(str).trim();
}

/**
 * Sanitize optional text field.
 * Returns undefined for empty strings after sanitization.
 */
function sanitizeOptionalText(str: string | undefined): string | undefined {
  if (!str) return undefined;
  const sanitized = sanitizeText(str);
  return sanitized.length > 0 ? sanitized : undefined;
}

// ==========================================
// Constants
// ==========================================

/**
 * Document statuses. Typed from the generated enum, so a value the schema lacks does not compile.
 */
export const DOCUMENT_STATUSES: readonly DocumentStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'];

// Document TYPES are platform reference data (`KYB_DOCUMENT_TYPE`), read with `useReferenceOptions`.
// There is no list of them here: a code the platform does not list is refused by `referenceCode(codes)`.

/**
 * Accepted file extensions
 */
export const ACCEPTED_FILE_EXTENSIONS = [
  '.pdf',
  '.jpg',
  '.jpeg',
  '.png',
] as const;

/**
 * Accepted MIME types
 */
export const ACCEPTED_MIME_TYPES = [
  'application/pdf',
  'image/jpeg',
  'image/png',
] as const;

/**
 * Maximum file size in bytes (5MB)
 */
export const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

/**
 * Maximum file size in MB
 */
export const MAX_FILE_SIZE_MB = 5;

// ==========================================
// Field-Level Schemas
// ==========================================

/**
 * Document type validation
 */
export function documentTypeSchema(listedCodes: readonly string[]) {
  return z
    .string({ error: 'Please select a valid document type' })
    .min(1, 'Please select a valid document type')
    .refine((code) => listedCodes.length === 0 || listedCodes.includes(code), 'Please select a valid document type');
}

/**
 * File validation schema
 * Note: This is a custom validation, actual file validation happens at upload
 */
export const fileSchema = z.custom<File>(
  (val) => val instanceof File,
  { message: 'Please select a file to upload' }
);

/**
 * Optional description field
 */
export const descriptionSchema = z
  .string()
  .max(500, 'Description must be less than 500 characters')
  .transform(sanitizeOptionalText)
  .optional();

// ==========================================
// Document Upload Form Schema
// ==========================================

/**
 * Document upload form validation schema
 */
export const documentUploadFormSchema = z.object({
  documentType: documentTypeSchema,
  file: fileSchema,
  description: descriptionSchema,
});

export type DocumentUploadFormData = z.infer<typeof documentUploadFormSchema>;
export type DocumentUploadFormInput = z.input<typeof documentUploadFormSchema>;

// ==========================================
// Document Review Schema (Admin)
// ==========================================

/**
 * Admin document review schema
 */
export const documentReviewSchema = z
  .object({
    documentId: z.string().min(1, 'Document ID is required'),
    approved: z.boolean(),
    rejectionReason: z
      .string()
      .max(500, 'Reason must be less than 500 characters')
      .transform(sanitizeOptionalText)
      .optional(),
  })
  .refine(
    (data) => {
      // If not approved, rejection reason is required
      if (!data.approved && !data.rejectionReason) {
        return false;
      }
      return true;
    },
    {
      message: 'Rejection reason is required when rejecting a document',
      path: ['rejectionReason'],
    }
  );

export type DocumentReviewFormData = z.infer<typeof documentReviewSchema>;

// ==========================================
// Document Filter Schema
// ==========================================

/**
 * Document filter schema for admin list
 */
export const documentFilterSchema = z.object({
  status: z.custom<DocumentStatus>((v) => typeof v === 'string' && (DOCUMENT_STATUSES as readonly string[]).includes(v)).optional(),
  documentType: z.string().optional(),
  organizationId: z.string().optional(),
  uploadedFrom: z.string().optional(),
  uploadedTo: z.string().optional(),
});

export type DocumentFilterFormData = z.infer<typeof documentFilterSchema>;

// ==========================================
// Bulk Document Upload Schema
// ==========================================

/**
 * Bulk document upload item
 */
export const bulkDocumentItemSchema = z.object({
  documentType: documentTypeSchema,
  file: fileSchema,
  description: descriptionSchema,
});

/**
 * Bulk document upload schema
 */
export const bulkDocumentUploadSchema = z.object({
  documents: z
    .array(bulkDocumentItemSchema)
    .min(1, 'At least one document is required')
    .max(10, 'Maximum 10 documents can be uploaded at once'),
});

export type BulkDocumentUploadFormData = z.infer<typeof bulkDocumentUploadSchema>;

// ==========================================
// Validation Helpers
// ==========================================

/**
 * Validate file extension
 */
export function isValidFileExtension(fileName: string): boolean {
  const extension = `.${fileName.split('.').pop()?.toLowerCase()}`;
  return (ACCEPTED_FILE_EXTENSIONS as readonly string[]).includes(extension);
}

/**
 * Validate MIME type
 */
export function isValidMimeType(mimeType: string): boolean {
  return (ACCEPTED_MIME_TYPES as readonly string[]).includes(mimeType);
}

/**
 * Validate file size
 */
export function isValidFileSize(sizeInBytes: number): boolean {
  return sizeInBytes <= MAX_FILE_SIZE_BYTES;
}

/**
 * Get file extension from filename
 */
export function getFileExtension(fileName: string): string {
  return `.${fileName.split('.').pop()?.toLowerCase() || ''}`;
}

/**
 * Validate a file against all criteria
 */
export function validateFile(file: File): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];

  if (!isValidFileExtension(file.name)) {
    errors.push(
      `Invalid file type. Accepted formats: ${ACCEPTED_FILE_EXTENSIONS.join(', ')}`
    );
  }

  if (!isValidMimeType(file.type)) {
    errors.push('Invalid file format');
  }

  if (!isValidFileSize(file.size)) {
    errors.push(`File size must be less than ${MAX_FILE_SIZE_MB}MB`);
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Get human-readable file size limit
 */
export function getFileSizeLimit(): string {
  return `${MAX_FILE_SIZE_MB}MB`;
}

/**
 * Get accepted file formats as string
 */
export function getAcceptedFormatsString(): string {
  return ACCEPTED_FILE_EXTENSIONS.join(', ');
}

/**
 * Get accept attribute value for file input
 */
export function getAcceptAttribute(): string {
  return [...ACCEPTED_FILE_EXTENSIONS, ...ACCEPTED_MIME_TYPES].join(',');
}

// ==========================================
// Re-export Constants
// ==========================================

export {
  ACCEPTED_FILE_EXTENSIONS as ALLOWED_EXTENSIONS,
  ACCEPTED_MIME_TYPES as ALLOWED_MIME_TYPES,
};
