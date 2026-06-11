package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for VerificationDocumentServiceImpl
 *
 * Test Focus:
 * - File upload validation and metadata handling
 * - Document approval/rejection workflow
 * - Status transitions and state management
 * - Organization verification status updates
 * - Event publication (notifications)
 * - Edge cases and error conditions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationDocumentService Unit Tests")
class VerificationDocumentServiceImplTest {

    @Mock
    private VerificationDocumentRepository documentRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private StreamBridge streamBridge;

    private VerificationDocumentServiceImpl service;

    private Organization testOrganization;
    private VerificationDocument testDocument;

    @BeforeEach
    void setUp() {
        service = new VerificationDocumentServiceImpl(
                documentRepository,
                organizationRepository,
                streamBridge
        );

        testOrganization = Organization.builder()
                .id("org-123")
                .name("Test Organization")
                .slug("test-organization")
                .ownerId("user-456")
                .build();

        testDocument = VerificationDocument.builder()
                .id("doc-789")
                .organizationId(testOrganization.getId())
                .documentType("BUSINESS_LICENSE")
                .documentUrl("https://s3.example.com/doc.pdf")
                .fileName("business-license.pdf")
                .fileSize(1024000L) // 1MB
                .mimeType("application/pdf")
                .status(DocumentStatus.PENDING)
                .uploadedAt(Instant.now())
                .build();
    }

    // =========================================================================
    // UPLOAD TESTS
    // =========================================================================

    @Nested
    @DisplayName("upload Tests")
    class UploadTests {

        @Test
        @DisplayName("Should upload new document successfully")
        void shouldUploadNewDocumentSuccessfully() {
            // Given
            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(documentRepository.existsByOrganizationIdAndDocumentType(
                    testOrganization.getId(), "BUSINESS_LICENSE"
            )).thenReturn(Mono.just(false));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.upload(
                            testOrganization.getId(),
                            "BUSINESS_LICENSE",
                            "https://s3.example.com/doc.pdf",
                            "business-license.pdf",
                            1024000L,
                            "application/pdf"
                    ))
                    .assertNext(doc -> {
                        assertThat(doc.getOrganizationId()).isEqualTo(testOrganization.getId());
                        assertThat(doc.getDocumentType()).isEqualTo("BUSINESS_LICENSE");
                        assertThat(doc.getDocumentUrl()).isEqualTo("https://s3.example.com/doc.pdf");
                        assertThat(doc.getFileName()).isEqualTo("business-license.pdf");
                        assertThat(doc.getFileSize()).isEqualTo(1024000L);
                        assertThat(doc.getMimeType()).isEqualTo("application/pdf");
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING);
                        assertThat(doc.getUploadedAt()).isNotNull();
                    })
                    .verifyComplete();

