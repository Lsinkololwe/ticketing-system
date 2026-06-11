package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.repository.AuditLogRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for organization approval workflow with role synchronization
 *
 * Tests the complete flow:
 * 1. Organization is approved
 * 2. Owner is granted ORGANIZER role in MongoDB
 * 3. Role is synced to Keycloak
 * 4. Audit log is created
 * 5. Event is published
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Organization Approval Integration Tests")
class OrganizationApprovalIntegrationTest {

    @Autowired
    private OrganizationOnboardingService organizationOnboardingService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockBean
    private KeycloakService keycloakService;

    private User testUser;
    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        // Clean up test data
        organizationRepository.deleteAll().block();
        userRepository.deleteAll().block();
        auditLogRepository.deleteAll().block();

        // Create test user
        testUser = User.builder()
                .id("test-user-id")
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(EnumSet.of(UserType.CUSTOMER))
                .build();
        testUser = userRepository.save(testUser).block();

        // Create test organization in PENDING_REVIEW status
        testOrganization = Organization.builder()
                .id("test-org-id")
                .name("Test Organization")
                .slug("test-organization")
                .ownerId(testUser.getId())
                .status(OrganizationStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testOrganization = organizationRepository.save(testOrganization).block();

        // Mock Keycloak responses
        when(keycloakService.addRoleToUser(any(), any())).thenReturn(Mono.empty());
        when(keycloakService.syncUserRoles(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should approve organization and grant ORGANIZER role successfully")
    void shouldApproveOrganizationAndGrantRole() {
        // Given
        String adminId = "admin-123";

        // When
        StepVerifier.create(organizationOnboardingService.approve(testOrganization.getId(), adminId))
                .assertNext(approvedOrg -> {
                    // Verify organization status
                    assertThat(approvedOrg.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                    assertThat(approvedOrg.isVerified()).isTrue();
                    assertThat(approvedOrg.getVerifiedBy()).isEqualTo(adminId);
                    assertThat(approvedOrg.getApprovedAt()).isNotNull();
                })
                .verifyComplete();

        // Then - Verify user has ORGANIZER role in MongoDB
        User updatedUser = userRepository.findById(testUser.getId()).block(Duration.ofSeconds(5));
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getRoles()).contains(UserType.ORGANIZER);
        assertThat(updatedUser.getRoles()).contains(UserType.CUSTOMER); // Should still have CUSTOMER

        // Verify Keycloak was called to add the role
        verify(keycloakService, timeout(5000)).addRoleToUser(testUser.getId(), "ORGANIZER");

        // Verify audit log was created
        AuditLog auditLog = auditLogRepository.findByUserIdOrderByTimestampDesc(testUser.getId())
                .blockFirst(Duration.ofSeconds(5));
        assertThat(auditLog).isNotNull();
        assertThat(auditLog.getAction()).isEqualTo(AuditLog.AuditAction.ROLE_GRANT);
        assertThat(auditLog.getStatus()).isEqualTo(AuditLog.AuditStatus.SUCCESS);
        assertThat(auditLog.getUserId()).isEqualTo(testUser.getId());
        assertThat(auditLog.getPerformedBy()).isEqualTo(adminId);
        assertThat(auditLog.getResourceId()).isEqualTo(testOrganization.getId());
    }

    @Test
    @DisplayName("Should handle Keycloak failure gracefully during approval")
    void shouldHandleKeycloakFailureGracefully() {
        // Given
        String adminId = "admin-123";

        // Mock Keycloak failure
        when(keycloakService.addRoleToUser(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Keycloak connection timeout")));

        // When
        StepVerifier.create(organizationOnboardingService.approve(testOrganization.getId(), adminId))
                .assertNext(approvedOrg -> {
                    // Organization should still be approved
                    assertThat(approvedOrg.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                })
                .verifyComplete();

        // Then - Verify user still has ORGANIZER role in MongoDB (partial success)
        User updatedUser = userRepository.findById(testUser.getId()).block(Duration.ofSeconds(5));
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getRoles()).contains(UserType.ORGANIZER);

        // Verify failure audit log was created
        AuditLog failureLog = auditLogRepository.findByStatusOrderByTimestampDesc(AuditLog.AuditStatus.FAILURE)
                .blockFirst(Duration.ofSeconds(5));
        assertThat(failureLog).isNotNull();
        assertThat(failureLog.getAction()).isEqualTo(AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE);
        assertThat(failureLog.getErrorCode()).isNotNull();
    }

    @Test
    @DisplayName("Should be idempotent when approving multiple times")
    void shouldBeIdempotentWhenApprovingMultipleTimes() {
        // Given
        String adminId = "admin-123";

        // First approval
        organizationOnboardingService.approve(testOrganization.getId(), adminId)
                .block(Duration.ofSeconds(5));

        // Reload organization (it's now APPROVED)
        Organization approvedOrg = organizationRepository.findById(testOrganization.getId())
                .block(Duration.ofSeconds(5));
        assertThat(approvedOrg).isNotNull();
        assertThat(approvedOrg.getStatus()).isEqualTo(OrganizationStatus.APPROVED);

        // When - Try to approve again
        StepVerifier.create(organizationOnboardingService.approve(approvedOrg.getId(), adminId))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("Only organizations in PENDING_REVIEW status")
                )
                .verify();

        // Then - User should still have ORGANIZER role (no duplicates)
        User user = userRepository.findById(testUser.getId()).block(Duration.ofSeconds(5));
        assertThat(user).isNotNull();
        assertThat(user.getRoles()).contains(UserType.ORGANIZER);

        // Keycloak should only be called once
        verify(keycloakService, times(1)).addRoleToUser(any(), any());
    }

    @Test
    @DisplayName("Should reject approval when organization not in PENDING_REVIEW")
    void shouldRejectApprovalWhenNotInPendingReview() {
        // Given
        testOrganization.setStatus(OrganizationStatus.DRAFT);
        organizationRepository.save(testOrganization).block();
        String adminId = "admin-123";

        // When & Then
        StepVerifier.create(organizationOnboardingService.approve(testOrganization.getId(), adminId))
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException &&
                        error.getMessage().contains("Only organizations in PENDING_REVIEW")
                )
                .verify();

        // Verify no role was granted
        User user = userRepository.findById(testUser.getId()).block(Duration.ofSeconds(5));
        assertThat(user.getRoles()).doesNotContain(UserType.ORGANIZER);

        // Verify Keycloak was never called
        verify(keycloakService, never()).addRoleToUser(any(), any());
    }

    @Test
    @DisplayName("Should handle non-existent organization")
    void shouldHandleNonExistentOrganization() {
        // Given
        String nonExistentOrgId = "non-existent-org";
        String adminId = "admin-123";

        // When & Then
        StepVerifier.create(organizationOnboardingService.approve(nonExistentOrgId, adminId))
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                        error.getMessage().contains("Organization not found")
                )
                .verify();

        // Verify no Keycloak calls
        verify(keycloakService, never()).addRoleToUser(any(), any());
    }
}
