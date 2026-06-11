/**
 * Document Type Definitions
 *
 * This file provides comprehensive types for the verification document domain
 * supporting document upload, review, and approval workflows.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

// ==========================================
// Document Status Enum
// ==========================================

/**
 * Document verification status
 */
export type DocumentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

/**
 * Document types for KYC/KYB verification
 */
export type DocumentType =
  | 'ID_DOCUMENT'
  | 'BUSINESS_LICENSE'
  | 'TAX_CERTIFICATE'
  | 'PACRA_CERTIFICATE'
  | 'BANK_STATEMENT'
  | 'UTILITY_BILL'
  | 'PROOF_OF_ADDRESS'
  | 'COMPANY_PROFILE'
  | 'DIRECTOR_ID'
  | 'SHAREHOLDER_AGREEMENT'
  | 'OTHER';

// ==========================================
// UI Helper Functions
// ==========================================

/**
 * Get color for document status (for UI badges)
 */
export function getDocumentStatusColor(status: DocumentStatus): {
  color: string;
  bg: string;
} {
  const colors: Record<DocumentStatus, { color: string; bg: string }> = {
    PENDING: { color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)' },
    APPROVED: { color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)' },
    REJECTED: { color: '#EF4444', bg: 'rgba(239, 68, 68, 0.1)' },
    EXPIRED: { color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)' },
  };
  return colors[status] || { color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)' };
}

/**
 * Get display label for document status
 */
export function getDocumentStatusLabel(status: DocumentStatus): string {
  const labels: Record<DocumentStatus, string> = {
    PENDING: 'Pending Review',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    EXPIRED: 'Expired',
  };
  return labels[status] || status;
}

/**
 * Get display label for document type
 */
export function getDocumentTypeLabel(type: DocumentType): string {
  const labels: Record<DocumentType, string> = {
    ID_DOCUMENT: 'ID Document',
    BUSINESS_LICENSE: 'Business License',
    TAX_CERTIFICATE: 'Tax Certificate',
    PACRA_CERTIFICATE: 'PACRA Certificate',
    BANK_STATEMENT: 'Bank Statement',
    UTILITY_BILL: 'Utility Bill',
    PROOF_OF_ADDRESS: 'Proof of Address',
    COMPANY_PROFILE: 'Company Profile',
    DIRECTOR_ID: 'Director ID',
    SHAREHOLDER_AGREEMENT: 'Shareholder Agreement',
    OTHER: 'Other Document',
  };
  return labels[type] || type;
}

/**
 * Get description for document type
 */
export function getDocumentTypeDescription(type: DocumentType): string {
  const descriptions: Record<DocumentType, string> = {
    ID_DOCUMENT: 'National Registration Card (NRC), Passport, or Driver\'s License',
    BUSINESS_LICENSE: 'Valid business operating license from local authority',
    TAX_CERTIFICATE: 'Tax clearance certificate from ZRA',
    PACRA_CERTIFICATE: 'Company registration certificate from PACRA',
    BANK_STATEMENT: 'Recent bank statement (within last 3 months)',
    UTILITY_BILL: 'Recent utility bill for address verification',
    PROOF_OF_ADDRESS: 'Document confirming business address',
    COMPANY_PROFILE: 'Company profile or business plan document',
    DIRECTOR_ID: 'ID document of company director',
    SHAREHOLDER_AGREEMENT: 'Shareholder agreement or articles of association',
    OTHER: 'Other supporting document',
  };
  return descriptions[type] || '';
}

// ==========================================
// Main Document Types
// ==========================================

/**
 * Verification document entity
 */
export interface VerificationDocument {
  id: string;
  documentType: string;
  documentUrl: string;
  fileName?: string | null;
  fileSize?: number | null;
  mimeType?: string | null;
  status: DocumentStatus;
  uploadedAt: string;
  verifiedAt?: string | null;
  verifiedById?: string | null;
  verifiedBy?: {
    id: string;
    firstName: string;
    lastName: string;
    fullName: string;
  } | null;
  rejectionReason?: string | null;
}

/**
 * Document list item for admin views
 */
export interface DocumentListItem {
  id: string;
  documentType: string;
  fileName?: string | null;
  status: DocumentStatus;
  uploadedAt: string;
  verifiedAt?: string | null;
}

// ==========================================
// Upload Types
// ==========================================

/**
 * Document upload input for GraphQL
 */
export interface UploadDocumentInput {
  documentType: string;
  file: File;
  description?: string | null;
}

/**
 * Document upload response
 */
export interface VerificationDocumentUploadResponse {
  success: boolean;
  message?: string | null;
  document?: VerificationDocument | null;
  errors?: FileUploadError[] | null;
}

/**
 * File upload error
 */
export interface FileUploadError {
  field: string;
  message: string;
  code: FileUploadErrorCode;
}

/**
 * File upload error codes
 */
export type FileUploadErrorCode =
  | 'FILE_TOO_LARGE'
  | 'INVALID_FILE_TYPE'
  | 'INVALID_MIME_TYPE'
  | 'INVALID_FILENAME'
  | 'CORRUPTED_FILE'
  | 'MALWARE_DETECTED'
  | 'UPLOAD_FAILED'
  | 'VALIDATION_FAILED';

