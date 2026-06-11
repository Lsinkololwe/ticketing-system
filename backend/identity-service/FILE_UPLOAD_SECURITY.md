# Secure File Upload Implementation

## Overview

This document describes the OWASP-compliant file upload workflow for verification documents in the Identity Service.

## Architecture

### Two Upload Flows

#### 1. Client-Side Upload (RECOMMENDED for Production)

```
┌─────────────┐                                ┌─────────────────┐
│   Client    │                                │  Identity Svc   │
│  (React/RN) │                                │  (GraphQL API)  │
└──────┬──────┘                                └────────┬────────┘
       │                                                │
       │  1. requestDocumentUploadUrl(input)           │
       │──────────────────────────────────────────────▶│
       │                                                │
       │  2. Generate presigned URL (S3)               │
       │                                                │
       │◀──────────────────────────────────────────────│
       │  { uploadUrl, fileKey, expiresAt, ... }       │
       │                                                │
       │  3. Upload file to S3 via presigned URL       │
       │─────────────────────────────────────────────────▶
       │                                                  │
       │  4. uploadVerificationDocument(documentUrl)      │
       │────────────────────────────────────────────────▶│
       │                                                  │
       │  5. Validate & save metadata to MongoDB         │
       │                                                  │
       │◀────────────────────────────────────────────────│
       │  { success, document }                          │
       │                                                  │
```

**Benefits:**
- Reduces server load (no file proxying)
- Faster uploads (direct to S3)
- Better mobile experience (handles interruptions)
- Scales to millions of uploads

#### 2. Server-Side Upload (Future Implementation)

Uses GraphQL multipart request specification for direct file upload to the backend.

## Security Features (OWASP Compliance)

### 1. File Type Validation (Whitelist Approach)

**Threat Mitigated:** Remote Code Execution, Content-Type Spoofing

**Implementation:**

```java
// FileUploadValidator.java

// Step 1: Extension whitelist
private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
    "pdf", "jpg", "jpeg", "png", "webp"
);

// Step 2: MIME type whitelist
private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
    "application/pdf", "image/jpeg", "image/png", "image/webp"
);

// Step 3: Magic number validation (prevents spoofing)
private static final List<MagicNumber> MAGIC_NUMBERS = Arrays.asList(
    new MagicNumber("PDF", new byte[]{0x25, 0x50, 0x44, 0x46}, "application/pdf"),
    new MagicNumber("JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg"),
    // ...
);
```

**Why This Works:**
- Extension alone is unreliable (easily spoofed)
- MIME type in headers can be forged
- Magic numbers are the actual file signature (hard to fake)

### 2. File Size Limits

**Threat Mitigated:** Denial of Service, Storage Exhaustion

**Configuration:**

```yaml
file-upload:
  max-file-size: 10485760  # 10MB in bytes
```

**Enforcement:**
- Validated in `FileUploadValidator.validateFileContent()`
- Enforced at storage layer (S3 presigned URL has size limit)
- Rejected before processing to prevent resource exhaustion

### 3. Filename Sanitization

**Threat Mitigated:** Path Traversal, Directory Traversal

**Implementation:**

```java
// Extract basename (prevents ../../../etc/passwd attacks)
String basename = Paths.get(filename).getFileName().toString();

// Reject path separators
if (basename.contains("..") || basename.contains("/") || basename.contains("\\")) {
    return ValidationResult.invalid("Path traversal detected");
}

// Sanitize special characters
return basename.replaceAll("[^a-zA-Z0-9._-]", "_");
```

**Attack Prevention:**
- `../../../etc/passwd` → Rejected
- `file<script>.pdf` → `file_script_.pdf`
- `invoice/../../secrets.txt` → Rejected

### 4. Secure File Storage

**Threat Mitigated:** Unauthorized Access, Enumeration, Data Exposure

**S3 Security:**

```java
// Unique UUID prevents enumeration
String fileKey = String.format(
    "organizations/%s/verification-documents/%s/%s-%s",
    organizationId,
    documentType.toLowerCase(),
    UUID.randomUUID(),  // Prevents guessing file URLs
    sanitizedFilename
);

// Server-side encryption at rest
PutObjectRequest putRequest = PutObjectRequest.builder()
    .serverSideEncryption(ServerSideEncryption.AES256)
    .build();
```

**Access Control:**
- Bucket is NOT public
- Files accessed via presigned URLs only
- URLs expire after 60 minutes (configurable)
- Organization-scoped folder structure

