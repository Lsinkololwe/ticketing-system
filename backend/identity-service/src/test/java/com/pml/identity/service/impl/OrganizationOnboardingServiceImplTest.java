package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.event.OrganizationApprovedEvent;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.BusinessAddress;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.RoleSyncService;
import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for OrganizationOnboardingServiceImpl
 *
 * Focus: Finding bugs through edge cases, validation errors, race conditions,
 * and state management issues.
 *
 * Test Categories:
 * 1. Happy path scenarios
 * 2. Validation edge cases (null, empty, whitespace)
 * 3. State transition errors (invalid status changes)
 * 4. Ownership and security violations
 * 5. Concurrency issues (duplicate submissions)
 * 6. Data integrity (slug uniqueness, field updates)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationOnboardingService Unit Tests")
class OrganizationOnboardingServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleSyncService roleSyncService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrganizationOnboardingServiceImpl service;

    private User testUser;
    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        service = new OrganizationOnboardingServiceImpl(
                organizationRepository,
                organizationMemberRepository,
                userRepository,
                roleSyncService,
                eventPublisher
        );

        testUser = User.builder()
                .id("user-123")
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+260971234567")
                .roles(EnumSet.of(UserType.CUSTOMER))
                .build();

        testOrganization = Organization.builder()
                .id("org-456")
                .name("Test Organization")
                .slug("test-organization")
                .ownerId(testUser.getId())
                .status(OrganizationStatus.DRAFT)
                .type(OrganizationType.INDIVIDUAL)
                .businessEmail(testUser.getEmail())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // =========================================================================
    // APPLY TO BE ORGANIZER TESTS
    // =========================================================================

    @Nested
    @DisplayName("applyToBeOrganizer Tests")
    class ApplyToBeOrganizerTests {

        @Test
        @DisplayName("Should create organization successfully with valid input")
        void shouldCreateOrganizationSuccessfully() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "My Event Company",
                    "We organize amazing events",
                    "Your trusted event partner",
                    null, // logoUrl
                    null, // bannerUrl
                    "https://example.com",
                    OrganizationType.BUSINESS,
                    "contact@example.com",
                    "+260971234567",
                    "Lusaka",
                    "Lusaka Province",
                    "Zambia",
                    null // socialLinks
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.just(testUser));
            when(organizationRepository.existsBySlug(anyString())).thenReturn(Mono.just(false));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getName()).isEqualTo("My Event Company");
                        assertThat(org.getDescription()).isEqualTo("We organize amazing events");
                        assertThat(org.getTagline()).isEqualTo("Your trusted event partner");
                        assertThat(org.getType()).isEqualTo(OrganizationType.BUSINESS);
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.DRAFT);
                        assertThat(org.getOwnerId()).isEqualTo(testUser.getId());
                        assertThat(org.getBusinessAddress()).isNotNull();
                        assertThat(org.getBusinessAddress().getCity()).isEqualTo("Lusaka");
                        assertThat(org.getBusinessAddress().getProvince()).isEqualTo("Lusaka Province");
                        assertThat(org.getBusinessAddress().getCountry()).isEqualTo("Zambia");
                    })
                    .verifyComplete();

            // Verify organization was saved
            verify(organizationRepository).save(any(Organization.class));

            // Verify owner membership was created
            ArgumentCaptor<OrganizationMember> memberCaptor = ArgumentCaptor.forClass(OrganizationMember.class);
            verify(organizationMemberRepository).save(memberCaptor.capture());
            OrganizationMember member = memberCaptor.getValue();
            assertThat(member.getUserId()).isEqualTo(testUser.getId());
            assertThat(member.getRole()).isEqualTo(OrganizationRole.OWNER);
            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should fail when user already has organization")
        void shouldFailWhenUserAlreadyHasOrganization() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Second Organization",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(testUser.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("already have an organization")
                    )
                    .verify();

            // Verify no new organization was created
            verify(organizationRepository, never()).save(any(Organization.class));
        }

        @Test
        @DisplayName("Should fail when user does not exist")
        void shouldFailWhenUserDoesNotExist() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Test Org",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("User not found")
                    )
                    .verify();

            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should generate slug from organization name")
        void shouldGenerateSlugFromOrganizationName() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Acme Events & Co.",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.just(testUser));
            when(organizationRepository.existsBySlug(anyString())).thenReturn(Mono.just(false));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .assertNext(org -> {
                        // Slug should be lowercase, alphanumeric with hyphens
                        assertThat(org.getSlug()).matches("^[a-z0-9-]+$");
                        assertThat(org.getSlug()).contains("acme");
                        assertThat(org.getSlug()).contains("events");
                        assertThat(org.getSlug()).contains("co");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle slug collision by generating unique suffix")
        void shouldHandleSlugCollision() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Test Organization",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.just(testUser));
            when(organizationRepository.existsBySlug("test-organization")).thenReturn(Mono.just(true)); // Slug taken
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .assertNext(org -> {
                        // Slug should have unique suffix
                        assertThat(org.getSlug()).startsWith("test-organization-");
                        assertThat(org.getSlug()).hasSize("test-organization-".length() + 6); // 6-char UUID suffix
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle null or empty organization name gracefully")
        void shouldHandleNullOrganizationName() {
            // Given - null name, should fallback to user's name
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    null,
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.just(testUser));
            when(organizationRepository.existsBySlug(anyString())).thenReturn(Mono.just(false));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .assertNext(org -> {
                        // Should fallback to user's full name
                        assertThat(org.getName()).isEqualTo("Test User");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use email prefix when user has no first/last name")
        void shouldUseEmailPrefixWhenNoName() {
            // Given
            User userWithoutName = testUser.toBuilder()
                    .firstName(null)
                    .lastName(null)
                    .email("john.doe@example.com")
                    .build();

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    null,
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(userWithoutName.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(userWithoutName.getId())).thenReturn(Mono.just(userWithoutName));
            when(organizationRepository.existsBySlug(anyString())).thenReturn(Mono.just(false));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(userWithoutName.getId(), input))
                    .assertNext(org -> {
                        // Should extract from email
                        assertThat(org.getName()).contains("John");
                        assertThat(org.getName()).contains("Doe");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should set default country to Zambia when not provided")
        void shouldSetDefaultCountryToZambia() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Test Org",
                    null, null, null, null, null, null, null, null,
                    "Lusaka", // city
                    "Lusaka Province", // province
                    null, // country (not provided)
                    null
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.just(testUser));
            when(organizationRepository.existsBySlug(anyString())).thenReturn(Mono.just(false));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getBusinessAddress()).isNotNull();
                        assertThat(org.getBusinessAddress().getCountry()).isEqualTo("Zambia");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should default to INDIVIDUAL type when not specified")
        void shouldDefaultToIndividualType() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Test Org",
                    null, null, null, null, null,
                    null, // type not specified
                    null, null, null, null, null, null
            );

            when(organizationRepository.findByOwnerId(testUser.getId())).thenReturn(Mono.empty());
            when(userRepository.findById(testUser.getId())).thenReturn(Mono.just(testUser));
            when(organizationRepository.existsBySlug(anyString())).thenReturn(Mono.just(false));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(organizationMemberRepository.save(any(OrganizationMember.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.applyToBeOrganizer(testUser.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getType()).isEqualTo(OrganizationType.INDIVIDUAL);
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // UPDATE APPLICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("updateApplication Tests")
    class UpdateApplicationTests {

        @Test
        @DisplayName("Should update organization fields successfully")
        void shouldUpdateOrganizationFieldsSuccessfully() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    "Updated description",
                    "Updated tagline",
                    "https://example.com/logo.png",
                    "https://example.com/banner.png",
                    "https://updated-website.com",
                    OrganizationType.BUSINESS,
                    "+260977777777", // businessPhone (record order: phone before email)
                    "new@example.com", // businessEmail
                    "Ndola",
                    "Copperbelt Province",
                    "Zambia",
                    null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getName()).isEqualTo("Updated Name");
                        assertThat(org.getDescription()).isEqualTo("Updated description");
                        assertThat(org.getTagline()).isEqualTo("Updated tagline");
                        assertThat(org.getLogoUrl()).isEqualTo("https://example.com/logo.png");
                        assertThat(org.getBannerUrl()).isEqualTo("https://example.com/banner.png");
                        assertThat(org.getWebsite()).isEqualTo("https://updated-website.com");
                        assertThat(org.getType()).isEqualTo(OrganizationType.BUSINESS);
                        assertThat(org.getBusinessEmail()).isEqualTo("new@example.com");
                        assertThat(org.getBusinessPhone()).isEqualTo("+260977777777");
                        assertThat(org.getBusinessAddress().getCity()).isEqualTo("Ndola");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when organization is SUSPENDED")
        void shouldFailWhenOrganizationIsSuspended() {
            // Given - SUSPENDED is a non-editable status (APPROVED/ACTIVE remain editable
            // per OrganizationStatus.canBeEdited()).
            testOrganization.setStatus(OrganizationStatus.SUSPENDED);

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("cannot be edited")
                    )
                    .verify();

            verify(organizationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should fail when organization is REJECTED")
        void shouldFailWhenOrganizationIsRejected() {
            // Given
            testOrganization.setStatus(OrganizationStatus.REJECTED);

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("cannot be edited")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should allow updates when status is CHANGES_REQUESTED")
        void shouldAllowUpdatesWhenChangesRequested() {
            // Given
            testOrganization.setStatus(OrganizationStatus.CHANGES_REQUESTED);

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getName()).isEqualTo("Updated Name");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when organization does not exist")
        void shouldFailWhenOrganizationDoesNotExist() {
            // Given
            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findById("non-existent-id")).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.updateApplication("non-existent-id", input))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Organization not found")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should preserve existing fields when input has null values")
        void shouldPreserveExistingFieldsWhenInputHasNullValues() {
            // Given
            testOrganization.setDescription("Original description");
            testOrganization.setTagline("Original tagline");
            testOrganization.setWebsite("https://original.com");

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    null, // description null
                    null, // tagline null
                    null, null,
                    null, // website null
                    null, null, null, null, null, null, null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getName()).isEqualTo("Updated Name");
                        // These should remain unchanged
                        assertThat(org.getDescription()).isEqualTo("Original description");
                        assertThat(org.getTagline()).isEqualTo("Original tagline");
                        assertThat(org.getWebsite()).isEqualTo("https://original.com");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should not update name when input name is blank")
        void shouldNotUpdateNameWhenBlank() {
            // Given
            String originalName = testOrganization.getName();

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "   ", // blank name
                    "Updated description",
                    null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .assertNext(org -> {
                        // Name should remain unchanged
                        assertThat(org.getName()).isEqualTo(originalName);
                        // Description should be updated
                        assertThat(org.getDescription()).isEqualTo("Updated description");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should update timestamp when application is updated")
        void shouldUpdateTimestamp() {
            // Given
            Instant originalUpdatedAt = testOrganization.getUpdatedAt();

            OrganizationApplicationInput input = new OrganizationApplicationInput(
                    "Updated Name",
                    null, null, null, null, null, null, null, null, null, null, null, null
            );

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.updateApplication(testOrganization.getId(), input))
                    .assertNext(org -> {
                        assertThat(org.getUpdatedAt()).isAfter(originalUpdatedAt);
                    })
                    .verifyComplete();
        }
    }

    // =========================================================================
    // SUBMIT FOR REVIEW TESTS
    // =========================================================================

    @Nested
    @DisplayName("submitForReview Tests")
    class SubmitForReviewTests {

        @Test
        @DisplayName("Should submit DRAFT organization for review successfully")
        void shouldSubmitDraftForReview() {
            // Given
            testOrganization.setStatus(OrganizationStatus.DRAFT);
            testOrganization.setName("Test Organization");
            testOrganization.setBusinessEmail("test@example.com");

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
                        assertThat(org.getSubmittedAt()).isNotNull();
                        assertThat(org.getRejectionReason()).isNull(); // Should clear any previous rejection
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should submit CHANGES_REQUESTED organization for review")
        void shouldSubmitChangesRequestedForReview() {
            // Given
            testOrganization.setStatus(OrganizationStatus.CHANGES_REQUESTED);
            testOrganization.setName("Test Organization");
            testOrganization.setBusinessEmail("test@example.com");
            testOrganization.setRejectionReason("Previous issue");

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
                        assertThat(org.getRejectionReason()).isNull(); // Should be cleared
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when organization name is missing")
        void shouldFailWhenNameIsMissing() {
            // Given
            testOrganization.setStatus(OrganizationStatus.DRAFT);
            testOrganization.setName(null);
            testOrganization.setBusinessEmail("test@example.com");

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("name is required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when organization name is blank")
        void shouldFailWhenNameIsBlank() {
            // Given
            testOrganization.setStatus(OrganizationStatus.DRAFT);
            testOrganization.setName("   ");
            testOrganization.setBusinessEmail("test@example.com");

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("name is required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when business email is missing")
        void shouldFailWhenBusinessEmailIsMissing() {
            // Given
            testOrganization.setStatus(OrganizationStatus.DRAFT);
            testOrganization.setName("Test Organization");
            testOrganization.setBusinessEmail(null);

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("Business email is required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when status is APPROVED")
        void shouldFailWhenStatusIsApproved() {
            // Given
            testOrganization.setStatus(OrganizationStatus.APPROVED);

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("cannot be submitted for review")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when status is PENDING_REVIEW")
        void shouldFailWhenAlreadyPendingReview() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.submitForReview(testOrganization.getId()))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("cannot be submitted for review")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when organization does not exist")
        void shouldFailWhenOrganizationDoesNotExist() {
            // Given
            when(organizationRepository.findById("non-existent-id")).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.submitForReview("non-existent-id"))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Organization not found")
                    )
                    .verify();
        }
    }

    // =========================================================================
    // APPROVE TESTS (ADMIN OPERATIONS)
    // =========================================================================

    @Nested
    @DisplayName("approve Tests")
    class ApproveTests {

        @Test
        @DisplayName("Should approve organization and grant ORGANIZER role successfully")
        void shouldApproveOrganizationSuccessfully() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(roleSyncService.grantOrganizerRole(testUser.getId(), adminId, testOrganization.getId()))
                    .thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.approve(testOrganization.getId(), adminId))
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                        assertThat(org.isVerified()).isTrue();
                        assertThat(org.getVerifiedBy()).isEqualTo(adminId);
                        assertThat(org.getVerifiedAt()).isNotNull();
                        assertThat(org.getReviewedBy()).isEqualTo(adminId);
                        assertThat(org.getReviewedAt()).isNotNull();
                        assertThat(org.getApprovedAt()).isNotNull();
                        assertThat(org.getRejectionReason()).isNull();
                    })
                    .verifyComplete();

            // Verify role was granted
            verify(roleSyncService).grantOrganizerRole(testUser.getId(), adminId, testOrganization.getId());

            // Verify event was published (best effort, doesn't fail)
            verify(eventPublisher).publishEvent(any(OrganizationApprovedEvent.class));
        }

        @Test
        @DisplayName("Should complete approval even if role grant fails")
        void shouldCompleteApprovalEvenIfRoleGrantFails() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(roleSyncService.grantOrganizerRole(testUser.getId(), adminId, testOrganization.getId()))
                    .thenReturn(Mono.error(new RuntimeException("Keycloak error")));

            // When & Then
            StepVerifier.create(service.approve(testOrganization.getId(), adminId))
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.APPROVED);
                        assertThat(org.isVerified()).isTrue();
                    })
                    .verifyComplete();

            // Role grant was attempted
            verify(roleSyncService).grantOrganizerRole(testUser.getId(), adminId, testOrganization.getId());
        }

        @Test
        @DisplayName("Should fail when organization is not PENDING_REVIEW")
        void shouldFailWhenNotPendingReview() {
            // Given
            testOrganization.setStatus(OrganizationStatus.DRAFT);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.approve(testOrganization.getId(), adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("Only organizations in PENDING_REVIEW")
                    )
                    .verify();

            verify(roleSyncService, never()).grantOrganizerRole(any(), any(), any());
        }

        @Test
        @DisplayName("Should fail when organization does not exist")
        void shouldFailWhenOrganizationDoesNotExist() {
            // Given
            String adminId = "admin-123";
            when(organizationRepository.findById("non-existent-id")).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(service.approve("non-existent-id", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Organization not found")
                    )
                    .verify();
        }
    }

    // =========================================================================
    // REQUEST CHANGES TESTS
    // =========================================================================

    @Nested
    @DisplayName("requestChanges Tests")
    class RequestChangesTests {

        @Test
        @DisplayName("Should request changes successfully")
        void shouldRequestChangesSuccessfully() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";
            String reason = "Please provide business registration certificate";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.requestChanges(testOrganization.getId(), reason, adminId))
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.CHANGES_REQUESTED);
                        assertThat(org.getRejectionReason()).isEqualTo(reason);
                        assertThat(org.getReviewedBy()).isEqualTo(adminId);
                        assertThat(org.getReviewedAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when reason is null")
        void shouldFailWhenReasonIsNull() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.requestChanges(testOrganization.getId(), null, adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Reason for changes is required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when reason is blank")
        void shouldFailWhenReasonIsBlank() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.requestChanges(testOrganization.getId(), "   ", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Reason for changes is required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when organization is not PENDING_REVIEW")
        void shouldFailWhenNotPendingReview() {
            // Given
            testOrganization.setStatus(OrganizationStatus.APPROVED);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.requestChanges(testOrganization.getId(), "Reason", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("Only organizations in PENDING_REVIEW")
                    )
                    .verify();
        }
    }

    // =========================================================================
    // REJECT TESTS
    // =========================================================================

    @Nested
    @DisplayName("reject Tests")
    class RejectTests {

        @Test
        @DisplayName("Should reject organization successfully")
        void shouldRejectOrganizationSuccessfully() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";
            String reason = "Business information not verifiable";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // When & Then
            StepVerifier.create(service.reject(testOrganization.getId(), reason, adminId))
                    .assertNext(org -> {
                        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.REJECTED);
                        assertThat(org.getRejectionReason()).isEqualTo(reason);
                        assertThat(org.getReviewedBy()).isEqualTo(adminId);
                        assertThat(org.getReviewedAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when rejection reason is null")
        void shouldFailWhenReasonIsNull() {
            // Given
            testOrganization.setStatus(OrganizationStatus.PENDING_REVIEW);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.reject(testOrganization.getId(), null, adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalArgumentException &&
                            error.getMessage().contains("Rejection reason is required")
                    )
                    .verify();
        }

        @Test
        @DisplayName("Should fail when organization is not PENDING_REVIEW")
        void shouldFailWhenNotPendingReview() {
            // Given
            testOrganization.setStatus(OrganizationStatus.DRAFT);
            String adminId = "admin-123";

            when(organizationRepository.findById(testOrganization.getId()))
                    .thenReturn(Mono.just(testOrganization));

            // When & Then
            StepVerifier.create(service.reject(testOrganization.getId(), "Reason", adminId))
                    .expectErrorMatches(error ->
                            error instanceof IllegalStateException &&
                            error.getMessage().contains("Only organizations in PENDING_REVIEW")
                    )
                    .verify();
        }
    }
}
