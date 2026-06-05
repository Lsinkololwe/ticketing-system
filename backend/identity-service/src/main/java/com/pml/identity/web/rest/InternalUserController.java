package com.pml.identity.web.rest;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.UserService;
import com.pml.shared.dto.UserSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Internal User Controller
 *
 * Provides internal APIs for other microservices to fetch user information.
 * These endpoints are not exposed externally through the API Gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final OrganizationOnboardingService organizationOnboardingService;

    /**
     * Get user summary by ID
     * Used by Catalog and Booking services to fetch user details
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserSummaryDto>> getUserById(@PathVariable String id) {
        log.debug("Internal request for user: {}", id);

        return userService.findById(id)
                .map(this::toUserSummaryDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get user summary by email
     */
    @GetMapping("/email/{email}")
    public Mono<ResponseEntity<UserSummaryDto>> getUserByEmail(@PathVariable String email) {
        log.debug("Internal request for user by email: {}", email);

        return userService.findByEmail(email)
                .map(this::toUserSummaryDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Convert User domain model to UserSummaryDto.
     *
     * <p>Multi-role support: Converts the user's roles set to string role names.</p>
     */
    private UserSummaryDto toUserSummaryDto(com.pml.identity.domain.model.User user) {
        // Convert EnumSet<UserType> to Set<String>
        java.util.Set<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet())
                : java.util.Set.of("CUSTOMER");

        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .roles(roleNames)
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .active(user.isActive())
                .build();
    }

    /**
     * Validate if a user exists and is active.
     *
     * ARCHITECTURE NOTE: Account enabled/disabled status is managed by Keycloak.
     * This endpoint only checks the MongoDB isActive field (soft delete status).
     * For full enabled check, verify against Keycloak.
     */
    @GetMapping("/{id}/exists")
    public Mono<ResponseEntity<Boolean>> userExists(@PathVariable String id) {
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(user.isActive()))
                .defaultIfEmpty(ResponseEntity.ok(false));
    }

    // =============================================
    // ORGANIZATION ONBOARDING (Progressive/Lazy)
    // =============================================

    /**
     * Get or create organization for a user.
     *
     * This endpoint supports the progressive onboarding pattern where organizations
     * are created lazily when a user needs one (e.g., when creating their first event).
     *
     * Security: Requires internal service authentication.
     *
     * @param userId The user ID (Keycloak subject)
     * @return The user's organization (created if it didn't exist)
     */
    @PostMapping("/{userId}/organization")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Organization>> getOrCreateOrganization(@PathVariable String userId) {
        log.info("Internal request to get or create organization for user: {}", userId);

        return organizationOnboardingService.getOrCreateOrganization(userId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to get/create organization for user {}: {}", userId, e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Check if a user has an organization.
     *
     * @param userId The user ID
     * @return true if user has an organization, false otherwise
     */
    @GetMapping("/{userId}/has-organization")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Boolean>> hasOrganization(@PathVariable String userId) {
        log.debug("Checking if user {} has organization", userId);

        return organizationOnboardingService.hasOrganization(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * Get organization by owner ID.
     *
     * @param userId The owner's user ID
     * @return The organization if found
     */
    @GetMapping("/{userId}/organization")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Organization>> getOrganizationByOwnerId(@PathVariable String userId) {
        log.debug("Fetching organization for user: {}", userId);

        return organizationOnboardingService.findOrganizationByOwnerId(userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
