package com.pml.identity.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Local File Storage Implementation (Development/Testing Only)
 *
 * Stores files on local filesystem for development.
 * DO NOT use in production - use S3FileStorageService instead.
 *
 * Activated via: file-storage.type=local
 *
 * Features:
 * - Stores files in configurable directory
 * - Generates unique filenames
 * - Calculates MD5 checksums
 * - Simulates presigned URLs (returns file:// URLs)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file-storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${file-storage.local.base-path:./uploads}")
    private String basePath;

    @Value("${server.port:8083}")
    private String serverPort;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    @Override
    public Mono<UploadResult> upload(FilePart filePart, String organizationId, String documentType) {
        String filename = filePart.filename();
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        log.info("Uploading file '{}' to local storage for organization: {}", filename, organizationId);

        // Generate storage path
        String fileKey = generateFileKey(organizationId, documentType, filename);
        Path targetPath = Paths.get(basePath, fileKey);

        // Ensure directory exists
        return Mono.fromCallable(() -> {
                    Files.createDirectories(targetPath.getParent());
                    return targetPath;
                })
                .flatMap(path -> {
                    // Save file
                    return filePart.transferTo(path)
                            .then(Mono.fromCallable(() -> {
                                long fileSize = Files.size(path);
                                byte[] fileBytes = Files.readAllBytes(path);
                                String checksum = calculateMD5(fileBytes);

                                // Generate file URL (use http://localhost:port/uploads/...)
                                String fileUrl = String.format("http://localhost:%s/uploads/%s",
                                        serverPort, fileKey);

                                return new UploadResult(
                                        fileUrl,
                                        fileKey,
                                        filename,
                                        fileSize,
                                        contentType,
                                        checksum
                                );
                            }));
                })
                .doOnSuccess(result -> log.info("Successfully uploaded file to local storage: {}", result.fileKey()))
                .doOnError(error -> log.error("Failed to upload file to local storage: {}", error.getMessage()));
    }

    @Override
    public Mono<String> generatePresignedUrl(String fileKey, int expiryMinutes) {
        log.debug("Generating presigned URL for: {} (local storage ignores expiry)", fileKey);

        // Local storage doesn't support presigned URLs - return direct URL
        String fileUrl = String.format("http://localhost:%s/uploads/%s", serverPort, fileKey);
        return Mono.just(fileUrl);
    }

    @Override
    public Mono<Void> delete(String fileKey) {
        log.info("Deleting file from local storage: {}", fileKey);

        return Mono.fromCallable(() -> {
                    Path filePath = Paths.get(basePath, fileKey);
                    Files.deleteIfExists(filePath);
                    return null;
                })
                .then()
                .doOnSuccess(v -> log.info("Successfully deleted file: {}", fileKey))
                .doOnError(error -> log.error("Failed to delete file {}: {}", fileKey, error.getMessage()));
    }

    @Override
    public Mono<Boolean> exists(String fileKey) {
        log.debug("Checking if file exists: {}", fileKey);

        return Mono.fromCallable(() -> {
            Path filePath = Paths.get(basePath, fileKey);
            return Files.exists(filePath);
        });
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Generates a unique file key with organization scoping
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
     */
    private String sanitizeFilename(String filename) {
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
        return basename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Calculates MD5 checksum
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
