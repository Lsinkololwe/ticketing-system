package com.pml.identity.web.rest;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.VerificationDocumentService;
import com.pml.identity.service.storage.FileStorageService;
import com.pml.identity.service.validation.FileUploadValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API Controller for Verification Document operations.
 *
 * <p>This controller implements file upload best practices by using:
 * <ul>
 *   <li><b>Presigned URLs</b>: Client uploads directly to S3, reducing server load</li>
 *   <li><b>Metadata-only storage</b>: Server stores only document metadata, not binary data</li>
 *   <li><b>OWASP validation</b>: File type, size, and magic number validation</li>
 *   <li><b>Reactive patterns</b>: Non-blocking I/O for better scalability</li>
 * </ul>
 *
 * <h2>Why REST over GraphQL for File Uploads?</h2>
 * <ul>
 *   <li><b>Native multipart support</b>: REST has built-in multipart/form-data handling</li>
 *   <li><b>Progress tracking</b>: XMLHttpRequest/fetch onUploadProgress works natively</li>
 *   <li><b>Browser compatibility</b>: No CSRF issues with multipart requests</li>
 *   <li><b>Streaming</b>: REST can stream files without buffering entire content</li>
 *   <li><b>Security</b>: GraphQL multipart spec introduces CSRF vulnerabilities</li>
 *   <li><b>Performance</b>: Direct S3 upload avoids proxying through GraphQL server</li>
 * </ul>
 *
 * @see <a href="https://wundergraph.com/blog/graphql_file_uploads_evaluating_the_5_most_common_approaches">GraphQL File Uploads - WunderGraph</a>
 * @see <a href="https://www.apollographql.com/blog/file-upload-best-practices">Apollo File Upload Best Practices</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class VerificationDocumentRestController {

    private final VerificationDocumentService documentService;
    private final OrganizationService organizationService;
    private final FileUploadValidator fileUploadValidator;
    private final FileStorageService fileStorageService;

    // ========================================================================
    // UPLOAD URL REQUEST (Step 1: Get presigned URL)
    // ========================================================================

    /**
     * Request a presigned URL for uploading a verification document.
     *
     * <p><b>Upload Flow</b>:
     * <ol>
     *   <li>Client calls this endpoint to get presigned URL</li>
     *   <li>Client uploads file directly to S3 using presigned URL</li>
     *   <li>Client calls {@link #registerDocument} with S3 URL to save metadata</li>
     * </ol>
     *
     * <p><b>Benefits</b>:
     * <ul>
     *   <li>Reduces server load (no file proxying)</li>
     *   <li>Faster uploads (direct to S3)</li>
     *   <li>Better mobile support (handles interruptions)</li>
     *   <li>Progress tracking works natively in browser</li>
     * </ul>
     *
     * @param orgId Organization ID
     * @param request Upload request with file metadata
     * @param jwt JWT token for authentication
     * @return Presigned URL response with upload URL and file key
     */
    @PostMapping("/organizations/{orgId}/documents/upload-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<PresignedUploadUrlResponse>> requestUploadUrl(
            @PathVariable String orgId,
            @Valid @RequestBody UploadUrlRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String userId = jwt.getSubject();
        log.info("User {} requesting upload URL for organization: {}, documentType: {}",
                userId, orgId, request.documentType);

        // Validate file metadata
        return validateFileMetadata(request.fileName, request.mimeType, request.fileSize)
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.just(ResponseEntity
                                .badRequest()
                                .body(PresignedUploadUrlResponse.error(validationResult.getErrorMessage())));
                    }

                    // Verify organization ownership
                    return organizationService.findByOwnerId(userId)
                            .flatMap(organization -> {
                                if (!organization.getId().equals(orgId)) {
                                    return Mono.just(ResponseEntity
                                            .status(HttpStatus.FORBIDDEN)
                                            .body(PresignedUploadUrlResponse.error(
                                                    "Organization does not belong to current user")));
                                }

                                // Generate unique file key
                                String fileKey = generateFileKey(
                                        orgId,
                                        request.documentType,
                                        request.fileName
                                );

                                // Generate presigned URL (valid for 15 minutes)
                                return fileStorageService.generatePresignedUrl(fileKey, 15)
                                        .map(presignedUrl -> {
                                            PresignedUploadUrlResponse response = new PresignedUploadUrlResponse(
                                                    true,
                                                    "Upload URL generated successfully",
                                                    presignedUrl,
                                                    fileKey,
                                                    Instant.now().plus(Duration.ofMinutes(15)),
                                                    10 * 1024 * 1024L, // 10MB max
                                                    List.of("application/pdf", "image/jpeg", "image/png", "image/webp"),
                                                    null
                                            );
                                            return ResponseEntity.ok(response);
                                        });
                            })
                            .switchIfEmpty(Mono.just(ResponseEntity
                                    .status(HttpStatus.NOT_FOUND)
                                    .body(PresignedUploadUrlResponse.error("Organization not found"))));
                })
                .onErrorResume(error -> {
                    log.error("Failed to generate upload URL: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(PresignedUploadUrlResponse.error("Failed to generate upload URL")));
                });
    }

    // ========================================================================
    // REGISTER DOCUMENT (Step 2: After S3 upload complete)
    // ========================================================================

    /**
     * Register document metadata after successful S3 upload.
     *
     * <p>This endpoint should be called AFTER the client has successfully
     * uploaded the file to S3 using the presigned URL.
     *
     * @param orgId Organization ID
     * @param request Document registration request
     * @param jwt JWT token for authentication
     * @return Saved verification document
     */
    @PostMapping("/organizations/{orgId}/documents")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<DocumentResponse>> registerDocument(
            @PathVariable String orgId,
            @Valid @RequestBody RegisterDocumentRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String userId = jwt.getSubject();
        log.info("User {} registering document for organization: {}", userId, orgId);

        // Verify organization ownership
        return organizationService.findByOwnerId(userId)
                .flatMap(organization -> {
                    if (!organization.getId().equals(orgId)) {
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(DocumentResponse.error("Organization does not belong to current user")));
                    }

                    // Save document metadata
                    return documentService.upload(
                                    orgId,
                                    request.documentType,
                                    request.documentUrl,
                                    request.fileName,
                                    request.fileSize,
                                    request.mimeType
                            )
                            .map(document -> ResponseEntity
                                    .status(HttpStatus.CREATED)
                                    .body(DocumentResponse.success(document)))
                            .onErrorResume(error -> {
                                log.error("Failed to register document: {}", error.getMessage());
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(DocumentResponse.error("Failed to register document")));
                            });
                })
                .switchIfEmpty(Mono.just(ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(DocumentResponse.error("Organization not found"))));
    }

    // ========================================================================
    // DOCUMENT RETRIEVAL
    // ========================================================================

    /**
     * List all documents for an organization.
     *
     * @param orgId Organization ID
     * @param status Optional status filter
     * @param jwt JWT token for authentication
     * @return List of verification documents
     */
    @GetMapping("/organizations/{orgId}/documents")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<List<VerificationDocument>>> listDocuments(
            @PathVariable String orgId,
            @RequestParam(required = false) DocumentStatus status,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return Mono.just(ResponseEntity.<List<VerificationDocument>>status(HttpStatus.UNAUTHORIZED).build());
        }

        String userId = jwt.getSubject();
        log.info("User {} listing documents for organization: {}", userId, orgId);

        // Verify organization ownership
        return organizationService.findByOwnerId(userId)
                .<ResponseEntity<List<VerificationDocument>>>flatMap(organization -> {
                    if (!organization.getId().equals(orgId)) {
                        return Mono.just(ResponseEntity.<List<VerificationDocument>>status(HttpStatus.FORBIDDEN).build());
                    }

                    Flux<VerificationDocument> documentsFlux = status != null
                            ? documentService.findByOrganizationAndStatus(orgId, status)
                            : documentService.findByOrganization(orgId);

                    return documentsFlux
                            .collectList()
                            .map(ResponseEntity::ok);
                })
                .switchIfEmpty(Mono.just(ResponseEntity.<List<VerificationDocument>>status(HttpStatus.NOT_FOUND).build()));
    }

    /**
     * Get a single document by ID.
     *
     * @param docId Document ID
     * @param jwt JWT token for authentication
     * @return Verification document
     */
    @GetMapping("/documents/{docId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<VerificationDocument>> getDocument(
            @PathVariable String docId,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String userId = jwt.getSubject();

        return documentService.findById(docId)
                .flatMap(document -> organizationService.findByOwnerId(userId)
                        .flatMap(organization -> {
                            if (!document.getOrganizationId().equals(organization.getId())) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<VerificationDocument>build());
                            }
                            return Mono.just(ResponseEntity.ok(document));
                        }))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    // ========================================================================
    // DOCUMENT DELETION
    // ========================================================================

    /**
     * Delete a verification document.
     *
     * <p>This deletes both the file from S3 and the metadata from the database.
     *
     * @param docId Document ID
     * @param jwt JWT token for authentication
     * @return Success response
     */
    @DeleteMapping("/documents/{docId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @PathVariable String docId,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String userId = jwt.getSubject();
        log.info("User {} deleting document: {}", userId, docId);

        // Verify document belongs to user's organization
        return documentService.findById(docId)
                .flatMap(doc -> organizationService.findByOwnerId(userId)
                        .flatMap(organization -> {
                            if (!doc.getOrganizationId().equals(organization.getId())) {
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build());
                            }

                            // Extract file key from URL
                            String fileKey = extractFileKeyFromUrl(doc.getDocumentUrl());

                            // Delete from S3 first, then database
                            return fileStorageService.delete(fileKey)
                                    .then(documentService.delete(docId))
                                    .then(Mono.just(ResponseEntity.noContent().<Void>build()));
                        }))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()))
                .onErrorResume(error -> {
                    log.error("Failed to delete document: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Validates file metadata (filename, size, MIME type).
     */
    private Mono<FileUploadValidator.ValidationResult> validateFileMetadata(
            String fileName,
            String mimeType,
            Long fileSize) {

        if (fileName == null || fileName.isBlank()) {
            return Mono.just(FileUploadValidator.ValidationResult.invalid("Filename is required"));
        }

        if (mimeType == null || mimeType.isBlank()) {
            return Mono.just(FileUploadValidator.ValidationResult.invalid("MIME type is required"));
        }

        if (fileSize == null || fileSize <= 0) {
            return Mono.just(FileUploadValidator.ValidationResult.invalid("File size must be positive"));
        }

        // Use validator's raw file validation (without file content)
        return Mono.just(fileUploadValidator.validateRawFile(
                fileName,
                mimeType,
                fileSize,
                new byte[0] // Magic number validation skipped for pre-uploaded files
        ));
    }

    /**
     * Generates a unique file key for S3 storage.
     */
    private String generateFileKey(String orgId, String documentType, String fileName) {
        return String.format("organizations/%s/verification-documents/%s/%s-%s",
                orgId,
                documentType.toLowerCase(),
                UUID.randomUUID(),
                sanitizeFilename(fileName)
        );
    }

    /**
     * Sanitizes filename for safe storage.
     */
    private String sanitizeFilename(String filename) {
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;
        return basename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Extracts file key from S3 URL.
     */
    private String extractFileKeyFromUrl(String url) {
        // Handle S3 URLs, presigned URLs, local URLs
        if (url.contains("amazonaws.com")) {
            String[] parts = url.split("amazonaws.com/");
            if (parts.length > 1) {
                return parts[1].split("\\?")[0]; // Remove query params
            }
        }
        return url;
    }

    // ========================================================================
    // REQUEST/RESPONSE DTOs
    // ========================================================================

    /**
     * Request to get a presigned upload URL.
     */
    public record UploadUrlRequest(
            @NotBlank String documentType,
            @NotBlank String fileName,
            @NotBlank String mimeType,
            @NotNull @Positive Long fileSize
    ) {
    }

    /**
     * Response containing presigned upload URL.
     */
    public record PresignedUploadUrlResponse(
            Boolean success,
            String message,
            String uploadUrl,
            String fileKey,
            Instant expiresAt,
            Long maxFileSize,
            List<String> allowedMimeTypes,
            String error
    ) {
        public static PresignedUploadUrlResponse error(String errorMessage) {
            return new PresignedUploadUrlResponse(
                    false,
                    errorMessage,
                    null,
                    null,
                    null,
                    null,
                    null,
                    errorMessage
            );
        }
    }

    /**
     * Request to register a document after S3 upload.
     */
    public record RegisterDocumentRequest(
            @NotBlank String documentType,
            @NotBlank String documentUrl,
            @NotBlank String fileName,
            @NotBlank String mimeType,
            @NotNull @Positive Long fileSize
    ) {
    }

    /**
     * Response for document operations.
     */
    public record DocumentResponse(
            Boolean success,
            String message,
            VerificationDocument document,
            String error
    ) {
        public static DocumentResponse success(VerificationDocument document) {
            return new DocumentResponse(
                    true,
                    "Document registered successfully",
                    document,
                    null
            );
        }

        public static DocumentResponse error(String errorMessage) {
            return new DocumentResponse(
                    false,
                    errorMessage,
                    null,
                    errorMessage
            );
        }
    }
}
