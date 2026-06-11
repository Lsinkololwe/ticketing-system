package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.VerificationDocumentService;
import com.pml.identity.service.storage.FileStorageService;
import com.pml.identity.service.validation.FileUploadValidator;
import com.pml.identity.web.graphql.mutation.VerificationDocumentMutationResolver.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for VerificationDocumentMutationResolver.
 *
 * Tests cover:
 * - Document upload (direct and pre-uploaded)
 * - Presigned URL generation
 * - Admin approval/rejection
 * - Document deletion
 * - File validation
 * - Security (ownership, authentication)
 * - Error scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationDocumentMutationResolver Unit Tests")
class VerificationDocumentMutationResolverTest {

    @Mock
    private VerificationDocumentService documentService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private FileUploadValidator fileUploadValidator;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private VerificationDocumentMutationResolver resolver;

    private static final String USER_ID = "user-123";
    private static final String ORG_ID = "org-456";
    private static final String DOC_ID = "doc-789";
    private static final String ADMIN_ID = "admin-999";

    private Organization testOrganization;
    private VerificationDocument testDocument;
    private Map<String, Object> uploadInput;

    @BeforeEach
    void setUp() {
        testOrganization = Organization.builder()
                .id(ORG_ID)
                .name("Test Organization")
                .ownerId(USER_ID)
                .build();

        testDocument = VerificationDocument.builder()
                .id(DOC_ID)
                .organizationId(ORG_ID)
                .documentType("BUSINESS_REGISTRATION")
                .documentUrl("https://s3.amazonaws.com/docs/registration.pdf")
                .fileName("registration.pdf")
                .fileSize(1024L * 500) // 500KB
                .mimeType("application/pdf")
                .status(DocumentStatus.PENDING)
                .uploadedAt(Instant.now())
                .build();

        uploadInput = new HashMap<>();
        uploadInput.put("documentType", "BUSINESS_REGISTRATION");
        uploadInput.put("fileName", "registration.pdf");
        uploadInput.put("fileSize", 1024L * 500);
        uploadInput.put("mimeType", "application/pdf");
        uploadInput.put("documentUrl", "https://s3.amazonaws.com/docs/registration.pdf");
    }

    // ========================================================================
    // DOCUMENT UPLOAD MUTATIONS
    // ========================================================================

    @Nested
    @DisplayName("uploadVerificationDocument - Pre-uploaded Document")
    class UploadDocumentTests {

        @Test
        @DisplayName("Should upload document when valid pre-uploaded URL provided")
        void shouldUploadDocument_WhenPreUploadedUrl() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.valid());
            when(documentService.upload(
                    eq(ORG_ID),
                    eq("BUSINESS_REGISTRATION"),
                    anyString(),
                    eq("registration.pdf"),
                    eq(1024L * 500),
                    eq("application/pdf")
            )).thenReturn(Mono.just(testDocument));

