package com.pml.identity.web.graphql.query;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.VerificationDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static com.pml.identity.testsupport.SecurityContextTestUtils.withUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for VerificationDocumentQueryResolver.
 *
 * <p>The resolver extracts the authenticated user from the reactive
 * {@code SecurityContext}; tests inject identity via
 * {@link com.pml.identity.testsupport.SecurityContextTestUtils#withUser}. The
 * "my*" queries use {@code getCurrentUserId()} (not {@code requireCurrentUserId()}),
 * so an unauthenticated call (no context) yields an empty result / zero count
 * rather than an error.</p>
 *
 * Tests cover:
 * - Single document queries
 * - User document queries (with organization lookup)
 * - Admin document queries (all organizations)
 * - Filtering by status and type
 * - Security (data isolation, admin vs organizer access)
 * - Edge cases (unauthenticated, empty results, missing organization)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationDocumentQueryResolver Unit Tests")
class VerificationDocumentQueryResolverTest {

    @Mock
    private VerificationDocumentService documentService;

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private VerificationDocumentQueryResolver resolver;

    private static final String USER_ID = "user-123";
    private static final String ORG_ID = "org-456";
    private static final String DOC_ID = "doc-789";

    private Organization testOrganization;
    private VerificationDocument testDocument;
    private VerificationDocument approvedDocument;
    private VerificationDocument rejectedDocument;

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
                .fileSize(1024L * 500)
                .mimeType("application/pdf")
                .status(DocumentStatus.PENDING)
                .uploadedAt(Instant.now())
                .build();

        approvedDocument = VerificationDocument.builder()
                .id("doc-approved")
                .organizationId(ORG_ID)
                .documentType("TAX_CLEARANCE")
                .documentUrl("https://s3.amazonaws.com/docs/tax.pdf")
                .fileName("tax.pdf")
                .fileSize(1024L * 300)
                .mimeType("application/pdf")
                .status(DocumentStatus.APPROVED)
                .uploadedAt(Instant.now())
                .verifiedAt(Instant.now())
                .verifiedById("admin-123")
                .build();

        rejectedDocument = VerificationDocument.builder()
                .id("doc-rejected")
                .organizationId(ORG_ID)
                .documentType("BUSINESS_LICENSE")
                .documentUrl("https://s3.amazonaws.com/docs/license.pdf")
                .fileName("license.pdf")
                .fileSize(1024L * 400)
                .mimeType("application/pdf")
                .status(DocumentStatus.REJECTED)
                .rejectionReason("Document is expired")
                .uploadedAt(Instant.now())
                .verifiedAt(Instant.now())
                .verifiedById("admin-123")
                .build();
    }

    // ========================================================================
    // SINGLE DOCUMENT QUERIES
    // ========================================================================

    @Nested
    @DisplayName("verificationDocument - Get by ID")
    class VerificationDocumentByIdTests {

        @Test
        @DisplayName("Should return document when ID exists")
        void shouldReturnDocument_WhenIdExists() {
            // Given
            when(documentService.findById(DOC_ID))
                    .thenReturn(Mono.just(testDocument));

            // When
            Mono<VerificationDocument> result = resolver.verificationDocument(DOC_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(doc -> {
                        assertThat(doc).isNotNull();
                        assertThat(doc.getId()).isEqualTo(DOC_ID);
                        assertThat(doc.getDocumentType()).isEqualTo("BUSINESS_REGISTRATION");
                    })
                    .verifyComplete();

            verify(documentService).findById(DOC_ID);
        }

        @Test
        @DisplayName("Should return empty when ID not found")
        void shouldReturnEmpty_WhenIdNotFound() {
            // Given
            when(documentService.findById("nonexistent"))
                    .thenReturn(Mono.empty());

            // When
            Mono<VerificationDocument> result = resolver.verificationDocument("nonexistent");

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }
    }

    // ========================================================================
    // ORGANIZER QUERIES
    // ========================================================================

    @Nested
    @DisplayName("myVerificationDocuments - Get User's Documents")
    class MyVerificationDocumentsTests {

        @Test
        @DisplayName("Should return all documents when no status filter")
        void shouldReturnAllDocuments_WhenNoStatusFilter() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganization(ORG_ID))
                    .thenReturn(Flux.just(testDocument, approvedDocument, rejectedDocument));

            // When
            Flux<VerificationDocument> result = resolver.myVerificationDocuments(null)
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .expectNext(testDocument)
                    .expectNext(approvedDocument)
                    .expectNext(rejectedDocument)
                    .verifyComplete();

            verify(documentService).findByOrganization(ORG_ID);
        }

        @Test
        @DisplayName("Should filter documents by status")
        void shouldFilterByStatus_WhenStatusProvided() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganizationAndStatus(ORG_ID, DocumentStatus.APPROVED))
                    .thenReturn(Flux.just(approvedDocument));

            // When
            Flux<VerificationDocument> result =
                    resolver.myVerificationDocuments(DocumentStatus.APPROVED)
                            .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .assertNext(doc ->
                            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED))
                    .verifyComplete();

            verify(documentService).findByOrganizationAndStatus(ORG_ID, DocumentStatus.APPROVED);
        }

        @Test
        @DisplayName("Should return empty when not authenticated")
        void shouldReturnEmpty_WhenNotAuthenticated() {
            // When (no security context written)
            Flux<VerificationDocument> result = resolver.myVerificationDocuments(null);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();

            verify(documentService, never()).findByOrganization(any());
        }

        @Test
        @DisplayName("Should return empty when user has no organization")
        void shouldReturnEmpty_WhenUserHasNoOrganization() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.empty());

            // When
            Flux<VerificationDocument> result = resolver.myVerificationDocuments(null)
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .verifyComplete();

            verify(documentService, never()).findByOrganization(any());
        }

        @Test
        @DisplayName("Should return pending documents only")
        void shouldReturnPendingDocuments() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganizationAndStatus(ORG_ID, DocumentStatus.PENDING))
                    .thenReturn(Flux.just(testDocument));

            // When
            Flux<VerificationDocument> result =
                    resolver.myVerificationDocuments(DocumentStatus.PENDING)
                            .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .assertNext(doc ->
                            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return rejected documents only")
        void shouldReturnRejectedDocuments() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganizationAndStatus(ORG_ID, DocumentStatus.REJECTED))
                    .thenReturn(Flux.just(rejectedDocument));

            // When
            Flux<VerificationDocument> result =
                    resolver.myVerificationDocuments(DocumentStatus.REJECTED)
                            .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .assertNext(doc -> {
                        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.REJECTED);
                        assertThat(doc.getRejectionReason()).isNotBlank();
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // ADMIN QUERIES
    // ========================================================================

    @Nested
    @DisplayName("verificationDocuments - Admin Get Documents for Organization")
    class VerificationDocumentsTests {

        @Test
        @DisplayName("Should return all documents for organization when no status filter")
        void shouldReturnAllDocuments_WhenNoStatusFilter() {
            // Given
            when(documentService.findByOrganization(ORG_ID))
                    .thenReturn(Flux.just(testDocument, approvedDocument, rejectedDocument));

            // When
            Flux<VerificationDocument> result =
                    resolver.verificationDocuments(ORG_ID, null);

            // Then
            StepVerifier.create(result)
                    .expectNext(testDocument)
                    .expectNext(approvedDocument)
                    .expectNext(rejectedDocument)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter by status when provided")
        void shouldFilterByStatus() {
            // Given
            when(documentService.findByOrganizationAndStatus(ORG_ID, DocumentStatus.APPROVED))
                    .thenReturn(Flux.just(approvedDocument));

            // When
            Flux<VerificationDocument> result =
                    resolver.verificationDocuments(ORG_ID, DocumentStatus.APPROVED);

            // Then
            StepVerifier.create(result)
                    .assertNext(doc ->
                            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("pendingVerificationDocuments - Admin Approval Queue")
    class PendingDocumentsTests {

        @Test
        @DisplayName("Should return all pending documents across all organizations")
        void shouldReturnAllPendingDocuments() {
            // Given
            VerificationDocument pendingDoc1 = testDocument;
            VerificationDocument pendingDoc2 = VerificationDocument.builder()
                    .id("doc-pending-2")
                    .organizationId("org-999")
                    .documentType("BUSINESS_REGISTRATION")
                    .status(DocumentStatus.PENDING)
                    .uploadedAt(Instant.now())
                    .build();

            when(documentService.findPendingDocuments())
                    .thenReturn(Flux.just(pendingDoc1, pendingDoc2));

            // When
            Flux<VerificationDocument> result = resolver.pendingVerificationDocuments();

            // Then
            StepVerifier.create(result)
                    .assertNext(doc -> assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING))
                    .assertNext(doc -> assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING))
                    .verifyComplete();

            verify(documentService).findPendingDocuments();
        }

        @Test
        @DisplayName("Should return empty when no pending documents")
        void shouldReturnEmpty_WhenNoPendingDocuments() {
            // Given
            when(documentService.findPendingDocuments())
                    .thenReturn(Flux.empty());

            // When
            Flux<VerificationDocument> result = resolver.pendingVerificationDocuments();

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }
    }

    // ========================================================================
    // DOCUMENT BY TYPE QUERIES
    // ========================================================================

    @Nested
    @DisplayName("myVerificationDocumentByType - Get Document by Type")
    class MyDocumentByTypeTests {

        @Test
        @DisplayName("Should return document when type exists")
        void shouldReturnDocument_WhenTypeExists() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganizationAndType(ORG_ID, "BUSINESS_REGISTRATION"))
                    .thenReturn(Mono.just(testDocument));

            // When
            Mono<VerificationDocument> result =
                    resolver.myVerificationDocumentByType("BUSINESS_REGISTRATION")
                            .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .assertNext(doc ->
                            assertThat(doc.getDocumentType()).isEqualTo("BUSINESS_REGISTRATION"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when type not found")
        void shouldReturnEmpty_WhenTypeNotFound() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganizationAndType(ORG_ID, "NONEXISTENT_TYPE"))
                    .thenReturn(Mono.empty());

            // When
            Mono<VerificationDocument> result =
                    resolver.myVerificationDocumentByType("NONEXISTENT_TYPE")
                            .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when not authenticated")
        void shouldReturnEmpty_WhenNotAuthenticated() {
            // When (no security context written)
            Mono<VerificationDocument> result =
                    resolver.myVerificationDocumentByType("BUSINESS_REGISTRATION");

            // Then
            StepVerifier.create(result)
                    .verifyComplete();

            verify(documentService, never()).findByOrganizationAndType(any(), any());
        }

        @Test
        @DisplayName("Should return empty when user has no organization")
        void shouldReturnEmpty_WhenUserHasNoOrganization() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<VerificationDocument> result =
                    resolver.myVerificationDocumentByType("BUSINESS_REGISTRATION")
                            .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .verifyComplete();

            verify(documentService, never()).findByOrganizationAndType(any(), any());
        }
    }

    // ========================================================================
    // COUNT QUERIES
    // ========================================================================

    @Nested
    @DisplayName("myVerificationDocumentCount - Count User's Documents")
    class MyDocumentCountTests {

        @Test
        @DisplayName("Should return count when user has documents")
        void shouldReturnCount_WhenUserHasDocuments() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.countByOrganization(ORG_ID))
                    .thenReturn(Mono.just(3L));

            // When
            Mono<Long> result = resolver.myVerificationDocumentCount()
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .expectNext(3L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return 0 when not authenticated")
        void shouldReturnZero_WhenNotAuthenticated() {
            // When (no security context written)
            Mono<Long> result = resolver.myVerificationDocumentCount();

            // Then
            StepVerifier.create(result)
                    .expectNext(0L)
                    .verifyComplete();

            verify(documentService, never()).countByOrganization(any());
        }

        @Test
        @DisplayName("Should return 0 when user has no organization")
        void shouldReturnZero_WhenUserHasNoOrganization() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Long> result = resolver.myVerificationDocumentCount()
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .expectNext(0L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return 0 when user has no documents")
        void shouldReturnZero_WhenUserHasNoDocuments() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.countByOrganization(ORG_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Long> result = resolver.myVerificationDocumentCount()
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("myApprovedDocumentCount - Count Approved Documents")
    class MyApprovedDocumentCountTests {

        @Test
        @DisplayName("Should return count of approved documents")
        void shouldReturnApprovedCount() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.countApprovedByOrganization(ORG_ID))
                    .thenReturn(Mono.just(2L));

            // When
            Mono<Long> result = resolver.myApprovedDocumentCount()
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .expectNext(2L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return 0 when not authenticated")
        void shouldReturnZero_WhenNotAuthenticated() {
            // When (no security context written)
            Mono<Long> result = resolver.myApprovedDocumentCount();

            // Then
            StepVerifier.create(result)
                    .expectNext(0L)
                    .verifyComplete();

            verify(documentService, never()).countApprovedByOrganization(any());
        }

        @Test
        @DisplayName("Should return 0 when user has no approved documents")
        void shouldReturnZero_WhenNoApprovedDocuments() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.countApprovedByOrganization(ORG_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Long> result = resolver.myApprovedDocumentCount()
                    .contextWrite(withUser(USER_ID));

            // Then
            StepVerifier.create(result)
                    .expectNext(0L)
                    .verifyComplete();
        }
    }

    // ========================================================================
    // DATA ISOLATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Data Isolation - Security Tests")
    class DataIsolationTests {

        @Test
        @DisplayName("User should only see their own organization's documents")
        void shouldOnlyReturnOwnDocuments() {
            // Given - User has their own organization
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(documentService.findByOrganization(ORG_ID))
                    .thenReturn(Flux.just(testDocument, approvedDocument));

            // When
            Flux<VerificationDocument> result = resolver.myVerificationDocuments(null)
                    .contextWrite(withUser(USER_ID));

            // Then - Should only get documents from their organization
            StepVerifier.create(result)
                    .assertNext(doc -> assertThat(doc.getOrganizationId()).isEqualTo(ORG_ID))
                    .assertNext(doc -> assertThat(doc.getOrganizationId()).isEqualTo(ORG_ID))
                    .verifyComplete();

            // Should only query their organization's documents
            verify(documentService).findByOrganization(ORG_ID);
            verify(documentService, never()).findPendingDocuments();
        }

        @Test
        @DisplayName("Different users should not see each other's documents")
        void shouldNotSeeDifferentUsersDocuments() {
            // Given - Different user
            String otherUserId = "other-user-999";
            String otherOrgId = "other-org-999";
            Organization otherOrg = Organization.builder()
                    .id(otherOrgId)
                    .ownerId(otherUserId)
                    .build();

            when(organizationService.findByOwnerId(otherUserId))
                    .thenReturn(Mono.just(otherOrg));
            when(documentService.findByOrganization(otherOrgId))
                    .thenReturn(Flux.empty());

            // When
            Flux<VerificationDocument> result = resolver.myVerificationDocuments(null)
                    .contextWrite(withUser(otherUserId));

            // Then - Should not get documents from ORG_ID
            StepVerifier.create(result)
                    .verifyComplete();

            // Should query the other user's organization, not the test organization
            verify(documentService).findByOrganization(otherOrgId);
            verify(documentService, never()).findByOrganization(ORG_ID);
        }
    }
}
