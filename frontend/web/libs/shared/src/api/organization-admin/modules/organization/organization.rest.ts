/**
 * Organization REST API Operations
 *
 * REST API operations for organization-admin app, primarily for document uploads.
 *
 * This module implements file upload using REST APIs instead of GraphQL for better:
 * - Progress tracking (native browser support)
 * - Performance (streaming, no buffering)
 * - Security (no CSRF issues with multipart)
 * - Scalability (direct S3 upload, no server proxying)
 *
 * @see backend/identity-service/src/main/java/com/pml/identity/web/rest/VerificationDocumentRestController.java
 *
 * References:
 * - https://wundergraph.com/blog/graphql_file_uploads_evaluating_the_5_most_common_approaches
 * - https://www.apollographql.com/blog/file-upload-best-practices
 * - https://www.bezkoder.com/react-hooks-file-upload/
 */

import { useState, useCallback } from 'react';

// ==================== Types ====================

export interface UploadUrlRequest {
  documentType: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
}

export interface PresignedUploadUrlResponse {
  success: boolean;
  message: string;
  uploadUrl?: string;
  fileKey?: string;
  expiresAt?: string;
  maxFileSize?: number;
  allowedMimeTypes?: string[];
  error?: string;
}

export interface RegisterDocumentRequest {
  documentType: string;
  documentUrl: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
}

export interface DocumentResponse {
  success: boolean;
  message: string;
  document?: VerificationDocument;
  error?: string;
}

export interface VerificationDocument {
  id: string;
  organizationId: string;
  documentType: string;
  documentUrl: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  uploadedAt: string;
  verifiedAt?: string;
  verifiedById?: string;
  rejectionReason?: string;
}

export interface UploadProgress {
  loaded: number;
  total: number;
  percentage: number;
}

// ==================== Configuration ====================

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const IDENTITY_SERVICE_URL = `${API_BASE_URL}/api/v1`;

/**
 * Get authorization headers from session storage
 */
function getAuthHeaders(): HeadersInit {
  const token = typeof window !== 'undefined' ? sessionStorage.getItem('access_token') : null;

  return {
    'Authorization': token ? `Bearer ${token}` : '',
    'Content-Type': 'application/json',
  };
}

// ==================== API Functions ====================

/**
 * Step 1: Request a presigned URL for uploading a document
 *
 * This gets a temporary upload URL from S3 that allows the client to upload
 * directly without routing through the backend server.
 *
 * @param organizationId Organization ID
 * @param request Upload request with file metadata
 * @returns Presigned URL response with upload URL and file key
 */
export async function requestUploadUrl(
  organizationId: string,
  request: UploadUrlRequest
): Promise<PresignedUploadUrlResponse> {
  const response = await fetch(
    `${IDENTITY_SERVICE_URL}/organizations/${organizationId}/documents/upload-url`,
    {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Failed to request upload URL' }));
    throw new Error(error.error || error.message || 'Failed to request upload URL');
  }

  return response.json();
}

/**
 * Step 2: Upload file directly to S3 using presigned URL
 *
 * This uploads the file directly to S3 with progress tracking.
 * The presigned URL already contains authentication, so no additional headers needed.
 *
 * **Important**: According to AWS SDK docs, when using presigned URLs with metadata,
 * you must include the metadata headers in the upload request.
 *
 * @param presignedUrl Presigned URL from requestUploadUrl
 * @param file File to upload
 * @param onProgress Progress callback (optional)
 * @returns Promise that resolves when upload completes
 *
 * @see https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.md
 */
export async function uploadToS3(
  presignedUrl: string,
  file: File,
  onProgress?: (progress: UploadProgress) => void
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    // Track upload progress
    if (onProgress) {
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable) {
          onProgress({
            loaded: event.loaded,
            total: event.total,
            percentage: Math.round((event.loaded / event.total) * 100),
          });
        }
      });
    }

    // Handle completion
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`Upload failed with status ${xhr.status}`));
      }
    });

    // Handle errors
    xhr.addEventListener('error', () => {
      reject(new Error('Upload failed due to network error'));
    });

    xhr.addEventListener('abort', () => {
      reject(new Error('Upload was aborted'));
    });

    // Send the request
    xhr.open('PUT', presignedUrl);
    xhr.setRequestHeader('Content-Type', file.type);
    xhr.send(file);
  });
}

