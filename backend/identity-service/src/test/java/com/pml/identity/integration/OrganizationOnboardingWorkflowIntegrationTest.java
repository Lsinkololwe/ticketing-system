package com.pml.identity.integration;

import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.repository.AuditLogRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-End Integration Test for Organization Onboarding Workflow
 *
 * This test demonstrates the complete approval-based onboarding flow:
 * 1. User applies to become organizer
 * 2. User fills in organization details
 * 3. User submits for admin review
 * 4. Admin approves the application
 * 5. System grants ORGANIZER role
 * 6. Audit trail is created
 *
 * Technology Stack:
 * - @SpringBootTest: Full application context
 * - TestContainers: Real MongoDB instance
 * - MockBean: Keycloak service (external dependency)
 * - Reactive Testing: StepVerifier for Mono/Flux
 *
 * Focus Areas:
 * - Data persistence and retrieval
 * - Status transitions
 * - Business rule enforcement
 * - Audit logging
 * - Error handling
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Organization Onboarding Workflow Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationOnboardingWorkflowIntegrationTest {

    // TestContainers MongoDB - Provides real database for integration testing
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(
            DockerImageName.parse("mongo:8.0")
    ).withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private OrganizationOnboardingService onboardingService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockBean
    private KeycloakService keycloakService;

    private User testUser;
    private String adminId;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        organizationRepository.deleteAll().block();
        userRepository.deleteAll().block();
        organizationMemberRepository.deleteAll().block();
        auditLogRepository.deleteAll().block();

        // Create test user
        testUser = User.builder()
                .id("integration-test-user-" + System.currentTimeMillis())
                .username("testuser")
                .email("testuser@example.com")
                .firstName("Integration")
                .lastName("Test")
                .phoneNumber("+260971234567")
                .roles(EnumSet.of(UserType.CUSTOMER))
                .createdAt(Instant.now())
                .build();
        testUser = userRepository.save(testUser).block();

        adminId = "admin-" + System.currentTimeMillis();

        // Mock Keycloak responses
        when(keycloakService.addRoleToUser(any(), any())).thenReturn(Mono.empty());
        when(keycloakService.syncUserRoles(any(), any())).thenReturn(Mono.empty());
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        organizationRepository.deleteAll().block();
        userRepository.deleteAll().block();
        organizationMemberRepository.deleteAll().block();
        auditLogRepository.deleteAll().block();
    }

    // =========================================================================
    // COMPLETE WORKFLOW TEST (HAPPY PATH)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Should complete full onboarding workflow: apply → update → submit → approve")
    void shouldCompleteFullOnboardingWorkflow() {
        // =====================================================================
        // STEP 1: User applies to become organizer
        // =====================================================================
        OrganizationApplicationInput initialApplication = new OrganizationApplicationInput(
                "Test Events Company",
                "We organize amazing events in Zambia",
                "Your trusted event partner",
                null, // logoUrl
                null, // bannerUrl
                "https://testevents.co.zm",
                OrganizationType.BUSINESS,
                "contact@testevents.co.zm",
                "+260971111111",
                "Lusaka",
                "Lusaka Province",
                "Zambia",
                null // socialLinks
        );

        Organization appliedOrg = onboardingService.applyToBeOrganizer(testUser.getId(), initialApplication)
                .block(Duration.ofSeconds(5));

        assertThat(appliedOrg).isNotNull();
        assertThat(appliedOrg.getId()).isNotNull();
        assertThat(appliedOrg.getStatus()).isEqualTo(OrganizationStatus.DRAFT);
        assertThat(appliedOrg.getName()).isEqualTo("Test Events Company");
        assertThat(appliedOrg.getOwnerId()).isEqualTo(testUser.getId());
        assertThat(appliedOrg.getSlug()).matches("^[a-z0-9-]+$");

        String organizationId = appliedOrg.getId();

        // Verify organization exists in database
        Organization savedOrg = organizationRepository.findById(organizationId)
                .block(Duration.ofSeconds(5));
        assertThat(savedOrg).isNotNull();
        assertThat(savedOrg.getName()).isEqualTo("Test Events Company");

        // Verify owner membership was created
        long memberCount = organizationMemberRepository.findByOrganizationId(organizationId)
                .count()
                .block(Duration.ofSeconds(5));
        assertThat(memberCount).isEqualTo(1L);

        // =====================================================================
        // STEP 2: User updates organization details
        // =====================================================================
        OrganizationApplicationInput updateInput = new OrganizationApplicationInput(
                "Test Events Company Ltd",
                "We organize world-class events in Zambia",
                "Africa's premier event organizer",
                "https://cdn.example.com/logo.png",
                "https://cdn.example.com/banner.jpg",
                "https://testevents.co.zm",
                OrganizationType.BUSINESS,
                "info@testevents.co.zm",
                "+260971111111",
                "Lusaka",
                "Lusaka Province",
                "Zambia",
                null
        );

        Organization updatedOrg = onboardingService.updateApplication(organizationId, updateInput)
                .block(Duration.ofSeconds(5));

        assertThat(updatedOrg).isNotNull();
        assertThat(updatedOrg.getName()).isEqualTo("Test Events Company Ltd");
        assertThat(updatedOrg.getDescription()).contains("world-class");
        assertThat(updatedOrg.getTagline()).isEqualTo("Africa's premier event organizer");
        assertThat(updatedOrg.getLogoUrl()).isNotNull();
        assertThat(updatedOrg.getBannerUrl()).isNotNull();

        // =====================================================================
        // STEP 3: User submits for admin review
        // =====================================================================
        Organization submittedOrg = onboardingService.submitForReview(organizationId)
                .block(Duration.ofSeconds(5));

        assertThat(submittedOrg).isNotNull();
        assertThat(submittedOrg.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
        assertThat(submittedOrg.getSubmittedAt()).isNotNull();
        assertThat(submittedOrg.getSubmittedAt()).isBefore(Instant.now());

        // Verify status persisted in database
        Organization dbOrg = organizationRepository.findById(organizationId)
                .block(Duration.ofSeconds(5));
        assertThat(dbOrg.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);

        // =====================================================================
        // STEP 4: Admin approves the application
        // =====================================================================
        Organization approvedOrg = onboardingService.approve(organizationId, adminId)
                .block(Duration.ofSeconds(5));

        assertThat(approvedOrg).isNotNull();
        assertThat(approvedOrg.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
        assertThat(approvedOrg.isVerified()).isTrue();
        assertThat(approvedOrg.getVerifiedBy()).isEqualTo(adminId);
        assertThat(approvedOrg.getVerifiedAt()).isNotNull();
        assertThat(approvedOrg.getReviewedBy()).isEqualTo(adminId);
        assertThat(approvedOrg.getReviewedAt()).isNotNull();
        assertThat(approvedOrg.getApprovedAt()).isNotNull();
        assertThat(approvedOrg.getRejectionReason()).isNull();

        // Verify user was granted ORGANIZER role
        User updatedUser = userRepository.findById(testUser.getId())
                .block(Duration.ofSeconds(5));
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getRoles()).contains(UserType.ORGANIZER);
        assertThat(updatedUser.getRoles()).contains(UserType.CUSTOMER); // Still has CUSTOMER

        // Verify audit log was created
        AuditLog auditLog = auditLogRepository.findByUserIdOrderByTimestampDesc(testUser.getId())
                .blockFirst(Duration.ofSeconds(5));
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getAction()).isEqualTo(AuditLog.AuditAction.ROLE_GRANT);
        assertThat(auditLog.getPerformedBy()).isEqualTo(adminId);
        assertThat(auditLog.getResourceId()).isEqualTo(organizationId);
    }

    // =========================================================================
    // ERROR HANDLING TESTS
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Should prevent duplicate organization applications")
    void shouldPreventDuplicateApplications() {
        // Given - User already has an organization
        OrganizationApplicationInput input = new OrganizationApplicationInput(
                "First Organization",
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        Organization firstOrg = onboardingService.applyToBeOrganizer(testUser.getId(), input)
                .block(Duration.ofSeconds(5));
        assertThat(firstOrg).isNotNull();

        // When - User tries to apply again
        OrganizationApplicationInput secondInput = new OrganizationApplicationInput(
                "Second Organization",
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        // Then - Should fail
        StepVerifier.create(onboardingService.applyToBeOrganizer(testUser.getId(), secondInput))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("already have an organization")
                )
                .verify();

        // Verify only one organization exists
        long orgCount = organizationRepository.findAll().count().block(Duration.ofSeconds(5));
        assertThat(orgCount).isEqualTo(1L);
    }

    @Test
    @Order(3)
    @DisplayName("Should prevent submission without required fields")
    void shouldPreventSubmissionWithoutRequiredFields() {
        // Given - Organization with missing business email
        OrganizationApplicationInput input = new OrganizationApplicationInput(
                "Incomplete Organization",
                null, null, null, null, null, null,
                null, // businessEmail missing
                null,
                null, null, null, null
        );

        Organization org = onboardingService.applyToBeOrganizer(testUser.getId(), input)
                .block(Duration.ofSeconds(5));
        assertThat(org).isNotNull();

        // When - Try to submit without email
        // Then - Should fail
        StepVerifier.create(onboardingService.submitForReview(org.getId()))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("Business email is required")
                )
                .verify();

        // Verify status remains DRAFT
        Organization dbOrg = organizationRepository.findById(org.getId())
                .block(Duration.ofSeconds(5));
        assertThat(dbOrg.getStatus()).isEqualTo(OrganizationStatus.DRAFT);
    }

    @Test
    @Order(4)
    @DisplayName("Should prevent approval of non-pending organizations")
    void shouldPreventApprovalOfNonPendingOrganizations() {
        // Given - Organization in DRAFT status
        OrganizationApplicationInput input = new OrganizationApplicationInput(
                "Test Organization",
                null, null, null, null, null, null,
                "test@example.com",
                null, null, null, null, null
        );

        Organization org = onboardingService.applyToBeOrganizer(testUser.getId(), input)
                .block(Duration.ofSeconds(5));
        assertThat(org).isNotNull();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.DRAFT);

        // When - Admin tries to approve DRAFT organization
        // Then - Should fail
        StepVerifier.create(onboardingService.approve(org.getId(), adminId))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("Only organizations in PENDING_REVIEW")
                )
                .verify();

        // Verify status remains DRAFT
        Organization dbOrg = organizationRepository.findById(org.getId())
                .block(Duration.ofSeconds(5));
        assertThat(dbOrg.getStatus()).isEqualTo(OrganizationStatus.DRAFT);
    }

    @Test
    @Order(5)
    @DisplayName("Should handle changes requested workflow")
    void shouldHandleChangesRequestedWorkflow() {
        // Given - Organization submitted for review
        OrganizationApplicationInput input = new OrganizationApplicationInput(
                "Initial Name",
                "Initial description",
                null, null, null, null, null,
                "test@example.com",
                null,
                "Lusaka", "Lusaka Province", "Zambia",
                null
        );

        Organization org = onboardingService.applyToBeOrganizer(testUser.getId(), input)
                .flatMap(createdOrg -> onboardingService.submitForReview(createdOrg.getId()))
                .block(Duration.ofSeconds(5));

        assertThat(org).isNotNull();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);

        // When - Admin requests changes
        String changeReason = "Please provide more details about your business operations";
        Organization changesRequested = onboardingService.requestChanges(org.getId(), changeReason, adminId)
                .block(Duration.ofSeconds(5));

        assertThat(changesRequested).isNotNull();
        assertThat(changesRequested.getStatus()).isEqualTo(OrganizationStatus.CHANGES_REQUESTED);
        assertThat(changesRequested.getRejectionReason()).isEqualTo(changeReason);
        assertThat(changesRequested.getReviewedBy()).isEqualTo(adminId);

        // Then - User updates and resubmits
        OrganizationApplicationInput updateInput = new OrganizationApplicationInput(
                "Updated Name",
                "Detailed description with more information about operations",
                null, null, null, null, null,
                "test@example.com",
                null, null, null, null, null
        );

        Organization updated = onboardingService.updateApplication(org.getId(), updateInput)
                .block(Duration.ofSeconds(5));

        assertThat(updated.getStatus()).isEqualTo(OrganizationStatus.CHANGES_REQUESTED);
        assertThat(updated.getDescription()).contains("Detailed description");

        // Resubmit
        Organization resubmitted = onboardingService.submitForReview(org.getId())
                .block(Duration.ofSeconds(5));

        assertThat(resubmitted.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
        assertThat(resubmitted.getRejectionReason()).isNull(); // Rejection reason cleared
        assertThat(resubmitted.getSubmittedAt()).isNotNull();

        // Finally approve
        Organization approved = onboardingService.approve(org.getId(), adminId)
                .block(Duration.ofSeconds(5));

        assertThat(approved.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
    }

    @Test
    @Order(6)
    @DisplayName("Should handle rejection workflow")
    void shouldHandleRejectionWorkflow() {
        // Given - Organization submitted for review
        OrganizationApplicationInput input = new OrganizationApplicationInput(
                "Suspicious Organization",
                "Vague description",
                null, null, null, null, null,
                "test@example.com",
                null,
                "Lusaka", "Lusaka Province", "Zambia",
                null
        );

        Organization org = onboardingService.applyToBeOrganizer(testUser.getId(), input)
                .flatMap(createdOrg -> onboardingService.submitForReview(createdOrg.getId()))
                .block(Duration.ofSeconds(5));

        assertThat(org).isNotNull();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);

        // When - Admin rejects
        String rejectionReason = "Business information could not be verified. Contact support for assistance.";
        Organization rejected = onboardingService.reject(org.getId(), rejectionReason, adminId)
                .block(Duration.ofSeconds(5));

        // Then
        assertThat(rejected).isNotNull();
        assertThat(rejected.getStatus()).isEqualTo(OrganizationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo(rejectionReason);
        assertThat(rejected.getReviewedBy()).isEqualTo(adminId);
        assertThat(rejected.getReviewedAt()).isNotNull();

        // Verify user does NOT have ORGANIZER role
        User user = userRepository.findById(testUser.getId())
                .block(Duration.ofSeconds(5));
        assertThat(user.getRoles()).doesNotContain(UserType.ORGANIZER);

        // Verify cannot resubmit rejected organization
        StepVerifier.create(onboardingService.submitForReview(org.getId()))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("cannot be submitted for review")
                )
                .verify();
    }

    @Test
    @Order(7)
    @DisplayName("Should maintain data integrity across transactions")
    void shouldMaintainDataIntegrityAcrossTransactions() {
        // This test verifies that partial failures don't leave orphaned data

        // Given
        OrganizationApplicationInput input = new OrganizationApplicationInput(
                "Test Organization",
                "Description",
                null, null, null, null, null,
                "test@example.com",
                "+260971234567",
                "Lusaka", "Lusaka Province", "Zambia",
                null
        );

        // When - Create organization
        Organization org = onboardingService.applyToBeOrganizer(testUser.getId(), input)
                .block(Duration.ofSeconds(5));

        assertThat(org).isNotNull();

        // Then - Verify referential integrity
        String orgId = org.getId();
        String userId = org.getOwnerId();

        // Organization exists
        assertThat(organizationRepository.findById(orgId).block()).isNotNull();

        // User exists
        assertThat(userRepository.findById(userId).block()).isNotNull();

        // Owner membership exists
        long memberCount = organizationMemberRepository.findByOrganizationId(orgId)
                .count()
                .block(Duration.ofSeconds(5));
        assertThat(memberCount).isEqualTo(1L);

        // Membership references valid organization
        organizationMemberRepository.findByOrganizationId(orgId)
                .take(1)
                .as(StepVerifier::create)
                .assertNext(member -> {
                    assertThat(member.getOrganizationId()).isEqualTo(orgId);
                    assertThat(member.getUserId()).isEqualTo(userId);
                })
                .verifyComplete();
    }

    // =========================================================================
    // PERFORMANCE & CONCURRENCY TESTS
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("Should handle multiple sequential applications from different users")
    void shouldHandleMultipleApplications() {
        // Create 5 different users and organizations
        int userCount = 5;

        for (int i = 0; i < userCount; i++) {
            User user = User.builder()
                    .id("user-" + i + "-" + System.currentTimeMillis())
                    .username("user" + i)
                    .email("user" + i + "@example.com")
                    .firstName("User")
                    .lastName("" + i)
                    .roles(EnumSet.of(UserType.CUSTOMER))
                    .createdAt(Instant.now())
                    .build();

            user = userRepository.save(user).block(Duration.ofSeconds(5));

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Organization " + i,
                    "Description for org " + i,
                    null, null, null, null, null,
                    "org" + i + "@example.com",
                    "+26097111111" + i,
                    "Lusaka", "Lusaka Province", "Zambia",
                    null
            );

            Organization org = onboardingService.applyToBeOrganizer(user.getId(), input)
                    .block(Duration.ofSeconds(5));

            assertThat(org).isNotNull();
            assertThat(org.getName()).isEqualTo("Organization " + i);
        }

        // Verify all organizations created
        long orgCount = organizationRepository.findAll().count().block(Duration.ofSeconds(5));
        assertThat(orgCount).isEqualTo(userCount);

        // Verify all have unique slugs
        organizationRepository.findAll()
                .map(Organization::getSlug)
                .distinct()
                .count()
                .as(StepVerifier::create)
                .expectNext((long) userCount)
                .verifyComplete();
    }
}
