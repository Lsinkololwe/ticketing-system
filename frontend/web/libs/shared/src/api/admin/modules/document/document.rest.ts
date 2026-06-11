/**
 * Document REST API Operations (Admin)
 *
 * Admin-specific REST API operations for document verification and management.
 *
 * @see backend/identity-service/src/main/java/com/pml/identity/web/rest/VerificationDocumentRestController.java
 */

// ==================== Types ====================

interface RestVerificationDocument {
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

interface RestDocumentListResponse {
  documents: RestVerificationDocument[];
  totalCount: number;
  pendingCount: number;
  approvedCount: number;
  rejectedCount: number;
}

// ==================== Configuration ====================

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const IDENTITY_SERVICE_URL = `${API_BASE_URL}/api/v1`;

/**
 * Get authorization headers from session storage
 */
function getAuthHeaders(): Record<string, string> {
  const token = typeof window !== 'undefined' ? sessionStorage.getItem('access_token') : null;

  return {
    'Authorization': token ? `Bearer ${token}` : '',
    'Content-Type': 'application/json',
  };
}

// ==================== API Functions ====================

/**
 * Get a single document by ID (Admin)
 *
 * @param documentId Document ID
 * @returns Verification document
 */
export async function getDocument(documentId: string): Promise<RestVerificationDocument> {
  const response = await fetch(`${IDENTITY_SERVICE_URL}/admin/documents/${documentId}`, {
    method: 'GET',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get document');
  }

  return response.json();
}

/**
 * List all documents with optional filters (Admin)
 *
 * @param status Optional status filter
 * @param organizationId Optional organization filter
 * @returns Document list response
 */
export async function listAllDocuments(
  status?: 'PENDING' | 'APPROVED' | 'REJECTED',
  organizationId?: string
): Promise<RestDocumentListResponse> {
  const url = new URL(`${IDENTITY_SERVICE_URL}/admin/documents`);
  if (status) {
    url.searchParams.append('status', status);
  }
  if (organizationId) {
    url.searchParams.append('organizationId', organizationId);
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
 * Download a document file (Admin)
 *
 * @param documentId Document ID
 * @returns Blob of the document file
 */
export async function downloadDocument(documentId: string): Promise<Blob> {
  const response = await fetch(`${IDENTITY_SERVICE_URL}/admin/documents/${documentId}/download`, {
    method: 'GET',
    headers: {
      'Authorization': getAuthHeaders()['Authorization'],
    },
  });

  if (!response.ok) {
    throw new Error('Failed to download document');
  }

  return response.blob();
}