/**
 * Step 3: Register document metadata after successful S3 upload
 *
 * This saves the document metadata to the database after the file has been
 * successfully uploaded to S3.
 *
 * @param organizationId Organization ID
 * @param request Document registration request
 * @returns Document response with saved document
 */
export async function registerDocument(
  organizationId: string,
  request: RegisterDocumentRequest
): Promise<DocumentResponse> {
  const response = await fetch(
    `${IDENTITY_SERVICE_URL}/organizations/${organizationId}/documents`,
    {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Failed to register document' }));
    throw new Error(error.error || error.message || 'Failed to register document');
  }

  return response.json();
}

/**
 * Complete upload workflow: request URL, upload to S3, register metadata
 *
 * This is a convenience function that combines all three steps:
 * 1. Request presigned URL
 * 2. Upload file to S3
 * 3. Register document metadata
 *
 * @param organizationId Organization ID
 * @param documentType Document type (e.g., "BUSINESS_LICENSE")
 * @param file File to upload
 * @param onProgress Progress callback (optional)
 * @returns Saved verification document
 */
export async function uploadDocument(
  organizationId: string,
  documentType: string,
  file: File,
  onProgress?: (progress: UploadProgress) => void
): Promise<VerificationDocument> {
  // Step 1: Request presigned URL
  const urlResponse = await requestUploadUrl(organizationId, {
    documentType,
    fileName: file.name,
    mimeType: file.type,
    fileSize: file.size,
  });

  if (!urlResponse.success || !urlResponse.uploadUrl || !urlResponse.fileKey) {
    throw new Error(urlResponse.error || 'Failed to get upload URL');
  }

  // Step 2: Upload to S3
  await uploadToS3(urlResponse.uploadUrl, file, onProgress);

  // Step 3: Register document metadata
  // Construct the S3 URL from the file key
  const documentUrl = urlResponse.uploadUrl.split('?')[0]; // Remove query params to get base URL

  const registerResponse = await registerDocument(organizationId, {
    documentType,
    documentUrl,
    fileName: file.name,
    mimeType: file.type,
    fileSize: file.size,
  });

  if (!registerResponse.success || !registerResponse.document) {
    throw new Error(registerResponse.error || 'Failed to register document');
  }

  return registerResponse.document;
}

/**
 * List all documents for an organization
 *
 * @param organizationId Organization ID
 * @param status Optional status filter
 * @returns List of verification documents
 */
export async function listDocuments(
  organizationId: string,
  status?: 'PENDING' | 'APPROVED' | 'REJECTED'
): Promise<VerificationDocument[]> {
  const url = new URL(`${IDENTITY_SERVICE_URL}/organizations/${organizationId}/documents`);
  if (status) {
    url.searchParams.append('status', status);
  }

  const response = await fetch(url.toString(), {
    method: 'GET',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to list documents');
  }

  return response.json();
}

/**
 * Get a single document by ID
 *
 * @param documentId Document ID
 * @returns Verification document
 */
export async function getDocument(documentId: string): Promise<VerificationDocument> {
  const response = await fetch(`${IDENTITY_SERVICE_URL}/documents/${documentId}`, {
    method: 'GET',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get document');
  }

  return response.json();
}

/**
 * Delete a verification document
 *
 * This deletes both the file from S3 and the metadata from the database.
 *
 * @param documentId Document ID
 */
export async function deleteDocument(documentId: string): Promise<void> {
  const response = await fetch(`${IDENTITY_SERVICE_URL}/documents/${documentId}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to delete document');
  }
}

// ==================== React Hooks ====================

export interface UseDocumentUploadReturn {
  /**
   * Upload a document with progress tracking
   */
  upload: (documentType: string, file: File) => Promise<VerificationDocument>;

  /**
   * Delete a document
   */
  remove: (documentId: string) => Promise<void>;

  /**
   * Refresh the list of documents
   */
  refresh: () => Promise<void>;

  /**
   * Current upload progress (0-100)
   */
  progress: UploadProgress;

  /**
   * Whether an upload is in progress
   */
  isUploading: boolean;

  /**
   * Whether documents are being loaded
   */
  isLoading: boolean;

  /**
   * Upload or operation error
   */
  error: string | null;

  /**
   * List of documents for the organization
   */
  documents: VerificationDocument[];
}

/**
 * Hook for uploading verification documents with progress tracking
 *
 * @example
 * ```tsx
 * function DocumentUploadForm({ organizationId }) {
 *   const { upload, progress, isUploading, error } = useDocumentUpload(organizationId);
 *
 *   const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
 *     const file = e.target.files?.[0];
 *     if (!file) return;
 *
 *     try {
 *       const document = await upload('BUSINESS_LICENSE', file);
 *       console.log('Uploaded:', document);
 *     } catch (err) {
 *       console.error('Upload failed:', err);
 *     }
 *   };
 *
 *   return (
 *     <div>
 *       <input type="file" onChange={handleFileSelect} disabled={isUploading} />
 *       {isUploading && <ProgressBar percentage={progress.percentage} />}
 *       {error && <ErrorMessage>{error}</ErrorMessage>}
 *     </div>
 *   );
 * }
 * ```
 *
 * @param organizationId Organization ID
 * @returns Upload functions and state
 */
export function useDocumentUpload(organizationId: string): UseDocumentUploadReturn {
  const [progress, setProgress] = useState<UploadProgress>({
    loaded: 0,
    total: 0,
    percentage: 0,
  });
  const [isUploading, setIsUploading] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [documents, setDocuments] = useState<VerificationDocument[]>([]);

  /**
   * Upload a document with progress tracking
   */
  const upload = useCallback(
    async (documentType: string, file: File): Promise<VerificationDocument> => {
      setIsUploading(true);
      setError(null);
      setProgress({ loaded: 0, total: file.size, percentage: 0 });

      try {
        const document = await uploadDocument(
          organizationId,
          documentType,
          file,
          (progressData) => {
            setProgress(progressData);
          }
        );

        // Add to documents list
        setDocuments((prev) => [...prev, document]);

        // Reset progress
        setProgress({ loaded: 0, total: 0, percentage: 0 });

        return document;
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Upload failed';
        setError(errorMessage);
        throw err;
      } finally {
        setIsUploading(false);
      }
    },
    [organizationId]
  );

  /**
   * Delete a document
   */
  const remove = useCallback(async (documentId: string): Promise<void> => {
    setError(null);

    try {
      await deleteDocument(documentId);

      // Remove from documents list
      setDocuments((prev) => prev.filter((doc) => doc.id !== documentId));
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Delete failed';
      setError(errorMessage);
      throw err;
    }
  }, []);

  /**
   * Refresh the list of documents
   */
  const refresh = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);

    try {
      const docs = await listDocuments(organizationId);
      setDocuments(docs);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to load documents';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  }, [organizationId]);

  return {
    upload,
    remove,
    refresh,
    progress,
    isUploading,
    isLoading,
    error,
    documents,
  };
}

/**
 * Hook for tracking upload status with visual feedback
 *
 * This is a simpler version that only tracks the upload state
 * without managing the documents list.
 *
 * @param organizationId Organization ID
 * @returns Upload function and state
 */
export function useSimpleUpload(organizationId: string) {
  const [progress, setProgress] = useState(0);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = useCallback(
    async (documentType: string, file: File): Promise<VerificationDocument> => {
      setIsUploading(true);
      setError(null);
      setProgress(0);

      try {
        const document = await uploadDocument(
          organizationId,
          documentType,
          file,
          (progressData) => {
            setProgress(progressData.percentage);
          }
        );

        setProgress(100);
        return document;
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Upload failed';
        setError(errorMessage);
        throw err;
      } finally {
        setTimeout(() => {
          setIsUploading(false);
          setProgress(0);
        }, 1000); // Keep 100% visible for 1 second
      }
    },
    [organizationId]
  );

  return {
    upload,
    progress,
    isUploading,
    error,
  };
}