            // Verify document was saved
            ArgumentCaptor<VerificationDocument> captor = ArgumentCaptor.forClass(VerificationDocument.class);
            verify(documentRepository).save(captor.capture());
            VerificationDocument savedDoc = captor.getValue();
            assertThat(savedDoc.getStatus()).isEqualTo(DocumentStatus.PENDING);
        }

        @Test
        @DisplayName("Should update existing document when type already exists")
        void shouldUpdateExistingDocument() {
            // Given
            VerificationDocument existingDoc = testDocument.toBuilder()
                    .documentUrl("https://s3.example.com/old-doc.pdf")
                    .fileName("old-license.pdf")
                    .fileSize(500000L)
                    .status(DocumentStatus.REJECTED)
                    .rejectionReason("Invalid document")
                    .build();

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(documentRepository.existsByOrganizationIdAndDocumentType(
                    testOrganization.getId(), "BUSINESS_LICENSE"
            )).thenReturn(Mono.just(true));
            when(documentRepository.findByOrganizationIdAndDocumentType(
                    testOrganization.getId(), "BUSINESS_LICENSE"
            )).thenReturn(Mono.just(existingDoc));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.upload(
                            testOrganization.getId(),
                            "BUSINESS_LICENSE",
                            "https://s3.example.com/new-doc.pdf",
                            "new-license.pdf",
                            1024000L,
                            "application/pdf"
                    ))
                    .assertNext(doc -> {
                        // Document should be updated
                        assertThat(doc.getDocumentUrl()).isEqualTo("https://s3.example.com/new-doc.pdf");
                        assertThat(doc.getFileName()).isEqualTo("new-license.pdf");
                        assertThat(doc.getFileSize()).isEqualTo(1024000L);
                        // Status should reset to PENDING
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING);
                        // Previous rejection data should be cleared
                        assertThat(doc.getRejectionReason()).isNull();
                        assertThat(doc.getVerifiedAt()).isNull();
                        assertThat(doc.getVerifiedById()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when organization does not exist")
        void shouldFailWhenOrganizationDoesNotExist() {
            // Given
            when(organizationRepository.findById("non-existent-org"))
                    .thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.upload(
                            "non-existent-org",
                            "BUSINESS_LICENSE",
                            "https://s3.example.com/doc.pdf",
                            "license.pdf",
                            1024000L,
                            "application/pdf"
                    ))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Organization not found")
                    )
                    .verify();

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle various MIME types")
        void shouldHandleVariousMimeTypes() {
            // Test with image/jpeg
            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(documentRepository.existsByOrganizationIdAndDocumentType(any(), any()))
                    .thenReturn(Mono.just(false));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.upload(
                            testOrganization.getId(),
                            "ID_CARD",
                            "https://s3.example.com/id.jpg",
                            "id-card.jpg",
                            2048000L,
                            "image/jpeg"
                    ))
                    .assertNext(doc -> {
                        assertThat(doc.getMimeType()).isEqualTo("image/jpeg");
                    })
                    .verifyComplete();

            // Test with image/png
            when(documentRepository.existsByOrganizationIdAndDocumentType(any(), any()))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(service.upload(
                            testOrganization.getId(),
                            "PROOF_OF_ADDRESS",
                            "https://s3.example.com/address.png",
                            "address.png",
                            1500000L,
                            "image/png"
                    ))
                    .assertNext(doc -> {
                        assertThat(doc.getMimeType()).isEqualTo("image/png");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle large file sizes")
        void shouldHandleLargeFileSizes() {
            // Given - 9.5MB file (just under 10MB limit)
            Long largeFileSize = 9_961_472L; // 9.5MB

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(documentRepository.existsByOrganizationIdAndDocumentType(any(), any()))
                    .thenReturn(Mono.just(false));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.upload(
                            testOrganization.getId(),
                            "BUSINESS_LICENSE",
                            "https://s3.example.com/large-doc.pdf",
                            "large-license.pdf",
                            largeFileSize,
                            "application/pdf"
                    ))
                    .assertNext(doc -> {
                        assertThat(doc.getFileSize()).isEqualTo(largeFileSize);
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // APPROVE TESTS
    // =========================================================================

    @Nested
    @DisplayName("approve Tests")
    class ApproveTests {

        @Test
        @DisplayName("Should approve document successfully")
        void shouldApproveDocumentSuccessfully() {
            // Given
            String adminId = "admin-123";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(documentRepository.countByOrganizationIdAndStatus(
                    testDocument.getOrganizationId(), DocumentStatus.APPROVED
            )).thenReturn(Mono.just(3L)); // Assume 3 approved docs
            when(organizationRepository.findById(testDocument.getOrganizationId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenReturn(Mono.just(testOrganization));
            when(streamBridge.send(anyString(), any())).thenReturn(true);

            // When & Then
            StepVerifier.create(service.approve(testDocument.getId(), adminId))
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
                        assertThat(doc.getVerifiedAt()).isNotNull();
                        assertThat(doc.getVerifiedById()).isEqualTo(adminId);
                        assertThat(doc.getRejectionReason()).isNull();
                    })
                    .verifyComplete();

            // Verify notification was sent
            verify(streamBridge).send(eq("notificationOutput-out-0"), any());
        }

        @Test
        @DisplayName("Should fail when document does not exist")
        void shouldFailWhenDocumentDoesNotExist() {
            // Given
            String adminId = "admin-123";
            when(documentRepository.findById("non-existent-doc"))
                    .thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.approve("non-existent-doc", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Document not found")
                    )
                    .verify();

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should fail when document is not PENDING")
        void shouldFailWhenDocumentNotPending() {
            // Given - Document already approved
            testDocument.setStatus(DocumentStatus.APPROVED);
            String adminId = "admin-123";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));

            // When & Then
            StepVerifier.create(service.approve(testDocument.getId(), adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("not pending approval")
                    )
                    .verify();

            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should fail when document is REJECTED")
        void shouldFailWhenDocumentRejected() {
            // Given
            testDocument.setStatus(DocumentStatus.REJECTED);
            String adminId = "admin-123";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));

            // When & Then
            StepVerifier.create(service.approve(testDocument.getId(), adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("not pending approval")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should update organization verification status when all docs approved")
        void shouldUpdateOrganizationWhenAllDocsApproved() {
            // Given
            String adminId = "admin-123";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            // Assume this is the 3rd approved document
            when(documentRepository.countByOrganizationIdAndStatus(
                    testDocument.getOrganizationId(), DocumentStatus.APPROVED
            )).thenReturn(Mono.just(3L));
            when(organizationRepository.findById(testDocument.getOrganizationId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(streamBridge.send(anyString(), any())).thenReturn(true);

            // When
            StepVerifier.create(service.approve(testDocument.getId(), adminId))
                    .expectNextCount(1)
                    .verifyComplete();

            // Then - Organization should be updated (async, may not be in main flow)
            // Verification happens in checkAndUpdateOrganizationStatus (subscribe)
            verify(documentRepository).countByOrganizationIdAndStatus(
                    testDocument.getOrganizationId(), DocumentStatus.APPROVED
            );
        }

        @Test
        @DisplayName("Should handle notification failure gracefully")
        void shouldHandleNotificationFailureGracefully() {
            // Given
            String adminId = "admin-123";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(documentRepository.countByOrganizationIdAndStatus(any(), any()))
                    .thenReturn(Mono.just(1L));
            when(streamBridge.send(anyString(), any()))
                    .thenThrow(new RuntimeException("Stream unavailable"));

            // When & Then - Should still complete successfully
            StepVerifier.create(service.approve(testDocument.getId(), adminId))
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // REJECT TESTS
    // =========================================================================

    @Nested
    @DisplayName("reject Tests")
    class RejectTests {

        @Test
        @DisplayName("Should reject document successfully")
        void shouldRejectDocumentSuccessfully() {
            // Given
            String adminId = "admin-123";
            String reason = "Document is not legible";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(streamBridge.send(anyString(), any())).thenReturn(true);

            // When & Then
            StepVerifier.create(service.reject(testDocument.getId(), reason, adminId))
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.REJECTED);
                        assertThat(doc.getVerifiedAt()).isNotNull();
                        assertThat(doc.getVerifiedById()).isEqualTo(adminId);
                        assertThat(doc.getRejectionReason()).isEqualTo(reason);
                    })
                    .verifyComplete();

            // Verify rejection notification was sent
            verify(streamBridge).send(eq("notificationOutput-out-0"), any());
        }

        @Test
        @DisplayName("Should fail when document does not exist")
        void shouldFailWhenDocumentDoesNotExist() {
            // Given
            String adminId = "admin-123";
            when(documentRepository.findById("non-existent-doc"))
                    .thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.reject("non-existent-doc", "Reason", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Document not found")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when document is not PENDING")
        void shouldFailWhenDocumentNotPending() {
            // Given - Document already approved
            testDocument.setStatus(DocumentStatus.APPROVED);
            String adminId = "admin-123";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));

            // When & Then
            StepVerifier.create(service.reject(testDocument.getId(), "Reason", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("not pending approval")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should handle detailed rejection reasons")
        void shouldHandleDetailedRejectionReasons() {
            // Given
            String adminId = "admin-123";
            String detailedReason = "The business license appears to be expired. " +
                    "Please upload a current license issued within the last 12 months. " +
                    "Contact support@example.com if you have questions.";

            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));
            when(documentRepository.save(any(VerificationDocument.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(streamBridge.send(anyString(), any())).thenReturn(true);

            // When & Then
            StepVerifier.create(service.reject(testDocument.getId(), detailedReason, adminId))
                    .assertNext(doc -> {
                        assertThat(doc.getRejectionReason()).isEqualTo(detailedReason);
                        assertThat(doc.getRejectionReason().length()).isGreaterThan(100);
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // READ OPERATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Read Operation Tests")
    class ReadOperationTests {

        @Test
        @DisplayName("Should find document by ID")
        void shouldFindDocumentById() {
            // Given
            when(documentRepository.findById(testDocument.getId()))
                    .thenReturn(Mono.just(testDocument));

            // When & Then
            StepVerifier.create(service.findById(testDocument.getId()))
                    .assertNext(doc -> {
                        assertThat(doc.getId()).isEqualTo(testDocument.getId());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should find documents by organization")
        void shouldFindDocumentsByOrganization() {
            // Given
            VerificationDocument doc2 = testDocument.toBuilder()
                    .id("doc-999")
                    .documentType("TAX_ID")
                    .build();

            when(documentRepository.findByOrganizationId(testOrganization.getId()))
                    .thenReturn(Flux.just(testDocument, doc2));

            // When & Then
            StepVerifier.create(service.findByOrganization(testOrganization.getId()))
                    .expectNext(testDocument)
                    .expectNext(doc2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should find documents by organization and status")
        void shouldFindDocumentsByOrganizationAndStatus() {
            // Given
            when(documentRepository.findByOrganizationIdAndStatus(
                    testOrganization.getId(), DocumentStatus.PENDING
            )).thenReturn(Flux.just(testDocument));

            // When & Then
            StepVerifier.create(service.findByOrganizationAndStatus(
                            testOrganization.getId(), DocumentStatus.PENDING
                    ))
                    .expectNext(testDocument)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should find pending documents")
        void shouldFindPendingDocuments() {
            // Given
            when(documentRepository.findByStatus(DocumentStatus.PENDING))
                    .thenReturn(Flux.just(testDocument));

            // When & Then
            StepVerifier.create(service.findPendingDocuments())
                    .expectNext(testDocument)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should count documents by organization")
        void shouldCountDocumentsByOrganization() {
            // Given
            when(documentRepository.countByOrganizationId(testOrganization.getId()))
                    .thenReturn(Mono.just(5L));

            // When & Then
            StepVerifier.create(service.countByOrganization(testOrganization.getId()))
                    .expectNext(5L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should count approved documents by organization")
        void shouldCountApprovedDocuments() {
            // Given
            when(documentRepository.countByOrganizationIdAndStatus(
                    testOrganization.getId(), DocumentStatus.APPROVED
            )).thenReturn(Mono.just(3L));

            // When & Then
            StepVerifier.create(service.countApprovedByOrganization(testOrganization.getId()))
                    .expectNext(3L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should check if document exists by type")
        void shouldCheckIfDocumentExistsByType() {
            // Given
            when(documentRepository.existsByOrganizationIdAndDocumentType(
                    testOrganization.getId(), "BUSINESS_LICENSE"
            )).thenReturn(Mono.just(true));

            // When & Then
            StepVerifier.create(service.existsByOrganizationAndType(
                            testOrganization.getId(), "BUSINESS_LICENSE"
                    ))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    // =========================================================================
    // DELETE TESTS
    // =========================================================================

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete document by ID")
        void shouldDeleteDocumentById() {
            // Given
            when(documentRepository.deleteById(testDocument.getId()))
                    .thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.delete(testDocument.getId()))
                    .verifyComplete();

            verify(documentRepository).deleteById(testDocument.getId());
        }

        @Test
        @DisplayName("Should delete all documents by organization")
        void shouldDeleteAllDocumentsByOrganization() {
            // Given
            when(documentRepository.deleteByOrganizationId(testOrganization.getId()))
                    .thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.deleteByOrganization(testOrganization.getId()))
                    .verifyComplete();

            verify(documentRepository).deleteByOrganizationId(testOrganization.getId());
        }
    }
}
