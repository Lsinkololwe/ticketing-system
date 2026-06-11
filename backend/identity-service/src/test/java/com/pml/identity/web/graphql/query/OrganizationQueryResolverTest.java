package com.pml.identity.web.graphql.query;

import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.identity.web.graphql.dto.pagination.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for OrganizationQueryResolver.
 *
 * Tests cover:
 * - Single entity queries (by ID, slug, owner)
 * - Collection queries (my organizations, all organizations)
 * - Pagination (offset and cursor-based)
 * - Filtering (status, search, verified)
 * - Security (data isolation, admin-only queries)
 * - Edge cases (empty results, null parameters)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationQueryResolver Unit Tests")
class OrganizationQueryResolverTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private OrganizationMemberService memberService;

    @Mock
    private PermissionResolutionService permissionService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private OrganizationQueryResolver resolver;

    private static final String USER_ID = "user-123";
    private static final String ORG_ID = "org-456";
    private static final String SLUG = "test-organization";

    private Organization testOrganization;
    private List<Organization> testOrganizations;

    @BeforeEach
    void setUp() {
        testOrganization = Organization.builder()
                .id(ORG_ID)
                .name("Test Organization")
                .slug(SLUG)
                .ownerId(USER_ID)
                .status(OrganizationStatus.APPROVED)
                .verified(true)
                .createdAt(Instant.now())
                .build();

        Organization org2 = Organization.builder()
                .id("org-789")
                .name("Another Organization")
                .slug("another-organization")
                .ownerId("user-999")
                .status(OrganizationStatus.PENDING_REVIEW)
                .verified(false)
                .createdAt(Instant.now())
                .build();

        Organization org3 = Organization.builder()
                .id("org-abc")
                .name("Third Organization")
                .slug("third-organization")
                .ownerId("user-111")
                .status(OrganizationStatus.DRAFT)
                .verified(false)
                .createdAt(Instant.now())
                .build();

        testOrganizations = List.of(testOrganization, org2, org3);
    }

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    @Nested
    @DisplayName("organization - Get by ID")
    class OrganizationByIdTests {

        @Test
        @DisplayName("Should return organization when ID exists")
        void shouldReturnOrganization_WhenIdExists() {
            // Given
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.organization(ORG_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org).isNotNull();
                        assertThat(org.getId()).isEqualTo(ORG_ID);
                        assertThat(org.getName()).isEqualTo("Test Organization");
                    })
                    .verifyComplete();

            verify(organizationService).findById(ORG_ID);
        }

        @Test
        @DisplayName("Should return empty when ID not found")
        void shouldReturnEmpty_WhenIdNotFound() {
            // Given
            when(organizationService.findById("nonexistent"))
                    .thenReturn(Mono.empty());

            // When
            Mono<Organization> result = resolver.organization("nonexistent");

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should throw NPE when ID is null")
        void shouldThrowNPE_WhenIdIsNull() {
            // When/Then
            StepVerifier.create(resolver.organization(null))
                    .expectError(NullPointerException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("organizationBySlug - Get by Slug")
    class OrganizationBySlugTests {

        @Test
        @DisplayName("Should return organization when slug exists")
        void shouldReturnOrganization_WhenSlugExists() {
            // Given
            when(organizationService.findBySlug(SLUG))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.organizationBySlug(SLUG);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getSlug()).isEqualTo(SLUG);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when slug not found")
        void shouldReturnEmpty_WhenSlugNotFound() {
            // Given
            when(organizationService.findBySlug("nonexistent-slug"))
                    .thenReturn(Mono.empty());

            // When
            Mono<Organization> result = resolver.organizationBySlug("nonexistent-slug");

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("organizationByOwnerId - Get by Owner")
    class OrganizationByOwnerIdTests {

        @Test
        @DisplayName("Should return organization when owner exists")
        void shouldReturnOrganization_WhenOwnerExists() {
            // Given
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.organizationByOwnerId(USER_ID);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getOwnerId()).isEqualTo(USER_ID);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when owner has no organization")
        void shouldReturnEmpty_WhenOwnerHasNoOrganization() {
            // Given
            when(organizationService.findByOwnerId("user-without-org"))
                    .thenReturn(Mono.empty());

            // When
            Mono<Organization> result = resolver.organizationByOwnerId("user-without-org");

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("myOrganizations - Get User's Organizations")
    class MyOrganizationsTests {

        @Test
        @DisplayName("Should return organizations user is a member of")
        void shouldReturnOrganizations_WhenUserIsMember() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);

            OrganizationMember member1 = OrganizationMember.builder()
                    .id("member-1")
                    .organizationId(ORG_ID)
                    .userId(USER_ID)
                    .build();

            OrganizationMember member2 = OrganizationMember.builder()
                    .id("member-2")
                    .organizationId("org-789")
                    .userId(USER_ID)
                    .build();

            when(memberService.findByUser(USER_ID))
                    .thenReturn(Flux.just(member1, member2));
            when(organizationService.findById(ORG_ID))
                    .thenReturn(Mono.just(testOrganization));
            when(organizationService.findById("org-789"))
                    .thenReturn(Mono.just(testOrganizations.get(1)));

            // When
            Flux<Organization> result = resolver.myOrganizations(jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> assertThat(org.getId()).isEqualTo(ORG_ID))
                    .assertNext(org -> assertThat(org.getId()).isEqualTo("org-789"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when JWT is null")
        void shouldReturnEmpty_WhenJwtIsNull() {
            // When
            Flux<Organization> result = resolver.myOrganizations(null);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when user is not a member of any organization")
        void shouldReturnEmpty_WhenUserHasNoMemberships() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(memberService.findByUser(USER_ID))
                    .thenReturn(Flux.empty());

            // When
            Flux<Organization> result = resolver.myOrganizations(jwt);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("myOwnedOrganization - Get Owned Organization")
    class MyOwnedOrganizationTests {

        @Test
        @DisplayName("Should return organization when user owns one")
        void shouldReturnOrganization_WhenUserOwnsOne() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.just(testOrganization));

            // When
            Mono<Organization> result = resolver.myOwnedOrganization(jwt);

            // Then
            StepVerifier.create(result)
                    .assertNext(org -> {
                        assertThat(org.getOwnerId()).isEqualTo(USER_ID);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when JWT is null")
        void shouldReturnEmpty_WhenJwtIsNull() {
            // When
            Mono<Organization> result = resolver.myOwnedOrganization(null);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when user owns no organization")
        void shouldReturnEmpty_WhenUserOwnsNoOrganization() {
            // Given
            when(jwt.getSubject()).thenReturn(USER_ID);
            when(organizationService.findByOwnerId(USER_ID))
                    .thenReturn(Mono.empty());

            // When
            Mono<Organization> result = resolver.myOwnedOrganization(jwt);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
        }
    }

    // ========================================================================
    // ORGANIZATION APPLICATIONS QUERIES (Admin)
    // ========================================================================

    @Nested
    @DisplayName("organizationApplicationsOffsetPagination - Approval Queue")
    class ApplicationsOffsetPaginationTests {

        @Test
        @DisplayName("Should return pending applications with pagination")
        void shouldReturnPendingApplications_WithPagination() {
            // Given
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            List<Organization> pendingOrgs = testOrganizations.stream()
                    .filter(org -> org.getStatus() == OrganizationStatus.PENDING_REVIEW)
                    .toList();

            when(organizationService.findByStatus(OrganizationStatus.PENDING_REVIEW))
                    .thenReturn(Flux.fromIterable(pendingOrgs));

            // When
            Mono<OrganizationApplicationOffsetPage> result =
                    resolver.organizationApplicationsOffsetPagination(
                            OrganizationStatus.PENDING_REVIEW, pagination
                    );

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(1);
                        assertThat(page.pageInfo().totalElements()).isEqualTo(1);
                        assertThat(page.content().get(0).getStatus())
                                .isEqualTo(OrganizationStatus.PENDING_REVIEW);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return all approval workflow orgs when status is null")
        void shouldReturnAllApprovalWorkflow_WhenStatusIsNull() {
            // Given
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            when(organizationService.findInApprovalWorkflow())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationApplicationOffsetPage> result =
                    resolver.organizationApplicationsOffsetPagination(null, pagination);

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(3);
                        assertThat(page.pageInfo().totalElements()).isEqualTo(3);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use default pagination when null")
        void shouldUseDefaultPagination_WhenNull() {
            // Given
            when(organizationService.findInApprovalWorkflow())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationApplicationOffsetPage> result =
                    resolver.organizationApplicationsOffsetPagination(null, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).isNotEmpty();
                        assertThat(page.pageInfo()).isNotNull();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("organizationApplicationsCursorPagination - Mobile/Infinite Scroll")
    class ApplicationsCursorPaginationTests {

        @Test
        @DisplayName("Should return cursor-based pagination")
        void shouldReturnCursorPagination() {
            // Given
            CursorPaginationInput pagination = CursorPaginationInput.first(10);
            when(organizationService.findInApprovalWorkflow())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationApplicationConnection> result =
                    resolver.organizationApplicationsCursorPagination(null, pagination);

            // Then
            StepVerifier.create(result)
                    .assertNext(connection -> {
                        assertThat(connection.edges()).hasSize(3);
                        assertThat(connection.totalCount()).isEqualTo(3);
                        assertThat(connection.pageInfo()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter by status in cursor pagination")
        void shouldFilterByStatus() {
            // Given
            CursorPaginationInput pagination = CursorPaginationInput.first(10);
            List<Organization> approvedOrgs = testOrganizations.stream()
                    .filter(org -> org.getStatus() == OrganizationStatus.APPROVED)
                    .toList();

            when(organizationService.findByStatus(OrganizationStatus.APPROVED))
                    .thenReturn(Flux.fromIterable(approvedOrgs));

            // When
            Mono<OrganizationApplicationConnection> result =
                    resolver.organizationApplicationsCursorPagination(
                            OrganizationStatus.APPROVED, pagination
                    );

            // Then
            StepVerifier.create(result)
                    .assertNext(connection -> {
                        assertThat(connection.edges()).hasSize(1);
                        assertThat(connection.edges().get(0).node().getStatus())
                                .isEqualTo(OrganizationStatus.APPROVED);
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    @Nested
    @DisplayName("organizationsOffsetPagination - Admin Search")
    class OrganizationsOffsetPaginationTests {

        @Test
        @DisplayName("Should return all organizations with pagination")
        void shouldReturnAll_WithPagination() {
            // Given
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationOffsetPage> result =
                    resolver.organizationsOffsetPagination(null, null, null, pagination);

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(3);
                        assertThat(page.pageInfo().totalElements()).isEqualTo(3);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter by search term")
        void shouldFilterBySearch() {
            // Given
            String searchTerm = "Test";
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationOffsetPage> result =
                    resolver.organizationsOffsetPagination(searchTerm, null, null, pagination);

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(1);
                        assertThat(page.content().get(0).getName()).contains("Test");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() {
            // Given
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationOffsetPage> result =
                    resolver.organizationsOffsetPagination(
                            null, OrganizationStatus.APPROVED, null, pagination
                    );

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(1);
                        assertThat(page.content().get(0).getStatus())
                                .isEqualTo(OrganizationStatus.APPROVED);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter by verified flag")
        void shouldFilterByVerified() {
            // Given
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationOffsetPage> result =
                    resolver.organizationsOffsetPagination(null, null, true, pagination);

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(1);
                        assertThat(page.content().get(0).isVerified()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should combine multiple filters")
        void shouldCombineFilters() {
            // Given
            OffsetPaginationInput pagination = OffsetPaginationInput.of(0, 10);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationOffsetPage> result =
                    resolver.organizationsOffsetPagination(
                            "Test", OrganizationStatus.APPROVED, true, pagination
                    );

            // Then
            StepVerifier.create(result)
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(1);
                        assertThat(page.content().get(0).getName()).contains("Test");
                        assertThat(page.content().get(0).getStatus())
                                .isEqualTo(OrganizationStatus.APPROVED);
                        assertThat(page.content().get(0).isVerified()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    @Nested
    @DisplayName("organizationsCursorPagination - Mobile Search")
    class OrganizationsCursorPaginationTests {

        @Test
        @DisplayName("Should return cursor-based results")
        void shouldReturnCursorResults() {
            // Given
            CursorPaginationInput pagination = CursorPaginationInput.first(2);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationConnection> result =
                    resolver.organizationsCursorPagination(null, null, null, pagination);

            // Then
            StepVerifier.create(result)
                    .assertNext(connection -> {
                        assertThat(connection.edges()).hasSize(2);
                        assertThat(connection.pageInfo().hasNextPage()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should apply filters to cursor pagination")
        void shouldApplyFilters() {
            // Given
            CursorPaginationInput pagination = CursorPaginationInput.first(10);
            when(organizationService.findAll())
                    .thenReturn(Flux.fromIterable(testOrganizations));

            // When
            Mono<OrganizationConnection> result =
                    resolver.organizationsCursorPagination(
                            "Another", null, null, pagination
                    );

            // Then
            StepVerifier.create(result)
                    .assertNext(connection -> {
                        assertThat(connection.edges()).hasSize(1);
                        assertThat(connection.edges().get(0).node().getName())
                                .contains("Another");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // UTILITY QUERIES
    // ========================================================================

    @Nested
    @DisplayName("isSlugAvailable - Slug Availability Check")
    class SlugAvailabilityTests {

        @Test
        @DisplayName("Should return true when slug is available")
        void shouldReturnTrue_WhenSlugAvailable() {
            // Given
            when(organizationService.isSlugAvailable("new-slug"))
                    .thenReturn(Mono.just(true));

            // When
            Mono<Boolean> result = resolver.isSlugAvailable("new-slug");

            // Then
            StepVerifier.create(result)
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when slug is taken")
        void shouldReturnFalse_WhenSlugTaken() {
            // Given
            when(organizationService.isSlugAvailable(SLUG))
                    .thenReturn(Mono.just(false));

            // When
            Mono<Boolean> result = resolver.isSlugAvailable(SLUG);

            // Then
            StepVerifier.create(result)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("organizationCount - Count by Status")
    class OrganizationCountTests {

        @Test
        @DisplayName("Should return count for specific status")
        void shouldReturnCount_ForStatus() {
            // Given
            when(organizationService.countByStatus(OrganizationStatus.APPROVED))
                    .thenReturn(Mono.just(5L));

            // When
            Mono<Long> result = resolver.organizationCount(OrganizationStatus.APPROVED);

            // Then
            StepVerifier.create(result)
                    .expectNext(5L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return total count when status is null")
        void shouldReturnTotalCount_WhenStatusIsNull() {
            // Given
            when(organizationService.countByStatus(null))
                    .thenReturn(Mono.just(10L));

            // When
            Mono<Long> result = resolver.organizationCount(null);

            // Then
            StepVerifier.create(result)
                    .expectNext(10L)
                    .verifyComplete();
        }
    }
}
