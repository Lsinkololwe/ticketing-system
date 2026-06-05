package com.pml.booking.infrastructure.client;

import com.pml.shared.dto.UserSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client for Identity Service
 *
 * <p>Provides access to identity service APIs including user management
 * and organization membership validation.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Centralized authorization via identity-service</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with service-to-service auth</li>
 * </ul>
 */
@Slf4j
@Component
public class IdentityServiceClient {

    private final WebClient webClient;

    public IdentityServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.identity.url:http://localhost:8083}") String identityServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(identityServiceUrl).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORGANIZATION MEMBERSHIP VALIDATION (OWASP A01:2021)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Check if a user is an active member of an organization.
     *
     * <p>Used for query resolver authorization to ensure users can only
     * access data from organizations they belong to.</p>
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID to check membership for
     * @return Mono with membership check response
     */
    public Mono<MembershipCheckResponse> checkOrganizationMembership(String userId, String organizationId) {
        log.debug("Checking organization membership: userId={}, orgId={}", userId, organizationId);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/check-organization-membership")
                        .queryParam("userId", userId)
                        .queryParam("organizationId", organizationId)
                        .build())
                .retrieve()
                .bodyToMono(MembershipCheckResponse.class)
                .doOnSuccess(result -> log.debug("Membership check result: isMember={}, role={}",
                        result.isMember(), result.role()))
                .onErrorResume(e -> {
                    log.error("Failed to check organization membership: {}", e.getMessage());
                    return Mono.just(MembershipCheckResponse.notMember());
                });
    }

    /**
     * Check if two users belong to the same organization.
     *
     * <p>Used when organizerId is provided in a query and we need to verify
     * the requesting user has access to that organizer's data (team member access).</p>
     *
     * @param requestingUserId The user making the request (from JWT)
     * @param targetOrganizerId The organizer whose data is being requested
     * @return Mono with shared organization response
     */
    public Mono<SharedOrganizationResponse> checkSameOrganization(String requestingUserId, String targetOrganizerId) {
        log.debug("Checking same organization: requestingUserId={}, targetOrganizerId={}",
                requestingUserId, targetOrganizerId);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/check-same-organization")
                        .queryParam("requestingUserId", requestingUserId)
                        .queryParam("targetOrganizerId", targetOrganizerId)
                        .build())
                .retrieve()
                .bodyToMono(SharedOrganizationResponse.class)
                .doOnSuccess(result -> log.debug("Same organization check: shares={}, orgId={}",
                        result.sharesOrganization(), result.sharedOrganizationId()))
                .onErrorResume(e -> {
                    log.error("Failed to check same organization: {}", e.getMessage());
                    return Mono.just(SharedOrganizationResponse.noSharedOrganization());
                });
    }

    /**
     * Get all organizations a user belongs to.
     *
     * @param userId User ID (from JWT)
     * @return Mono with user organizations response
     */
    public Mono<UserOrganizationsResponse> getUserOrganizations(String userId) {
        log.debug("Getting user organizations: userId={}", userId);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/user-organizations")
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .bodyToMono(UserOrganizationsResponse.class)
                .doOnSuccess(result -> log.debug("User has {} organizations", result.organizations().size()))
                .onErrorResume(e -> {
                    log.error("Failed to get user organizations: {}", e.getMessage());
                    return Mono.just(new UserOrganizationsResponse(List.of()));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESPONSE DTOs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Response for organization membership check.
     */
    public record MembershipCheckResponse(
            boolean isMember,
            boolean isActive,
            String role,
            String organizationId
    ) {
        public static MembershipCheckResponse notMember() {
            return new MembershipCheckResponse(false, false, null, null);
        }
    }

    /**
     * Response for same organization check.
     */
    public record SharedOrganizationResponse(
            boolean sharesOrganization,
            String sharedOrganizationId,
            String requestingUserRole,
            String targetUserRole
    ) {
        public static SharedOrganizationResponse noSharedOrganization() {
            return new SharedOrganizationResponse(false, null, null, null);
        }
    }

    /**
     * Response for user organizations query.
     */
    public record UserOrganizationsResponse(
            List<OrganizationMembershipInfo> organizations
    ) {}

    /**
     * Organization membership info.
     */
    public record OrganizationMembershipInfo(
            String organizationId,
            String organizationName,
            String role,
            boolean isOwner,
            boolean isActive
    ) {}

    /**
     * Get user summary by ID
     */
    public Mono<UserSummaryDto> getUserById(String userId) {
        log.debug("Fetching user from identity service: {}", userId);
        return webClient.get()
                .uri("/api/internal/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserSummaryDto.class)
                .doOnSuccess(user -> log.debug("User fetched successfully: {}", userId))
                .doOnError(error -> log.error("Failed to fetch user: {}", userId, error));
    }

    /**
     * Get user by email
     */
    public Mono<UserSummaryDto> getUserByEmail(String email) {
        log.debug("Fetching user by email from identity service: {}", email);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/users/by-email")
                        .queryParam("email", email)
                        .build())
                .retrieve()
                .bodyToMono(UserSummaryDto.class)
                .doOnSuccess(user -> log.debug("User fetched successfully by email: {}", email))
                .doOnError(error -> log.error("Failed to fetch user by email: {}", email, error));
    }

    /**
     * Validate user token
     */
    public Mono<TokenValidationResponse> validateToken(String token) {
        log.debug("Validating token with identity service");
        return webClient.post()
                .uri("/api/internal/users/validate-token")
                .bodyValue(new TokenValidationRequest(token))
                .retrieve()
                .bodyToMono(TokenValidationResponse.class)
                .doOnSuccess(response -> log.debug("Token validation result: {}", response.valid()))
                .doOnError(error -> log.error("Failed to validate token", error));
    }

    public record TokenValidationRequest(String token) {}

    /**
     * Response for token validation.
     *
     * <p>Multi-role support: The {@code roles} field contains all user roles.</p>
     */
    public record TokenValidationResponse(
            boolean valid,
            String userId,
            java.util.Set<String> roles
    ) {
        /**
         * Check if user has a specific role.
         *
         * @param role the role to check (e.g., "ORGANIZER", "ADMIN")
         * @return true if user has the role
         */
        public boolean hasRole(String role) {
            return roles != null && roles.contains(role);
        }

        /**
         * Check if user is an organizer.
         *
         * @return true if user has ORGANIZER role
         */
        public boolean isOrganizer() {
            return hasRole("ORGANIZER");
        }

        /**
         * Check if user is an admin.
         *
         * @return true if user has ADMIN or SUPER_ADMIN role
         */
        public boolean isAdmin() {
            return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        }
    }
}