// ==========================================
// Admin Review Types
// ==========================================

/**
 * Document review decision input
 */
export interface ReviewDocumentInput {
  documentId: string;
  approved: boolean;
  rejectionReason?: string | null;
}

// ==========================================
// Filter Types
// ==========================================

/**
 * Document filter input
 */
export interface DocumentFilterInput {
  status?: DocumentStatus | null;
  documentType?: string | null;
  organizationId?: string | null;
  uploadedFrom?: string | null;
  uploadedTo?: string | null;
}

// ==========================================
// Required Documents Configuration
// ==========================================

/**
 * Required document configuration for organization types
 */
export interface RequiredDocumentConfig {
  documentType: DocumentType;
  label: string;
  description: string;
  required: boolean;
  acceptedFormats: string[];
  maxSizeMB: number;
}

/**
 * Get required documents for individual organizers
 */
export function getIndividualRequiredDocuments(): RequiredDocumentConfig[] {
  return [
    {
      documentType: 'ID_DOCUMENT',
      label: 'ID Document',
      description: 'National Registration Card (NRC), Passport, or Driver\'s License',
      required: true,
      acceptedFormats: ['.pdf', '.jpg', '.jpeg', '.png'],
      maxSizeMB: 5,
    },
    {
      documentType: 'PROOF_OF_ADDRESS',
      label: 'Proof of Address',
      description: 'Utility bill or bank statement (within last 3 months)',
      required: true,
      acceptedFormats: ['.pdf', '.jpg', '.jpeg', '.png'],
      maxSizeMB: 5,
    },
  ];
}

/**
 * Get required documents for business organizers
 */
export function getBusinessRequiredDocuments(): RequiredDocumentConfig[] {
  return [
    {
      documentType: 'PACRA_CERTIFICATE',
      label: 'PACRA Certificate',
      description: 'Company registration certificate from PACRA',
      required: true,
      acceptedFormats: ['.pdf', '.jpg', '.jpeg', '.png'],
      maxSizeMB: 5,
    },
    {
      documentType: 'TAX_CERTIFICATE',
      label: 'Tax Clearance Certificate',
      description: 'Valid tax clearance certificate from ZRA',
      required: true,
      acceptedFormats: ['.pdf', '.jpg', '.jpeg', '.png'],
      maxSizeMB: 5,
    },
    {
      documentType: 'DIRECTOR_ID',
      label: 'Director ID',
      description: 'ID document of at least one company director',
      required: true,
      acceptedFormats: ['.pdf', '.jpg', '.jpeg', '.png'],
      maxSizeMB: 5,
    },
    {
      documentType: 'BUSINESS_LICENSE',
      label: 'Business License',
      description: 'Valid business operating license (if applicable)',
      required: false,
      acceptedFormats: ['.pdf', '.jpg', '.jpeg', '.png'],
      maxSizeMB: 5,
    },
    {
      documentType: 'BANK_STATEMENT',
      label: 'Bank Statement',
      description: 'Recent business bank statement',
      required: false,
      acceptedFormats: ['.pdf'],
      maxSizeMB: 5,
    },
  ];
}

// ==========================================
// Helper Functions
// ==========================================

/**
 * Format file size for display
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

/**
 * Check if document can be edited (re-uploaded)
 */
export function canEditDocument(status: DocumentStatus): boolean {
  return ['PENDING', 'REJECTED'].includes(status);
}

/**
 * Check if document can be reviewed (admin)
 */
export function canReviewDocument(status: DocumentStatus): boolean {
  return status === 'PENDING';
}

/**
 * Check if document is approved
 */
export function isDocumentApproved(status: DocumentStatus): boolean {
  return status === 'APPROVED';
}

/**
 * Get accepted MIME types from file extensions
 */
export function getAcceptedMimeTypes(extensions: string[]): string {
  const mimeMap: Record<string, string> = {
    '.pdf': 'application/pdf',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.png': 'image/png',
    '.doc': 'application/msword',
    '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  };

  return extensions
    .map((ext) => mimeMap[ext.toLowerCase()])
    .filter(Boolean)
    .join(',');
}

/**
 * Validate file type against accepted formats
 */
export function isValidFileType(file: File, acceptedFormats: string[]): boolean {
  const extension = `.${file.name.split('.').pop()?.toLowerCase()}`;
  return acceptedFormats.includes(extension);
}

/**
 * Get document completion percentage for an organization
 */
export function getDocumentCompletionPercentage(
  documents: VerificationDocument[],
  requiredDocuments: RequiredDocumentConfig[]
): number {
  const requiredTypes = requiredDocuments
    .filter((d) => d.required)
    .map((d) => d.documentType);

  if (requiredTypes.length === 0) return 100;

  const approvedRequired = documents.filter(
    (d) =>
      requiredTypes.includes(d.documentType as DocumentType) &&
      d.status === 'APPROVED'
  );

  return Math.round((approvedRequired.length / requiredTypes.length) * 100);
}
