package com.pml.identity.service.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * AWS S3 File Storage Implementation
 *
 * Provides secure file upload to S3 with:
 * - Server-side encryption (SSE-S3 or SSE-KMS)
 * - Presigned URLs for secure access
 * - Folder structure by organization
 * - MD5 checksum verification
 * - Metadata tagging
 *
 * Security Features:
 * - Files stored with unique UUIDs to prevent enumeration
 * - Bucket is NOT public (uses presigned URLs)
 * - Server-side encryption at rest
 * - Supports object lifecycle policies
 *
 * NOTE: This implementation assumes AWS SDK v2 is configured.
 * For production, inject S3AsyncClient and S3Presigner as beans.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true", matchIfMissing = false)
public class S3FileStorageService implements FileStorageService {

    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket.verification-documents}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${file-upload.presigned-url.default-expiry-minutes:60}")
    private int defaultExpiryMinutes;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    @Override
    public Mono<UploadResult> upload(FilePart filePart, String organizationId, String documentType) {
        String filename = filePart.filename();
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        log.info("Uploading file '{}' to S3 for organization: {}", filename, organizationId);

        // Generate unique storage key
        String fileKey = generateFileKey(organizationId, documentType, filename);

        // Read file content into byte array (for MD5 checksum and upload)
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    long fileSize = bytes.length;
                    String checksum = calculateMD5(bytes);

                    // Upload to S3
                    return uploadToS3(fileKey, bytes, contentType, organizationId, documentType)
                            .flatMap(putResponse -> {
                                // Generate presigned URL for initial access
                                return generatePresignedUrl(fileKey, defaultExpiryMinutes)
                                        .map(presignedUrl -> new UploadResult(
                                                presignedUrl,
                                                fileKey,
                                                filename,
                                                fileSize,
                                                contentType,
                                                checksum
                                        ));
                            });
                })
                .doOnSuccess(result -> log.info("Successfully uploaded file to S3: {}", result.fileKey()))
                .doOnError(error -> log.error("Failed to upload file to S3: {}", error.getMessage()));
    }

    @Override
    public Mono<String> generatePresignedUrl(String fileKey, int expiryMinutes) {
        log.debug("Generating presigned URL for: {} (expiry: {} minutes)", fileKey, expiryMinutes);

        return Mono.fromCallable(() -> {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        });
    }

    @Override
    public Mono<Void> delete(String fileKey) {
        log.info("Deleting file from S3: {}", fileKey);

        return Mono.fromFuture(() -> {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            return s3AsyncClient.deleteObject(deleteRequest);
        })
                .then()
                .doOnSuccess(v -> log.info("Successfully deleted file: {}", fileKey))
                .doOnError(error -> log.error("Failed to delete file {}: {}", fileKey, error.getMessage()));
    }

    @Override
    public Mono<Boolean> exists(String fileKey) {
        log.debug("Checking if file exists: {}", fileKey);

        return Mono.fromFuture(() -> {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            return s3AsyncClient.headObject(headRequest);
        })
                .map(response -> true)
                .onErrorResume(NoSuchKeyException.class, e -> Mono.just(false));
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Uploads file bytes to S3 with metadata and encryption
     */
    private Mono<PutObjectResponse> uploadToS3(
            String fileKey,
            byte[] bytes,
            String contentType,
            String organizationId,
            String documentType) {

        return Mono.fromFuture(() -> {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    // Server-side encryption (use SSE-KMS in production)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    // Metadata for filtering/lifecycle
                    .metadata(java.util.Map.of(
                            "organization-id", organizationId,
                            "document-type", documentType,
                            "uploaded-at", Instant.now().toString()
                    ))
                    // Tagging for cost allocation and lifecycle
                    .tagging("Environment=production&Type=verification-document")
                    .build();

            AsyncRequestBody requestBody = AsyncRequestBody.fromBytes(bytes);
            return s3AsyncClient.putObject(putRequest, requestBody);
        });
    }

    /**
     * Generates a unique file key with organization scoping
     *
     * Format: organizations/{orgId}/verification-documents/{docType}/{uuid}-{filename}
     *
     * This structure:
     * - Prevents file collisions
     * - Enables org-level access control
     * - Supports S3 lifecycle policies per prefix
     * - Makes enumeration attacks harder (UUID)
     */
    private String generateFileKey(String organizationId, String documentType, String filename) {
        String sanitizedFilename = sanitizeFilename(filename);
        String uuid = UUID.randomUUID().toString();

        return String.format("organizations/%s/verification-documents/%s/%s-%s",
                organizationId,
                documentType.toLowerCase(),
                uuid,
                sanitizedFilename
        );
    }

    /**
     * Sanitizes filename for safe storage
     * Removes path components and special characters
     */
    private String sanitizeFilename(String filename) {
        // Extract basename
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;

        // Remove or replace unsafe characters
        return basename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Calculates MD5 checksum for file integrity verification
     */
    private String calculateMD5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 algorithm not available, skipping checksum", e);
            return "";
        }
    }
}