### 5. Presigned URL Security

**Threat Mitigated:** Unauthorized Access, Link Sharing

**Implementation:**

```java
// Generate time-limited presigned URL
GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
    .signatureDuration(Duration.ofMinutes(60))  // 1-hour expiry
    .getObjectRequest(getObjectRequest)
    .build();
```

**Security Properties:**
- URL is valid for limited time only
- Cannot be used after expiration
- Requires AWS signature (cannot be forged)
- Can be revoked by deleting the object

### 6. Input Validation

**Threat Mitigated:** Injection Attacks, XSS, SQL Injection

**Bean Validation:**

```java
@NotBlank(message = "Document type is required")
private String documentType;

@NotBlank(message = "Document URL is required")
private String documentUrl;

@NotNull
@Min(value = 1, message = "File size must be positive")
private Long fileSize;
```

### 7. Malware Scanning (Future)

**Threat Mitigated:** Malware Upload, Virus Distribution

**Integration Points:**

```yaml
file-upload:
  malware-scanning:
    enabled: false  # Enable in production
    clamav-host: localhost
    clamav-port: 3310
```

**Workflow:**
1. File uploaded to temporary storage
2. ClamAV scans file asynchronously
3. If clean → Move to permanent storage
4. If infected → Delete, notify user, log incident

## GraphQL Mutations

### 1. Request Upload URL

```graphql
mutation RequestUploadUrl {
  requestDocumentUploadUrl(input: {
    documentType: "ID_DOCUMENT"
    fileName: "national-id.pdf"
    fileSize: 2048576
    mimeType: "application/pdf"
  }) {
    uploadUrl       # Presigned S3 URL for PUT request
    fileKey         # Storage key (save for next mutation)
    expiresAt       # URL expiration timestamp
    maxFileSize     # 10485760 (10MB)
    allowedMimeTypes # ["application/pdf", "image/jpeg", ...]
  }
}
```

### 2. Upload Document (After S3 Upload)

```graphql
mutation UploadDocument {
  uploadVerificationDocument(input: {
    documentType: "ID_DOCUMENT"
    documentUrl: "https://s3.amazonaws.com/bucket/organizations/123/..."
    fileName: "national-id.pdf"
    fileSize: 2048576
    mimeType: "application/pdf"
  }) {
    success
    message
    document {
      id
      documentType
      status
      uploadedAt
    }
    errors {
      field
      message
      code
    }
  }
}
```

### 3. Admin Approve Document

```graphql
mutation ApproveDocument {
  approveVerificationDocument(documentId: "doc-123") {
    id
    status          # APPROVED
    verifiedAt
    verifiedById
  }
}
```

### 4. Admin Reject Document

```graphql
mutation RejectDocument {
  rejectVerificationDocument(
    documentId: "doc-123"
    reason: "ID card is blurry and unreadable"
  ) {
    id
    status          # REJECTED
    rejectionReason
  }
}
```

### 5. Delete Document

```graphql
mutation DeleteDocument {
  deleteVerificationDocument(documentId: "doc-123")
}
```

## Frontend Implementation Guide

### React Example (Client-Side Upload)

```typescript
import { useMutation } from '@apollo/client';

function DocumentUpload({ documentType }) {
  const [requestUrl] = useMutation(REQUEST_UPLOAD_URL);
  const [uploadDocument] = useMutation(UPLOAD_VERIFICATION_DOCUMENT);

  const handleFileUpload = async (file: File) => {
    // Step 1: Request presigned URL from backend
    const { data } = await requestUrl({
      variables: {
        input: {
          documentType,
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type,
        },
      },
    });

    const { uploadUrl, fileKey } = data.requestDocumentUploadUrl;

    // Step 2: Upload file to S3 using presigned URL
    const uploadResponse = await fetch(uploadUrl, {
      method: 'PUT',
      body: file,
      headers: {
        'Content-Type': file.type,
      },
    });

    if (!uploadResponse.ok) {
      throw new Error('S3 upload failed');
    }

    // Step 3: Notify backend about successful upload
    const { data: uploadData } = await uploadDocument({
      variables: {
        input: {
          documentType,
          documentUrl: uploadUrl.split('?')[0], // Remove query params
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type,
        },
      },
    });

    return uploadData.uploadVerificationDocument.document;
  };

  return (
    <input
      type="file"
      accept=".pdf,.jpg,.jpeg,.png,.webp"
      onChange={(e) => handleFileUpload(e.target.files[0])}
    />
  );
}
```