            // When
            Mono<VerificationDocumentUploadResponse> result =
                    resolver.uploadVerificationDocument(uploadInput, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.success()).isTrue();
                        assertThat(response.document()).isNotNull();
                        assertThat(response.document().getId()).isEqualTo(DOC_ID);
                        assertThat(response.errors()).isEmpty();
                    })
                    .verifyComplete();

            verify(documentService).upload(
                    eq(ORG_ID),
                    eq("BUSINESS_REGISTRATION"),
                    anyString(),
                    eq("registration.pdf"),
                    eq(1024L * 500),
                    eq("application/pdf")
            );
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // When
            Mono<VerificationDocumentUploadResponse> result =
                    resolver.uploadVerificationDocument(uploadInput, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.success()).isFalse();
                        assertThat(response.message()).contains("Authentication required");
                    })
                    .verifyComplete();

            verify(documentService, never()).upload(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should fail when organization not found")
        void shouldFail_WhenOrganizationNotFound() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<VerificationDocumentUploadResponse> result =
                    resolver.uploadVerificationDocument(uploadInput, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.success()).isFalse();
                        assertThat(response.message()).contains("Organization not found");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when file validation fails")
        void shouldFail_WhenValidationFails() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.invalid("File too large"));

            // When
            Mono<VerificationDocumentUploadResponse> result =
                    resolver.uploadVerificationDocument(uploadInput, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.success()).isFalse();
                        assertThat(response.message()).contains("File too large");
                        assertThat(response.errors()).hasSize(1);
                        assertThat(response.errors().get(0).code())
                                .isEqualTo(FileUploadErrorCode.VALIDATION_FAILED);
                    })
                    .verifyComplete();

            verify(documentService, never()).upload(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should fail when documentUrl is missing (direct upload not implemented)")
        void shouldFail_WhenDirectUploadAttempted() {
            // Given
            Map<String, Object> inputWithoutUrl = new HashMap<>(uploadInput);
            inputWithoutUrl.remove("documentUrl");

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.valid());

            // When
            Mono<VerificationDocumentUploadResponse> result =
                    resolver.uploadVerificationDocument(inputWithoutUrl, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.success()).isFalse();
                        assertThat(response.message()).contains("Direct file upload not yet implemented");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle service errors gracefully")
        void shouldHandleServiceErrors() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.valid());
            when(documentService.upload(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // When
            Mono<VerificationDocumentUploadResponse> result =
                    resolver.uploadVerificationDocument(uploadInput, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.success()).isFalse();
                        assertThat(response.message()).contains("Upload failed");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("requestDocumentUploadUrl - Presigned URL")
    class RequestUploadUrlTests {

        @Test
        @DisplayName("Should generate presigned URL when valid request")
        void shouldGeneratePresignedUrl_WhenValid() {
            // Given
            Map<String, Object> urlRequestInput = new HashMap<>();
            urlRequestInput.put("documentType", "BUSINESS_REGISTRATION");
            urlRequestInput.put("fileName", "registration.pdf");
            urlRequestInput.put("fileSize", 1024L * 500);
            urlRequestInput.put("mimeType", "application/pdf");

            String presignedUrl = "https://s3.amazonaws.com/presigned-url?expires=...";
            String fileKey = "organizations/org-456/verification-documents/business_registration/uuid-registration.pdf";

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.valid());
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileStorageService.generatePresignedUrl(anyString(), eq(15)))
                    .thenReturn(Mono.just(presignedUrl));

            // When
            Mono<DocumentUploadUrlResponse> result =
                    resolver.requestDocumentUploadUrl(urlRequestInput, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.uploadUrl()).isEqualTo(presignedUrl);
                        assertThat(response.fileKey()).contains("organizations");
                        assertThat(response.fileKey()).contains(ORG_ID);
                        assertThat(response.expiresAt()).isNotNull();
                        assertThat(response.maxFileSize()).isEqualTo(10 * 1024 * 1024L);
                        assertThat(response.allowedMimeTypes()).contains("application/pdf");
                    })
                    .verifyComplete();

            verify(fileStorageService).generatePresignedUrl(anyString(), eq(15));
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // Given
            Map<String, Object> urlRequestInput = new HashMap<>();
            urlRequestInput.put("documentType", "BUSINESS_REGISTRATION");
            urlRequestInput.put("fileName", "registration.pdf");
            urlRequestInput.put("fileSize", 1024L * 500);
            urlRequestInput.put("mimeType", "application/pdf");

            // When
            Mono<DocumentUploadUrlResponse> result =
                    resolver.requestDocumentUploadUrl(urlRequestInput, null);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Authentication required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when validation fails")
        void shouldFail_WhenValidationFails() {
            // Given
            Map<String, Object> urlRequestInput = new HashMap<>();
            urlRequestInput.put("documentType", "BUSINESS_REGISTRATION");
            urlRequestInput.put("fileName", "registration.pdf");
            urlRequestInput.put("fileSize", 1024L * 500);
            urlRequestInput.put("mimeType", "application/pdf");

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.invalid("Invalid MIME type"));

            // When
            Mono<DocumentUploadUrlResponse> result =
                    resolver.requestDocumentUploadUrl(urlRequestInput, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Invalid MIME type")
                    )
                    .verify();

            verify(fileStorageService, never()).generatePresignedUrl(any(), anyInt());
        }

        @Test
        @DisplayName("Should fail when organization not found")
        void shouldFail_WhenOrganizationNotFound() {
            // Given
            Map<String, Object> urlRequestInput = new HashMap<>();
            urlRequestInput.put("documentType", "BUSINESS_REGISTRATION");
            urlRequestInput.put("fileName", "registration.pdf");
            urlRequestInput.put("fileSize", 1024L * 500);
            urlRequestInput.put("mimeType", "application/pdf");

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(fileUploadValidator.validateRawFile(anyString(), anyString(), anyLong(), any()))
                    .thenReturn(FileUploadValidator.ValidationResult.valid());
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<DocumentUploadUrlResponse> result =
                    resolver.requestDocumentUploadUrl(urlRequestInput, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Organization not found")
                    )
                    .verify();
        }
    }

    // ========================================================================
    // ADMIN APPROVAL MUTATIONS
    // ========================================================================

    @Nested
    @DisplayName("approveVerificationDocument - Admin Approval")
    class ApproveDocumentTests {

        @Test
        @DisplayName("Should approve document when admin requests")
        void shouldApproveDocument_WhenAdmin() {
            // Given
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            VerificationDocument approvedDoc = testDocument.toBuilder()
                    .status(DocumentStatus.APPROVED)
                    .reviewedBy(ADMIN_ID)
                    .reviewedAt(Instant.now())
                    .build();
            when(documentService.approve(DOC_ID, ADMIN_ID))
                    .thenReturn(Mono.just(approvedDoc));

            // When
            Mono<VerificationDocument> result =
                    resolver.approveVerificationDocument(DOC_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
                        assertThat(doc.getReviewedBy()).isEqualTo(ADMIN_ID);
                        assertThat(doc.getReviewedAt()).isNotNull();
                    })
                    .verifyComplete();

            verify(documentService).approve(DOC_ID, ADMIN_ID);
        }

        @Test
        @DisplayName("Should handle null JWT gracefully")
        void shouldHandleNullJwt() {
            // Given
            VerificationDocument approvedDoc = testDocument.toBuilder()
                    .status(DocumentStatus.APPROVED)
                    .build();
            when(documentService.approve(DOC_ID, "system"))
                    .thenReturn(Mono.just(approvedDoc));

            // When
            Mono<VerificationDocument> result =
                    resolver.approveVerificationDocument(DOC_ID, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
                    })
                    .verifyComplete();

            verify(documentService).approve(DOC_ID, "system");
        }
    }

    @Nested
    @DisplayName("rejectVerificationDocument - Admin Rejection")
    class RejectDocumentTests {

        @Test
        @DisplayName("Should reject document when admin provides reason")
        void shouldRejectDocument_WithReason() {
            // Given
            String reason = "Document is expired";
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            VerificationDocument rejectedDoc = testDocument.toBuilder()
                    .status(DocumentStatus.REJECTED)
                    .rejectionReason(reason)
                    .reviewedBy(ADMIN_ID)
                    .build();
            when(documentService.reject(DOC_ID, reason, ADMIN_ID))
                    .thenReturn(Mono.just(rejectedDoc));

            // When
            Mono<VerificationDocument> result =
                    resolver.rejectVerificationDocument(DOC_ID, reason, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.REJECTED);
                        assertThat(doc.getRejectionReason()).isEqualTo(reason);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when reason is blank")
        void shouldFail_WhenReasonIsBlank() {
            // When
            Mono<VerificationDocument> result =
                    resolver.rejectVerificationDocument(DOC_ID, "", jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Rejection reason is required")
                    )
                    .verify();

            verify(documentService, never()).reject(any(), any(), any());
        }

        @Test
        @DisplayName("Should fail when reason is null")
        void shouldFail_WhenReasonIsNull() {
            // When
            Mono<VerificationDocument> result =
                    resolver.rejectVerificationDocument(DOC_ID, null, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Rejection reason is required")
                    )
                    .verify();
        }
    }

    // ========================================================================
    // DOCUMENT DELETION
    // ========================================================================

    @Nested
    @DisplayName("deleteVerificationDocument - Delete Document")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete document when owner requests")
        void shouldDeleteDocument_WhenOwner() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(documentService.findById(DOC_ID))
                    .thenReturn(Mono.just(testDocument));
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileStorageService.delete(anyString()))
                    .thenReturn(Mono.empty());
            when(documentService.delete(DOC_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = resolver.deleteVerificationDocument(DOC_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .expectNext(true)
                    .verifyComplete();

            verify(fileStorageService).delete(anyString());
            verify(documentService).delete(DOC_ID);
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // When
            Mono<Boolean> result = resolver.deleteVerificationDocument(DOC_ID, null);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Authentication required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when document not found")
        void shouldFail_WhenDocumentNotFound() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(documentService.findById(DOC_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = resolver.deleteVerificationDocument(DOC_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Document not found")
                    )
                    .verify();

            verify(documentService, never()).delete(any());
        }

        @Test
        @DisplayName("Should fail when document does not belong to user's organization")
        void shouldFail_WhenNotOwner() {
            // Given
            String otherOrgId = "other-org-999";
            VerificationDocument otherDoc = testDocument.toBuilder()
                    .organizationId(otherOrgId)
                    .build();

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(documentService.findById(DOC_ID))
                    .thenReturn(Mono.just(otherDoc));
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Boolean> result = resolver.deleteVerificationDocument(DOC_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Document does not belong to your organization")
                    )
                    .verify();

            verify(documentService, never()).delete(any());
        }

        @Test
        @DisplayName("Should delete from storage even if database delete fails")
        void shouldAttemptStorageDelete_BeforeDatabaseDelete() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(documentService.findById(DOC_ID))
                    .thenReturn(Mono.just(testDocument));
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(fileStorageService.delete(anyString()))
                    .thenReturn(Mono.empty());
            when(documentService.delete(DOC_ID))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // When
            Mono<Boolean> result = resolver.deleteVerificationDocument(DOC_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            // Storage delete should have been attempted
            verify(fileStorageService).delete(anyString());
        }
    }
}
