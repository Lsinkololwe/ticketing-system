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
 * Document statuses
 */
export const DOCUMENT_STATUSES = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'EXPIRED',
] as const;

/**
 * Document types for KYC/KYB
 */
export const DOCUMENT_TYPES = [
  'ID_DOCUMENT',
  'BUSINESS_LICENSE',
  'TAX_CERTIFICATE',
  'PACRA_CERTIFICATE',
  'BANK_STATEMENT',
  'UTILITY_BILL',
  'PROOF_OF_ADDRESS',
  'COMPANY_PROFILE',
  'DIRECTOR_ID',
  'SHAREHOLDER_AGREEMENT',
  'OTHER',
] as const;

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
export const documentTypeSchema = z.enum(DOCUMENT_TYPES, {
  message: 'Please select a valid document type',
});

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
  status: z.enum(DOCUMENT_STATUSES).optional(),
  documentType: z.enum(DOCUMENT_TYPES).optional(),
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
