package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationSettingsInput;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for OrganizationMutationResolver.
 *
 * Tests cover:
 * - All mutation operations (apply, update, submit, approve, reject, suspend)
 * - Security validations (authentication, authorization, ownership)
 * - Edge cases (missing JWT, wrong owner, invalid status transitions)
 * - Error scenarios (service failures, validation failures)
 * - Business logic (status transitions, permission checks)
 *
 * Uses Mockito for mocking and reactor-test StepVerifier for reactive assertions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationMutationResolver Unit Tests")
class OrganizationMutationResolverTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private OrganizationOnboardingService onboardingService;

    @Mock
    private OrganizationMemberService memberService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private OrganizationMutationResolver resolver;

    private static final String USER_ID = "user-123";
    private static final String ORG_ID = "org-456";
    private static final String ADMIN_ID = "admin-789";

    private Organization testOrganization;
    private OrganizationApplicationInput testInput;

    @BeforeEach
    void setUp() {
        testOrganization = Organization.builder()
                .id(ORG_ID)
                .name("Test Organization")
                .slug("test-organization")
                .ownerId(USER_ID)
                .status(OrganizationStatus.DRAFT)
                .type(OrganizationType.INDIVIDUAL)
                .createdAt(Instant.now())
                .build();

        testInput = new OrganizationApplicationInput(
                "Test Organization",
                "Test description",
                "Test tagline",
                null, // logoUrl
                null, // bannerUrl
                "https://example.com",
                OrganizationType.INDIVIDUAL,
                "+260971234567",
                "test@example.com",
                "Lusaka",
                "Lusaka Province",
                "Zambia",
                null // socialLinks
        );
    }

    // ========================================================================
    // ONBOARDING MUTATIONS (User)
    // ========================================================================

    @Nested
    @DisplayName("applyToBeOrganizer - User Application")
    class ApplyToBeOrganizerTests {

        @Test
        @DisplayName("Should create organization when valid input provided")
        void shouldCreateOrganization_WhenValidInput() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(onboardingService.applyToBeOrganizer(USER_ID, testInput))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.applyToBeOrganizer(testInput, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org).isNotNull();
                        assertThat(org.getId()).isEqualTo(ORG_ID);
                        assertThat(org.getOwnerId()).isEqualTo(USER_ID);
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.DRAFT);
                    })
                    .verifyComplete();

            verify(onboardingService).applyToBeOrganizer(USER_ID, testInput);
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // When
            Mono<Organization> result = resolver.applyToBeOrganizer(testInput, null);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Authentication required")
                    )
                    .verify();

            verify(onboardingService, never()).applyToBeOrganizer(any(), any());
        }

        @Test
        @DisplayName("Should propagate service error when onboarding fails")
        void shouldPropagateError_WhenServiceFails() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(onboardingService.applyToBeOrganizer(USER_ID, testInput))
                    .thenReturn(Mono.error(new IllegalArgumentException("Invalid organization name")));

            // When
            Mono<Organization> result = resolver.applyToBeOrganizer(testInput, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Invalid organization name")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("updateOrganizationApplication - Update Application")
    class UpdateApplicationTests {

        @Test
        @DisplayName("Should update application when owner makes request")
        void shouldUpdateApplication_WhenOwner() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(onboardingService.updateApplication(ORG_ID, testInput))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.updateOrganizationApplication(
                    ORG_ID, testInput, jwt
            );

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org).isNotNull();
                        assertThat(org.getId()).isEqualTo(ORG_ID);
                    })
                    .verifyComplete();

            verify(onboardingService).updateApplication(ORG_ID, testInput);
        }

        @Test
        @DisplayName("Should fail when user is not the owner")
        void shouldFail_WhenNotOwner() {
            // Given
            String otherUserId = "other-user-999";
            when(jwt.getSubject()).thenReturn(otherUserId);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.updateOrganizationApplication(
                    ORG_ID, testInput, jwt
            );

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Only the owner can update the application")
                    )
                    .verify();

            verify(onboardingService, never()).updateApplication(any(), any());
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // When
            Mono<Organization> result = resolver.updateOrganizationApplication(
                    ORG_ID, testInput, null
            );

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Authentication required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when organization not found")
        void shouldFail_WhenOrganizationNotFound() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Organization> result = resolver.updateOrganizationApplication(
                    ORG_ID, testInput, jwt
            );

            // Then
            StepVerifier.create(result)
                    .verifyComplete(); // Empty mono completes without emitting

            verify(onboardingService, never()).updateApplication(any(), any());
        }
    }

    @Nested
    @DisplayName("submitOrganizationForReview - Submit for Review")
    class SubmitForReviewTests {

        @Test
        @DisplayName("Should submit for review when owner and valid status")
        void shouldSubmitForReview_WhenOwner() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));

            Organization reviewedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.PENDING_REVIEW)
                    .submittedAt(Instant.now())
                    .build();
            when(onboardingService.submitForReview(ORG_ID))
                    .thenReturn(Mono.just(reviewedOrg));

            // When
            Mono<Organization> result = resolver.submitOrganizationForReview(ORG_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
                        assertThat(org.getSubmittedAt()).isNotNull();
                    })
                    .verifyComplete();

            verify(onboardingService).submitForReview(ORG_ID);
        }

        @Test
        @DisplayName("Should fail when user is not the owner")
        void shouldFail_WhenNotOwner() {
            // Given
            String otherUserId = "other-user-999";
            when(jwt.getSubject()).thenReturn(otherUserId);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.submitOrganizationForReview(ORG_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Only the owner can submit for review")
                    )
                    .verify();

            verify(onboardingService, never()).submitForReview(any());
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // When
            Mono<Organization> result = resolver.submitOrganizationForReview(ORG_ID, null);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Authentication required")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("getOrCreateMyOrganization - Get or Create")
    class GetOrCreateOrganizationTests {

        @Test
        @DisplayName("Should return existing organization if user has one")
        void shouldReturnExisting_WhenUserHasOrganization() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(onboardingService.getOrCreateOrganization(USER_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.getOrCreateMyOrganization(jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getId()).isEqualTo(ORG_ID);
                        assertThat(org.getOwnerId()).isEqualTo(USER_ID);
                    })
                    .verifyComplete();

            verify(onboardingService).getOrCreateOrganization(USER_ID);
        }

        @Test
        @DisplayName("Should create new organization if user has none")
        void shouldCreateNew_WhenUserHasNoOrganization() {
            // Given
            Organization newOrg = Organization.builder()
                    .id("new-org-789")
                    .name("New Organization")
                    .slug("new-organization")
                    .ownerId(USER_ID)
                    .status(OrganizationStatus.DRAFT)
                    .build();

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(onboardingService.getOrCreateOrganization(USER_ID))
                    .thenReturn(Mono.just(newOrg));

            // When
            Mono<Organization> result = resolver.getOrCreateMyOrganization(jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getId()).isEqualTo("new-org-789");
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.DRAFT);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when JWT is null")
        void shouldFail_WhenJwtIsNull() {
            // When
            Mono<Organization> result = resolver.getOrCreateMyOrganization(null);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Authentication required")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("upgradeToBusinessOrganization - Upgrade to Business")
    class UpgradeToBusinessTests {

        @Test
        @DisplayName("Should upgrade to business when owner requests")
        void shouldUpgrade_WhenOwner() {
            // Given
            String businessName = "Test Business Ltd";
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));

            Organization businessOrg = testOrganization.toBuilder()
                    .type(OrganizationType.BUSINESS)
                    .name(businessName)
                    .build();
            when(onboardingService.upgradeToBusinessOrganization(ORG_ID, businessName))
                    .thenReturn(Mono.just(businessOrg));

            // When
            Mono<Organization> result = resolver.upgradeToBusinessOrganization(
                    ORG_ID, businessName, jwt
            );

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getType()).isEqualTo(OrganizationType.BUSINESS);
                        assertThat(org.getName()).isEqualTo(businessName);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when user is not the owner")
        void shouldFail_WhenNotOwner() {
            // Given
            String otherUserId = "other-user-999";
            when(jwt.getSubject()).thenReturn(otherUserId);
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.upgradeToBusinessOrganization(
                    ORG_ID, "Business Name", jwt
            );

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Only the owner can upgrade the organization")
                    )
                    .verify();
        }
    }

    // ========================================================================
    // ADMIN MUTATIONS (Approval Workflow)
    // ========================================================================

    @Nested
    @DisplayName("approveOrganization - Admin Approval")
    class ApproveOrganizationTests {

        @Test
        @DisplayName("Should approve organization when admin requests")
        void shouldApprove_WhenAdmin() {
            // Given
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            Organization approvedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.APPROVED)
                    .approvedAt(Instant.now())
                    .reviewedBy(ADMIN_ID)
                    .build();
            when(onboardingService.approve(ORG_ID, ADMIN_ID))
                    .thenReturn(Mono.just(approvedOrg));

            // When
            Mono<Organization> result = resolver.approveOrganization(ORG_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                        assertThat(org.getApprovedAt()).isNotNull();
                        assertThat(org.getReviewedBy()).isEqualTo(ADMIN_ID);
                    })
                    .verifyComplete();

            verify(onboardingService).approve(ORG_ID, ADMIN_ID);
        }

        @Test
        @DisplayName("Should handle null JWT gracefully")
        void shouldHandleNullJwt() {
            // Given
            Organization approvedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.APPROVED)
                    .build();
            when(onboardingService.approve(ORG_ID, "system"))
                    .thenReturn(Mono.just(approvedOrg));

            // When
            Mono<Organization> result = resolver.approveOrganization(ORG_ID, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                    })
                    .verifyComplete();

            verify(onboardingService).approve(ORG_ID, "system");
        }
    }

    @Nested
    @DisplayName("requestOrganizationChanges - Request Changes")
    class RequestChangesTests {

        @Test
        @DisplayName("Should request changes when admin provides reason")
        void shouldRequestChanges_WithReason() {
            // Given
            String reason = "Please provide valid business registration number";
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            Organization changesRequestedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.CHANGES_REQUESTED)
                    .rejectionReason(reason)
                    .reviewedBy(ADMIN_ID)
                    .build();
            when(onboardingService.requestChanges(ORG_ID, reason, ADMIN_ID))
                    .thenReturn(Mono.just(changesRequestedOrg));

            // When
            Mono<Organization> result = resolver.requestOrganizationChanges(
                    ORG_ID, reason, jwt
            );

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.CHANGES_REQUESTED);
                        assertThat(org.getRejectionReason()).isEqualTo(reason);
                        assertThat(org.getReviewedBy()).isEqualTo(ADMIN_ID);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("rejectOrganization - Admin Rejection")
    class RejectOrganizationTests {

        @Test
        @DisplayName("Should reject organization when admin provides reason")
        void shouldReject_WithReason() {
            // Given
            String reason = "Business not registered in Zambia";
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            Organization rejectedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.REJECTED)
                    .rejectionReason(reason)
                    .reviewedBy(ADMIN_ID)
                    .build();
            when(onboardingService.reject(ORG_ID, reason, ADMIN_ID))
                    .thenReturn(Mono.just(rejectedOrg));

            // When
            Mono<Organization> result = resolver.rejectOrganization(ORG_ID, reason, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.REJECTED);
                        assertThat(org.getRejectionReason()).isEqualTo(reason);
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // ORGANIZATION MANAGEMENT
    // ========================================================================

    @Nested
    @DisplayName("updateOrganization - Update Details")
    class UpdateOrganizationTests {

        @Test
        @DisplayName("Should update organization when user has permission")
        void shouldUpdate_WhenHasPermission() {
            // Given
            UpdateOrganizationInput input = new UpdateOrganizationInput(
                    "Updated Name",
                    "Updated description",
                    "https://example.com/logo.png",
                    "https://example.com/banner.png"
            );

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(memberService.hasPermission(USER_ID, ORG_ID, "ORG_EDIT"))
                    .thenReturn(Mono.just(true));

            Organization updatedOrg = testOrganization.toBuilder()
                    .name(input.name())
                    .description(input.description())
                    .logoUrl(input.logoUrl())
                    .bannerUrl(input.bannerUrl())
                    .build();
            when(organizationService.update(
                    eq(ORG_ID),
                    eq(input.name()),
                    eq(input.description()),
                    eq(input.logoUrl()),
                    eq(input.bannerUrl())
            )).thenReturn(Mono.just(updatedOrg));

            // When
            Mono<Organization> result = resolver.updateOrganization(ORG_ID, input, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getName()).isEqualTo(input.name());
                        assertThat(org.getDescription()).isEqualTo(input.description());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when user lacks permission")
        void shouldFail_WhenLacksPermission() {
            // Given
            UpdateOrganizationInput input = new UpdateOrganizationInput(
                    "Updated Name", "Description", null, null
            );

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(memberService.hasPermission(USER_ID, ORG_ID, "ORG_EDIT"))
                    .thenReturn(Mono.just(false));

            // When
            Mono<Organization> result = resolver.updateOrganization(ORG_ID, input, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Permission denied: ORG_EDIT")
                    )
                    .verify();

            verify(organizationService, never()).update(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("updateOrganizationSettings - Update Settings")
    class UpdateSettingsTests {

        @Test
        @DisplayName("Should update settings when user has permission")
        void shouldUpdateSettings_WhenHasPermission() {
            // Given
            UpdateOrganizationSettingsInput input = new UpdateOrganizationSettingsInput(
                    true,  // allowMemberInvites
                    false, // requireApprovalForEvents
                    true,  // notifyOnNewMember
                    "PUBLIC" // defaultEventVisibility
            );

            OrganizationSettings currentSettings = new OrganizationSettings();
            Organization orgWithSettings = testOrganization.toBuilder()
                    .settings(currentSettings)
                    .build();

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(memberService.hasPermission(USER_ID, ORG_ID, "ORG_MANAGE_SETTINGS"))
                    .thenReturn(Mono.just(true));
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(orgWithSettings));

            OrganizationSettings updatedSettings = new OrganizationSettings();
            updatedSettings.setAllowMembersToInvite(true);
            Organization updatedOrg = orgWithSettings.toBuilder()
                    .settings(updatedSettings)
                    .build();
            when(organizationService.updateSettings(eq(ORG_ID), any(OrganizationSettings.class)))
                    .thenReturn(Mono.just(updatedOrg));

            // When
            Mono<Organization> result = resolver.updateOrganizationSettings(ORG_ID, input, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getSettings()).isNotNull();
                    })
                    .verifyComplete();

            verify(organizationService).updateSettings(eq(ORG_ID), any(OrganizationSettings.class));
        }

        @Test
        @DisplayName("Should fail when user lacks settings permission")
        void shouldFail_WhenLacksPermission() {
            // Given
            UpdateOrganizationSettingsInput input = new UpdateOrganizationSettingsInput(
                    true, false, true, "PUBLIC"
            );

            when(jwt.getSubject()).thenReturn(USER_ID);
            when(memberService.hasPermission(USER_ID, ORG_ID, "ORG_MANAGE_SETTINGS"))
                    .thenReturn(Mono.just(false));

            // When
            Mono<Organization> result = resolver.updateOrganizationSettings(ORG_ID, input, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                                    error.getMessage().contains("Permission denied: ORG_MANAGE_SETTINGS")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("suspendOrganization - Admin Suspension")
    class SuspendOrganizationTests {

        @Test
        @DisplayName("Should suspend organization when admin provides reason")
        void shouldSuspend_WithReason() {
            // Given
            String reason = "Terms of service violation";
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            Organization suspendedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.SUSPENDED)
                    .rejectionReason(reason)
                    .build();
            when(organizationService.suspend(ORG_ID, reason))
                    .thenReturn(Mono.just(suspendedOrg));

            // When
            Mono<Organization> result = resolver.suspendOrganization(ORG_ID, reason, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
                        assertThat(org.getRejectionReason()).isEqualTo(reason);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when reason is blank")
        void shouldFail_WhenReasonIsBlank() {
            // When
            Mono<Organization> result = resolver.suspendOrganization(ORG_ID, "", jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Suspension reason is required")
                    )
                    .verify();

            verify(organizationService, never()).suspend(any(), any());
        }

        @Test
        @DisplayName("Should fail when reason is null")
        void shouldFail_WhenReasonIsNull() {
            // When
            Mono<Organization> result = resolver.suspendOrganization(ORG_ID, null, jwt);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                                    error.getMessage().contains("Suspension reason is required")
                    )
                    .verify();
        }
    }

    @Nested
    @DisplayName("unsuspendOrganization - Admin Unsuspension")
    class UnsuspendOrganizationTests {

        @Test
        @DisplayName("Should unsuspend organization")
        void shouldUnsuspend() {
            // Given
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            Organization unsuspendedOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.APPROVED)
                    .build();
            when(organizationService.unsuspend(ORG_ID))
                    .thenReturn(Mono.just(unsuspendedOrg));

            // When
            Mono<Organization> result = resolver.unsuspendOrganization(ORG_ID, jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateOrganizationStatus - Admin Status Update")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update status to ACTIVE")
        void shouldUpdateStatus_ToActive() {
            // Given
            when(jwt.getSubject()).thenReturn(ADMIN_ID);

            Organization activeOrg = testOrganization.toBuilder()
                    .status(OrganizationStatus.ACTIVE)
                    .build();
            when(organizationService.updateStatus(ORG_ID, OrganizationStatus.ACTIVE))
                    .thenReturn(Mono.just(activeOrg));

            // When
            Mono<Organization> result = resolver.updateOrganizationStatus(
                    ORG_ID, OrganizationStatus.ACTIVE, jwt
            );

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
                    })
                    .verifyComplete();
        }
    }
}
