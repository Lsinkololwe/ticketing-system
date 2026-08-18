/**
 * OpenAPI artifact for the verification-document REST surface.
 *
 * Documents do not travel over GraphQL — spec ET-ORG-001 R4 and D-11 put them on
 * REST with presigned URLs, deliberately, so that no document byte passes
 * through a resolver. That means the onboarding e2e suite needs a second
 * Microcks API alongside the GraphQL one.
 *
 * Microcks serves REST mocks from an OpenAPI spec's `examples`, and the app
 * builds its URLs as `${NEXT_PUBLIC_API_URL}/api/v1/organizations/...` — so the
 * paths below are written with that `/api/v1` prefix and `NEXT_PUBLIC_API_URL`
 * is pointed at this API's Microcks mock root.
 *
 * @see https://microcks.io/documentation/references/artifacts/openapi-conventions/
 */

export const DOCUMENTS_API_NAME = 'Onboarding Documents API';
export const DOCUMENTS_API_VERSION = '1.0';

export interface StubDocument {
  id: string;
  documentType: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  fileName: string;
  rejectionReason?: string | null;
}

/** A document that satisfies its requirement. */
export const uploaded = (documentType: string): StubDocument => ({
  id: `doc-${documentType}`,
  documentType,
  status: 'PENDING',
  fileName: `${documentType.toLowerCase()}.pdf`,
});

/**
 * A document a reviewer turned down.
 * Does NOT satisfy its requirement — spec R3.
 */
export const rejected = (documentType: string, reason: string): StubDocument => ({
  id: `doc-${documentType}`,
  documentType,
  status: 'REJECTED',
  fileName: `${documentType.toLowerCase()}.pdf`,
  rejectionReason: reason,
});

function fullDocument(doc: StubDocument) {
  return {
    id: doc.id,
    organizationId: 'org-e2e-1',
    documentType: doc.documentType,
    documentUrl: `https://storage.test/${doc.documentType}`,
    fileName: doc.fileName,
    fileSize: 2048,
    mimeType: 'application/pdf',
    status: doc.status,
    uploadedAt: '2026-08-01T09:00:00Z',
    verifiedAt: null,
    verifiedById: null,
    rejectionReason: doc.rejectionReason ?? null,
  };
}

/**
 * Build the documents OpenAPI artifact for a given document set.
 *
 * Microcks matches on the example name, and with a single named example per
 * operation it serves that one — which is what lets each spec declare exactly
 * the upload state it wants to test.
 */
export function buildDocumentsOpenApi(documents: StubDocument[]) {
  const list = documents.map(fullDocument);

  return {
    openapi: '3.0.2',
    info: {
      title: DOCUMENTS_API_NAME,
      version: DOCUMENTS_API_VERSION,
      description: 'Verification documents for the organizer onboarding e2e suite.',
    },
    paths: {
      '/api/v1/organizations/{orgId}/documents': {
        parameters: [
          {
            name: 'orgId',
            in: 'path',
            required: true,
            schema: { type: 'string' },
            examples: { current: { value: 'org-e2e-1' } },
          },
        ],
        get: {
          operationId: 'listDocuments',
          summary: 'List verification documents',
          responses: {
            '200': {
              description: 'The documents on file',
              content: {
                'application/json': {
                  schema: { type: 'array', items: { type: 'object' } },
                  examples: { current: { value: list } },
                },
              },
            },
          },
        },
        post: {
          operationId: 'registerDocument',
          summary: 'Register an uploaded document',
          requestBody: {
            content: {
              'application/json': {
                schema: { type: 'object' },
                examples: { current: { value: { documentType: 'NATIONAL_ID' } } },
              },
            },
          },
          responses: {
            '200': {
              description: 'Registered',
              content: {
                'application/json': {
                  schema: { type: 'object' },
                  examples: {
                    current: {
                      value: {
                        success: true,
                        message: 'ok',
                        document: fullDocument(uploaded('NATIONAL_ID')),
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
      '/api/v1/organizations/{orgId}/documents/upload-url': {
        parameters: [
          {
            name: 'orgId',
            in: 'path',
            required: true,
            schema: { type: 'string' },
            examples: { current: { value: 'org-e2e-1' } },
          },
        ],
        post: {
          operationId: 'requestUploadUrl',
          summary: 'Issue a presigned PUT URL',
          requestBody: {
            content: {
              'application/json': {
                schema: { type: 'object' },
                examples: { current: { value: { documentType: 'NATIONAL_ID' } } },
              },
            },
          },
          responses: {
            '200': {
              description: 'A presigned URL',
              content: {
                'application/json': {
                  schema: { type: 'object' },
                  examples: {
                    current: {
                      value: {
                        success: true,
                        message: 'ok',
                        // Points back at this same mock: the browser PUTs the
                        // bytes here, standing in for object storage.
                        uploadUrl: 'http://localhost/__upload/document',
                        fileKey: 'key-document',
                        expiresAt: '2026-08-01T10:00:00Z',
                        maxFileSize: 10485760,
                        allowedMimeTypes: ['image/jpeg', 'image/png', 'application/pdf'],
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };
}
