package com.pml.identity.service.storage;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * File Storage Service Interface
 *
 * Abstracts cloud storage operations (S3, Cloudinary, Azure Blob, etc.)
 * Provides secure file upload, retrieval, and deletion.
 *
 * Implementations should:
 * - Generate unique filenames to prevent collisions
 * - Store files outside webroot
 * - Implement access control (presigned URLs)
 * - Support malware scanning integration
 * - Handle file versioning/replacement
 */
public interface FileStorageService {

    /**
     * Uploads a file to cloud storage
     *
     * @param filePart       Spring WebFlux FilePart
     * @param organizationId Organization ID for scoping
     * @param documentType   Type of document (for folder structure)
     * @return Mono<UploadResult> with file URL and metadata
     */
    Mono<UploadResult> upload(FilePart filePart, String organizationId, String documentType);

    /**
     * Generates a presigned URL for secure file access
     *
     * @param fileKey      File key/path in storage
     * @param expiryMinutes URL expiry time in minutes
     * @return Mono<String> presigned URL
     */
    Mono<String> generatePresignedUrl(String fileKey, int expiryMinutes);

    /**
     * Deletes a file from storage
     *
     * @param fileKey File key/path in storage
     * @return Mono<Void> completion signal
     */
    Mono<Void> delete(String fileKey);

    /**
     * Checks if a file exists in storage
     *
     * @param fileKey File key/path in storage
     * @return Mono<Boolean> true if exists
     */
    Mono<Boolean> exists(String fileKey);

    /**
     * Result of file upload operation
     */
    record UploadResult(
            String fileUrl,        // Public URL or presigned URL
            String fileKey,        // Storage key/path
            String fileName,       // Original filename (sanitized)
            Long fileSize,         // File size in bytes
            String contentType,    // MIME type
            String checksum        // File hash (MD5/SHA256) for integrity
    ) {
    }
}
