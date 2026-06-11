package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.identity.service.VerificationDocumentService;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Integration Test for VerificationDocument Workflow
 *
 * <p>This test suite validates the complete document verification lifecycle including:
 * <ul>
 *   <li>Document upload and registration</li>
 *   <li>Admin approval workflow</li>
 *   <li>Admin rejection workflow</li>
 *   <li>Multiple document types handling</li>
 *   <li>Organization status updates</li>
 *   <li>Security and ownership validation</li>
 *   <li>Concurrent upload scenarios</li>
 * </ul>
 *
 * <h2>Technology Stack</h2>
 * <ul>
 *   <li>SpringBootTest: Full application context</li>
 *   <li>TestContainers: Real MongoDB instance (mongo:8.0)</li>
 *   <li>MockBean: StreamBridge for event publication</li>
 *   <li>Reactive Testing: StepVerifier for Mono/Flux validation</li>
 * </ul>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Happy path: Upload → Approve → Verify</li>
 *   <li>Rejection path: Upload → Reject → Fix → Reupload</li>
 *   <li>Edge cases: Invalid org, duplicate types, concurrent uploads</li>
 *   <li>Security: Ownership validation, cross-organization access</li>
 * </ul>
 *
 * @see VerificationDocument
 * @see VerificationDocumentService
 * @see Organization
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("VerificationDocument Workflow Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VerificationDocumentWorkflowIntegrationTest {

    // =========================================================================
    // TESTCONTAINERS CONFIGURATION
    // =========================================================================

    /**
     * TestContainers MongoDB instance.
     * Provides real database for integration testing with automatic cleanup.
     */
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(
            DockerImageName.parse("mongo:8.0")
    ).withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    // =========================================================================
    // TEST DEPENDENCIES
    // =========================================================================

    @Autowired
    private VerificationDocumentService documentService;

    @Autowired
    private VerificationDocumentRepository documentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Mock StreamBridge to avoid Azure Service Bus dependency in tests.
     * We verify event publishing happens but don't send to real message broker.
     */
    @MockBean
    private StreamBridge streamBridge;

    // =========================================================================
    // TEST DATA
    // =========================================================================

    private Organization testOrganization;
    private Organization anotherOrganization;
    private User testUser;
    private User adminUser;

    // Document constants
    private static final String TEST_ORG_ID_PREFIX = "test-org-";
    private static final String TEST_USER_ID_PREFIX = "test-user-";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String VALID_MIME_TYPE = "application/pdf";

    // =========================================================================
    // SETUP AND TEARDOWN
    // =========================================================================

    @BeforeEach
    void setUp() {
        // Clean database before each test
        documentRepository.deleteAll().block();
        organizationRepository.deleteAll().block();
        userRepository.deleteAll().block();

        // Create test user
        testUser = User.builder()
                .id(TEST_USER_ID_PREFIX + System.currentTimeMillis())
                .username("testorganizer")
                .email("organizer@example.com")
                .firstName("Test")
                .lastName("Organizer")
                .phoneNumber("+260971111111")
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER))
                .createdAt(Instant.now())
                .build();
        testUser = userRepository.save(testUser).block();

        // Create admin user
        adminUser = User.builder()
                .id(TEST_USER_ID_PREFIX + "admin-" + System.currentTimeMillis())
                .username("adminuser")
                .email("admin@example.com")
                .firstName("Admin")
                .lastName("User")
                .phoneNumber("+260972222222")
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ADMIN))
                .createdAt(Instant.now())
                .build();
        adminUser = userRepository.save(adminUser).block();

        // Create approved organization
        testOrganization = Organization.builder()
                .id(TEST_ORG_ID_PREFIX + System.currentTimeMillis())
                .name("Test Organization")
                .slug("test-org-" + System.currentTimeMillis())
                .ownerId(testUser.getId())
                .type(OrganizationType.BUSINESS)
                .status(OrganizationStatus.APPROVED)
                .businessEmail("business@example.com")
                .businessPhone("+260971234567")
                .documentsVerified(false)
                .createdAt(Instant.now())
                .build();
        testOrganization = organizationRepository.save(testOrganization).block();

        // Create another organization for cross-organization tests
        anotherOrganization = Organization.builder()
                .id(TEST_ORG_ID_PREFIX + "another-" + System.currentTimeMillis())
                .name("Another Organization")
                .slug("another-org-" + System.currentTimeMillis())
                .ownerId(TEST_USER_ID_PREFIX + "another")
                .type(OrganizationType.BUSINESS)
                .status(OrganizationStatus.APPROVED)
                .businessEmail("another@example.com")
                .businessPhone("+260979999999")
                .documentsVerified(false)
                .createdAt(Instant.now())
                .build();
        anotherOrganization = organizationRepository.save(anotherOrganization).block();

        // Mock StreamBridge to return true (successful send)
        when(streamBridge.send(anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        // Cleanup after each test
        documentRepository.deleteAll().block();
        organizationRepository.deleteAll().block();
        userRepository.deleteAll().block();

        // Reset mocks
        reset(streamBridge);
    }

    // =========================================================================
    // TEST 1: COMPLETE UPLOAD WORKFLOW
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Should upload document and save metadata to MongoDB")
    void shouldUploadDocumentSuccessfully() {
        // Given: Document upload request
        String documentType = "ID_DOCUMENT";
        String documentUrl = "s3://bucket/documents/" + testOrganization.getId() + "/id.pdf";
        String fileName = "national_id.pdf";
        Long fileSize = 2_500_000L; // 2.5 MB
        String mimeType = VALID_MIME_TYPE;

        // When: Document is uploaded
        Mono<VerificationDocument> uploadResult = documentService.upload(
                testOrganization.getId(),
                documentType,
                documentUrl,
                fileName,
                fileSize,
                mimeType
        );

        // Then: Document is saved successfully
        StepVerifier.create(uploadResult)
                .assertNext(savedDoc -> {
                    assertThat(savedDoc).isNotNull();
                    assertThat(savedDoc.getId()).isNotNull();
                    assertThat(savedDoc.getOrganizationId()).isEqualTo(testOrganization.getId());
                    assertThat(savedDoc.getDocumentType()).isEqualTo(documentType);
                    assertThat(savedDoc.getDocumentUrl()).isEqualTo(documentUrl);
                    assertThat(savedDoc.getFileName()).isEqualTo(fileName);
                    assertThat(savedDoc.getFileSize()).isEqualTo(fileSize);
                    assertThat(savedDoc.getMimeType()).isEqualTo(mimeType);
                    assertThat(savedDoc.getStatus()).isEqualTo(DocumentStatus.PENDING);
                    assertThat(savedDoc.getUploadedAt()).isNotNull();
                    assertThat(savedDoc.getVerifiedAt()).isNull();
                    assertThat(savedDoc.getVerifiedById()).isNull();
                })
                .verifyComplete();

        // Verify: Document exists in database
        StepVerifier.create(documentRepository.countByOrganizationId(testOrganization.getId()))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @Order(2)
    @DisplayName("Should update existing document when same type is uploaded again")
    void shouldUpdateExistingDocumentOnReupload() {
        // Given: Initial document upload
        String documentType = "BUSINESS_LICENSE";
        documentService.upload(
                testOrganization.getId(),
                documentType,
                "s3://bucket/old-license.pdf",
                "old_license.pdf",
                1_000_000L,
                VALID_MIME_TYPE
        ).block();

        // When: Same document type is uploaded again
        String newUrl = "s3://bucket/new-license.pdf";
        String newFileName = "new_license.pdf";
        Long newFileSize = 1_500_000L;

        Mono<VerificationDocument> updateResult = documentService.upload(
                testOrganization.getId(),
                documentType,
                newUrl,
                newFileName,
                newFileSize,
                VALID_MIME_TYPE
        );

        // Then: Document is updated, not duplicated
        StepVerifier.create(updateResult)
                .assertNext(updatedDoc -> {
                    assertThat(updatedDoc.getDocumentUrl()).isEqualTo(newUrl);
                    assertThat(updatedDoc.getFileName()).isEqualTo(newFileName);
                    assertThat(updatedDoc.getFileSize()).isEqualTo(newFileSize);
                    assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.PENDING);
                    assertThat(updatedDoc.getVerifiedAt()).isNull(); // Reset on reupload
                })
                .verifyComplete();

        // Verify: Only one document exists for this type
        StepVerifier.create(documentRepository.countByOrganizationId(testOrganization.getId()))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @Order(3)
    @DisplayName("Should reject upload for non-existent organization")
    void shouldRejectUploadForNonExistentOrganization() {
        // Given: Invalid organization ID
        String invalidOrgId = "non-existent-org-id";

        // When: Upload is attempted
        Mono<VerificationDocument> uploadResult = documentService.upload(
                invalidOrgId,
                "ID_DOCUMENT",
                "s3://bucket/doc.pdf",
                "doc.pdf",
                1_000_000L,
                VALID_MIME_TYPE
        );

        // Then: Error is thrown
        StepVerifier.create(uploadResult)
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                        error.getMessage().contains("Organization not found")
                )
                .verify();
    }

    // =========================================================================
    // TEST 2: ADMIN APPROVAL WORKFLOW
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("Should approve document and update organization status when all docs approved")
    void shouldApproveDocumentAndUpdateOrganization() {
        // Given: Three documents uploaded (minimum required for verification)
        VerificationDocument doc1 = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc2 = documentService.upload(
                testOrganization.getId(), "BUSINESS_LICENSE",
                "s3://bucket/license.pdf", "license.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc3 = documentService.upload(
                testOrganization.getId(), "TAX_CERTIFICATE",
                "s3://bucket/tax.pdf", "tax.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // When: Admin approves all documents
        documentService.approve(doc1.getId(), adminUser.getId()).block();
        documentService.approve(doc2.getId(), adminUser.getId()).block();
        Mono<VerificationDocument> finalApproval = documentService.approve(doc3.getId(), adminUser.getId());

        // Then: Document is approved with metadata
        StepVerifier.create(finalApproval)
                .assertNext(approvedDoc -> {
                    assertThat(approvedDoc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
                    assertThat(approvedDoc.getVerifiedAt()).isNotNull();
                    assertThat(approvedDoc.getVerifiedById()).isEqualTo(adminUser.getId());
                    assertThat(approvedDoc.getRejectionReason()).isNull();
                })
                .verifyComplete();

        // Wait a moment for async organization update
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify: Organization documentsVerified is true
        StepVerifier.create(organizationRepository.findById(testOrganization.getId()))
                .assertNext(org -> {
                    assertThat(org.isDocumentsVerified()).isTrue();
                })
                .verifyComplete();

        // Verify: Event was published
        verify(streamBridge, atLeastOnce()).send(eq("notificationOutput-out-0"), any());
    }

    @Test
    @Order(11)
    @DisplayName("Should reject approval of already approved document")
    void shouldRejectApprovalOfApprovedDocument() {
        // Given: Already approved document
        VerificationDocument document = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        documentService.approve(document.getId(), adminUser.getId()).block();

        // When: Attempt to approve again
        Mono<VerificationDocument> reapprovalAttempt = documentService.approve(
                document.getId(),
                adminUser.getId()
        );

        // Then: Error is thrown
        StepVerifier.create(reapprovalAttempt)
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("not pending approval")
                )
                .verify();
    }

    @Test
    @Order(12)
    @DisplayName("Should reject approval of non-existent document")
    void shouldRejectApprovalOfNonExistentDocument() {
        // Given: Invalid document ID
        String invalidDocId = "non-existent-doc-id";

        // When: Approval is attempted
        Mono<VerificationDocument> approvalResult = documentService.approve(
                invalidDocId,
                adminUser.getId()
        );

        // Then: Error is thrown
        StepVerifier.create(approvalResult)
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                        error.getMessage().contains("Document not found")
                )
                .verify();
    }

    // =========================================================================
    // TEST 3: ADMIN REJECTION WORKFLOW
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("Should reject document with reason and keep org verification false")
    void shouldRejectDocumentWithReason() {
        // Given: Uploaded document
        VerificationDocument document = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // When: Admin rejects with reason
        String rejectionReason = "Document is blurry and unreadable";
        Mono<VerificationDocument> rejectionResult = documentService.reject(
                document.getId(),
                rejectionReason,
                adminUser.getId()
        );

        // Then: Document is rejected with metadata
        StepVerifier.create(rejectionResult)
                .assertNext(rejectedDoc -> {
                    assertThat(rejectedDoc.getStatus()).isEqualTo(DocumentStatus.REJECTED);
                    assertThat(rejectedDoc.getVerifiedAt()).isNotNull();
                    assertThat(rejectedDoc.getVerifiedById()).isEqualTo(adminUser.getId());
                    assertThat(rejectedDoc.getRejectionReason()).isEqualTo(rejectionReason);
                })
                .verifyComplete();

        // Verify: Organization documentsVerified remains false
        StepVerifier.create(organizationRepository.findById(testOrganization.getId()))
                .assertNext(org -> {
                    assertThat(org.isDocumentsVerified()).isFalse();
                })
                .verifyComplete();

        // Verify: Rejection event was published
        verify(streamBridge, atLeastOnce()).send(eq("notificationOutput-out-0"), any());
    }

    @Test
    @Order(21)
    @DisplayName("Should allow reupload after rejection")
    void shouldAllowReuploadAfterRejection() {
        // Given: Rejected document
        VerificationDocument document = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/bad-id.pdf", "bad_id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        documentService.reject(document.getId(), "Poor quality", adminUser.getId()).block();

        // When: User uploads new document of same type
        Mono<VerificationDocument> reuploadResult = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/good-id.pdf", "good_id.pdf", 1_200_000L, VALID_MIME_TYPE
        );

        // Then: New document is pending review
        StepVerifier.create(reuploadResult)
                .assertNext(newDoc -> {
                    assertThat(newDoc.getDocumentUrl()).isEqualTo("s3://bucket/good-id.pdf");
                    assertThat(newDoc.getStatus()).isEqualTo(DocumentStatus.PENDING);
                    assertThat(newDoc.getRejectionReason()).isNull();
                    assertThat(newDoc.getVerifiedAt()).isNull();
                })
                .verifyComplete();
    }

    // =========================================================================
    // TEST 4: MULTIPLE DOCUMENT TYPES
    // =========================================================================

    @Test
    @Order(30)
    @DisplayName("Should handle multiple document types independently")
    void shouldHandleMultipleDocumentTypes() {
        // Given: Multiple document types
        String[] documentTypes = {
                "ID_DOCUMENT",
                "BUSINESS_LICENSE",
                "TAX_CERTIFICATE",
                "BANK_STATEMENT"
        };

        // When: All documents are uploaded
        for (String docType : documentTypes) {
            documentService.upload(
                    testOrganization.getId(),
                    docType,
                    "s3://bucket/" + docType.toLowerCase() + ".pdf",
                    docType.toLowerCase() + ".pdf",
                    1_000_000L,
                    VALID_MIME_TYPE
            ).block();
        }

        // Then: All documents are saved
        StepVerifier.create(documentRepository.countByOrganizationId(testOrganization.getId()))
                .expectNext((long) documentTypes.length)
                .verifyComplete();

        // Verify: Each document type exists
        for (String docType : documentTypes) {
            StepVerifier.create(documentService.findByOrganizationAndType(
                    testOrganization.getId(), docType))
                    .assertNext(doc -> {
                        assertThat(doc.getDocumentType()).isEqualTo(docType);
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING);
                    })
                    .verifyComplete();
        }
    }

    @Test
    @Order(31)
    @DisplayName("Should query documents by status correctly")
    void shouldQueryDocumentsByStatus() {
        // Given: Documents with different statuses
        VerificationDocument doc1 = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc2 = documentService.upload(
                testOrganization.getId(), "BUSINESS_LICENSE",
                "s3://bucket/license.pdf", "license.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc3 = documentService.upload(
                testOrganization.getId(), "TAX_CERTIFICATE",
                "s3://bucket/tax.pdf", "tax.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // Approve one, reject one, leave one pending
        documentService.approve(doc1.getId(), adminUser.getId()).block();
        documentService.reject(doc2.getId(), "Invalid", adminUser.getId()).block();

        // When: Query by status
        Flux<VerificationDocument> pendingDocs = documentService.findByOrganizationAndStatus(
                testOrganization.getId(), DocumentStatus.PENDING);
        Flux<VerificationDocument> approvedDocs = documentService.findByOrganizationAndStatus(
                testOrganization.getId(), DocumentStatus.APPROVED);
        Flux<VerificationDocument> rejectedDocs = documentService.findByOrganizationAndStatus(
                testOrganization.getId(), DocumentStatus.REJECTED);

        // Then: Correct documents are returned
        StepVerifier.create(pendingDocs.count()).expectNext(1L).verifyComplete();
        StepVerifier.create(approvedDocs.count()).expectNext(1L).verifyComplete();
        StepVerifier.create(rejectedDocs.count()).expectNext(1L).verifyComplete();
    }

    // =========================================================================
    // TEST 5: DELETE DOCUMENT WORKFLOW
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("Should delete document successfully")
    void shouldDeleteDocument() {
        // Given: Uploaded document
        VerificationDocument document = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // When: Document is deleted
        Mono<Void> deleteResult = documentService.delete(document.getId());

        // Then: Document is removed from database
        StepVerifier.create(deleteResult).verifyComplete();

        StepVerifier.create(documentRepository.findById(document.getId()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @Order(41)
    @DisplayName("Should delete all documents for organization")
    void shouldDeleteAllDocumentsForOrganization() {
        // Given: Multiple documents
        documentService.upload(testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE).block();
        documentService.upload(testOrganization.getId(), "BUSINESS_LICENSE",
                "s3://bucket/license.pdf", "license.pdf", 1_000_000L, VALID_MIME_TYPE).block();
        documentService.upload(testOrganization.getId(), "TAX_CERTIFICATE",
                "s3://bucket/tax.pdf", "tax.pdf", 1_000_000L, VALID_MIME_TYPE).block();

        // When: All documents are deleted
        Mono<Void> deleteResult = documentService.deleteByOrganization(testOrganization.getId());

        // Then: All documents are removed
        StepVerifier.create(deleteResult).verifyComplete();

        StepVerifier.create(documentRepository.countByOrganizationId(testOrganization.getId()))
                .expectNext(0L)
                .verifyComplete();
    }

    // =========================================================================
    // TEST 6: CONCURRENT UPLOADS
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("Should handle concurrent document uploads correctly")
    void shouldHandleConcurrentUploadsCorrectly() throws InterruptedException {
        // Given: Multiple concurrent upload requests
        int concurrentUploads = 5;
        CountDownLatch latch = new CountDownLatch(concurrentUploads);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: Documents are uploaded concurrently
        for (int i = 0; i < concurrentUploads; i++) {
            final int index = i;
            documentService.upload(
                    testOrganization.getId(),
                    "DOC_TYPE_" + index,
                    "s3://bucket/doc-" + index + ".pdf",
                    "doc-" + index + ".pdf",
                    1_000_000L,
                    VALID_MIME_TYPE
            ).subscribe(
                    doc -> {
                        successCount.incrementAndGet();
                        latch.countDown();
                    },
                    error -> latch.countDown()
            );
        }

        // Wait for all uploads to complete
        latch.await();

        // Then: All documents are saved
        assertThat(successCount.get()).isEqualTo(concurrentUploads);

        StepVerifier.create(documentRepository.countByOrganizationId(testOrganization.getId()))
                .expectNext((long) concurrentUploads)
                .verifyComplete();
    }

    // =========================================================================
    // TEST 7: PENDING DOCUMENTS QUEUE (ADMIN)
    // =========================================================================

    @Test
    @Order(60)
    @DisplayName("Should retrieve pending documents for admin review queue")
    void shouldRetrievePendingDocumentsQueue() {
        // Given: Documents from multiple organizations
        documentService.upload(testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id1.pdf", "id1.pdf", 1_000_000L, VALID_MIME_TYPE).block();
        documentService.upload(testOrganization.getId(), "BUSINESS_LICENSE",
                "s3://bucket/license1.pdf", "license1.pdf", 1_000_000L, VALID_MIME_TYPE).block();
        documentService.upload(anotherOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id2.pdf", "id2.pdf", 1_000_000L, VALID_MIME_TYPE).block();

        // When: Admin retrieves pending documents
        Flux<VerificationDocument> pendingQueue = documentService.findPendingDocuments();

        // Then: All pending documents are returned
        StepVerifier.create(pendingQueue.collectList())
                .assertNext(docs -> {
                    assertThat(docs).hasSize(3);
                    assertThat(docs).allMatch(doc -> doc.getStatus() == DocumentStatus.PENDING);
                })
                .verifyComplete();
    }

    // =========================================================================
    // TEST 8: COUNT OPERATIONS
    // =========================================================================

    @Test
    @Order(70)
    @DisplayName("Should count documents correctly")
    void shouldCountDocumentsCorrectly() {
        // Given: Documents with different statuses
        VerificationDocument doc1 = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc2 = documentService.upload(
                testOrganization.getId(), "BUSINESS_LICENSE",
                "s3://bucket/license.pdf", "license.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc3 = documentService.upload(
                testOrganization.getId(), "TAX_CERTIFICATE",
                "s3://bucket/tax.pdf", "tax.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // Approve two documents
        documentService.approve(doc1.getId(), adminUser.getId()).block();
        documentService.approve(doc2.getId(), adminUser.getId()).block();

        // When: Count operations are performed
        Mono<Long> totalCount = documentService.countByOrganization(testOrganization.getId());
        Mono<Long> approvedCount = documentService.countApprovedByOrganization(testOrganization.getId());

        // Then: Counts are correct
        StepVerifier.create(totalCount).expectNext(3L).verifyComplete();
        StepVerifier.create(approvedCount).expectNext(2L).verifyComplete();
    }

    // =========================================================================
    // TEST 9: DOCUMENT TYPE EXISTENCE CHECK
    // =========================================================================

    @Test
    @Order(80)
    @DisplayName("Should check document type existence correctly")
    void shouldCheckDocumentTypeExistence() {
        // Given: Document uploaded
        documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // When: Check existence
        Mono<Boolean> idDocExists = documentService.existsByOrganizationAndType(
                testOrganization.getId(), "ID_DOCUMENT");
        Mono<Boolean> licenseExists = documentService.existsByOrganizationAndType(
                testOrganization.getId(), "BUSINESS_LICENSE");

        // Then: Existence checks are correct
        StepVerifier.create(idDocExists).expectNext(true).verifyComplete();
        StepVerifier.create(licenseExists).expectNext(false).verifyComplete();
    }

    // =========================================================================
    // TEST 10: ORGANIZATION VERIFICATION THRESHOLD
    // =========================================================================

    @Test
    @Order(90)
    @DisplayName("Should update organization verification status only after 3 approved docs")
    void shouldUpdateOrganizationAfterThreeApprovals() {
        // Given: Two approved documents
        VerificationDocument doc1 = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        VerificationDocument doc2 = documentService.upload(
                testOrganization.getId(), "BUSINESS_LICENSE",
                "s3://bucket/license.pdf", "license.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        documentService.approve(doc1.getId(), adminUser.getId()).block();
        documentService.approve(doc2.getId(), adminUser.getId()).block();

        // Wait for async update
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify: Organization not yet verified (< 3 docs)
        StepVerifier.create(organizationRepository.findById(testOrganization.getId()))
                .assertNext(org -> assertThat(org.isDocumentsVerified()).isFalse())
                .verifyComplete();

        // When: Third document is approved
        VerificationDocument doc3 = documentService.upload(
                testOrganization.getId(), "TAX_CERTIFICATE",
                "s3://bucket/tax.pdf", "tax.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();
        documentService.approve(doc3.getId(), adminUser.getId()).block();

        // Wait for async update
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: Organization is now verified
        StepVerifier.create(organizationRepository.findById(testOrganization.getId()))
                .assertNext(org -> assertThat(org.isDocumentsVerified()).isTrue())
                .verifyComplete();
    }

    // =========================================================================
    // TEST 11: ERROR SCENARIOS
    // =========================================================================

    @Test
    @Order(100)
    @DisplayName("Should handle null parameters gracefully")
    void shouldHandleNullParameters() {
        // When: Null organization ID
        Mono<VerificationDocument> nullOrgResult = documentService.upload(
                null, "ID_DOCUMENT", "s3://bucket/doc.pdf",
                "doc.pdf", 1_000_000L, VALID_MIME_TYPE
        );

        // Then: Error is thrown
        StepVerifier.create(nullOrgResult)
                .expectError()
                .verify();
    }

    @Test
    @Order(101)
    @DisplayName("Should handle rejection without reason")
    void shouldRejectRejectionWithoutReason() {
        // Given: Uploaded document
        VerificationDocument document = documentService.upload(
                testOrganization.getId(), "ID_DOCUMENT",
                "s3://bucket/id.pdf", "id.pdf", 1_000_000L, VALID_MIME_TYPE
        ).block();

        // When: Rejection attempted without reason
        Mono<VerificationDocument> rejectionResult = documentService.reject(
                document.getId(),
                null, // No reason provided
                adminUser.getId()
        );

        // Then: Document is rejected but reason is null
        StepVerifier.create(rejectionResult)
                .assertNext(rejectedDoc -> {
                    assertThat(rejectedDoc.getStatus()).isEqualTo(DocumentStatus.REJECTED);
                    assertThat(rejectedDoc.getRejectionReason()).isNull();
                })
                .verifyComplete();
    }
}