## Configuration

### Development (Local Storage)

```yaml
file-storage:
  type: local
  local:
    base-path: ./uploads
    serve-files: true
```

### Production (AWS S3)

```yaml
file-storage:
  type: s3

aws:
  region: us-east-1
  s3:
    bucket:
      verification-documents: ticketing-verification-docs
    encryption:
      enabled: true
      type: AES256  # or aws:kms for KMS encryption
```

### Environment Variables

```bash
# Storage
FILE_STORAGE_TYPE=s3
AWS_REGION=us-east-1
AWS_S3_BUCKET_VERIFICATION_DOCS=ticketing-verification-docs

# AWS Credentials (use IAM roles in production)
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY

# Malware Scanning (optional)
CLAMAV_HOST=clamav-service
CLAMAV_PORT=3310
```

## Testing

### Unit Tests

```java
@Test
void shouldRejectFileWithInvalidExtension() {
    ValidationResult result = fileUploadValidator.validateRawFile(
        "malware.exe",
        "application/octet-stream",
        1024L,
        new byte[]{0x4D, 0x5A} // PE executable header
    );

    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("not allowed"));
}

@Test
void shouldRejectFileExceedingSizeLimit() {
    ValidationResult result = fileUploadValidator.validateRawFile(
        "large.pdf",
        "application/pdf",
        11 * 1024 * 1024L, // 11MB
        new byte[]{0x25, 0x50, 0x44, 0x46}
    );

    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("exceeds maximum"));
}
```

### Integration Tests

```java
@Test
@WithMockUser(roles = "ORGANIZER")
void shouldUploadDocumentSuccessfully() {
    // Mock S3 upload
    when(fileStorageService.upload(any(), any(), any()))
        .thenReturn(Mono.just(new UploadResult(...)));

    // Execute mutation
    String mutation = """
        mutation {
          uploadVerificationDocument(input: {
            documentType: "ID_DOCUMENT"
            fileName: "id.pdf"
            fileSize: 1024
            mimeType: "application/pdf"
          }) {
            success
            document { id }
          }
        }
    """;

    // Assert success
    webTestClient.post()
        .uri("/graphql")
        .bodyValue(Map.of("query", mutation))
        .exchange()
        .expectStatus().isOk();
}
```

## Security Checklist

- [x] File type validation using magic numbers
- [x] File size limits enforced
- [x] Filename sanitization (path traversal prevention)
- [x] Server-side encryption at rest (S3 AES256)
- [x] Presigned URLs with expiration
- [x] Organization-scoped file storage
- [x] UUID-based filenames (prevents enumeration)
- [x] Audit logging for document operations
- [x] Role-based access control (ORGANIZER, ADMIN)
- [ ] Malware scanning (ClamAV integration - future)
- [ ] Rate limiting on upload mutations
- [ ] Content Security Policy headers
- [ ] CORS configuration for S3 bucket

## Performance Considerations

### S3 Presigned URLs

- **Latency:** ~50ms to generate presigned URL
- **Scalability:** Unlimited (no backend bottleneck)
- **Cost:** $0.005 per 1,000 PUT requests

### Local Storage (Development)

- **Disk Space:** 10MB per file × 1,000 users = 10GB
- **Performance:** Limited by disk I/O
- **NOT recommended for production**

### Database Impact

- **Document metadata:** ~1KB per document
- **MongoDB:** Can handle millions of document records
- **Indexes:** organizationId, status, documentType

## Troubleshooting

### "File type not allowed"

**Cause:** File extension or MIME type not in whitelist

**Solution:** Check `file-upload.allowed-extensions` in application.yml

### "File size exceeds maximum"

**Cause:** File larger than 10MB

**Solution:** Compress images, reduce PDF quality, or increase `max-file-size`

### "S3 upload failed"

**Cause:** Invalid AWS credentials or bucket permissions

**Solution:**
1. Verify `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
2. Check S3 bucket policy allows PutObject
3. Ensure bucket exists in correct region

### "Presigned URL expired"

**Cause:** URL used after 60-minute expiration

**Solution:** Request new URL via `requestDocumentUploadUrl`

## References

- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
- [AWS S3 Presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html)
- [GraphQL Multipart Request Spec](https://github.com/jaydenseric/graphql-multipart-request-spec)
- [Netflix DGS Framework](https://netflix.github.io/dgs/)

## License

Proprietary - PML Ticketing System
