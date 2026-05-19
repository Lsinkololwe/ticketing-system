package com.pml.catalog.infrastructure.client;

import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Identity Service Client
 *
 * <p>Client for calling Identity Service authorization API.
 * Used to verify user permissions before performing event operations.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Centralized authorization via Identity Service</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with cross-service auth checks</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityServiceClient {

    @Qualifier("identityServiceWebClient")
    private final WebClient identityServiceWebClient;

    /**
     * Check if a user is authorized to perform an action.
     *
     * @param request Authorization request
     * @return Authorization result
     */
    public Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request) {
        log.debug("Checking authorization: userId={}, permission={}, eventId={}",
                request.getUserId(), request.getRequiredPermission(), request.getEventId());

        return identityServiceWebClient.post()
                .uri("/api/internal/authorization/check")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(AuthorizationResult.class)
                                .map(result -> new AuthorizationDeniedException(
                                        result.getReason() != null ? result.getReason() : "Authorization denied"
                                )))
                .bodyToMono(AuthorizationResult.class)
                .doOnSuccess(result -> log.debug("Authorization result: authorized={}, source={}",
                        result.isAuthorized(), result.getAuthorizationSource()))
                .doOnError(e -> log.error("Authorization check failed: {}", e.getMessage()));
    }

    /**
     * Check if user can perform an action on events within an organization.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @param permission Required permission
     * @return Authorization result
     */
    public Mono<AuthorizationResult> checkEventPermission(String userId, String organizationId, String permission) {
        log.debug("Checking event permission: userId={}, orgId={}, permission={}",
                userId, organizationId, permission);

        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/event-permission")
                        .queryParam("userId", userId)
                        .queryParam("organizationId", organizationId)
                        .queryParam("permission", permission)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(AuthorizationResult.class)
                                .map(result -> new AuthorizationDeniedException(
                                        result.getReason() != null ? result.getReason() : "Permission denied"
                                )))
                .bodyToMono(AuthorizationResult.class);
    }

    /**
     * Check if user has access to a specific event.
     *
     * @param userId User ID (from JWT)
     * @param eventId Event ID
     * @param organizationId Organization ID (optional)
     * @param permission Required permission
     * @return Authorization result
     */
    public Mono<AuthorizationResult> checkEventAccess(
            String userId,
            String eventId,
            String organizationId,
            String permission) {

        log.debug("Checking event access: userId={}, eventId={}, permission={}",
                userId, eventId, permission);

        return identityServiceWebClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/internal/authorization/event-access")
                            .queryParam("userId", userId)
                            .queryParam("eventId", eventId)
                            .queryParam("permission", permission);
                    if (organizationId != null) {
                        builder.queryParam("organizationId", organizationId);
                    }
                    return builder.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(AuthorizationResult.class)
                                .map(result -> new AuthorizationDeniedException(
                                        result.getReason() != null ? result.getReason() : "Access denied"
                                )))
                .bodyToMono(AuthorizationResult.class);
    }

    /**
     * Check if user is a member of an organization.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @param minimumRole Minimum role required
     * @return Authorization result
     */
    public Mono<AuthorizationResult> checkMembership(String userId, String organizationId, String minimumRole) {
        log.debug("Checking membership: userId={}, orgId={}, minimumRole={}",
                userId, organizationId, minimumRole);

        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/membership")
                        .queryParam("userId", userId)
                        .queryParam("organizationId", organizationId)
                        .queryParam("minimumRole", minimumRole)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(AuthorizationResult.class)
                                .map(result -> new AuthorizationDeniedException(
                                        result.getReason() != null ? result.getReason() : "Not a member"
                                )))
                .bodyToMono(AuthorizationResult.class);
    }

    /**
     * Check if user owns the organization.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @return Authorization result
     */
    public Mono<AuthorizationResult> checkOwnership(String userId, String organizationId) {
        log.debug("Checking ownership: userId={}, orgId={}", userId, organizationId);

        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/ownership")
                        .queryParam("userId", userId)
                        .queryParam("organizationId", organizationId)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(AuthorizationResult.class)
                                .map(result -> new AuthorizationDeniedException(
                                        result.getReason() != null ? result.getReason() : "Not the owner"
                                )))
                .bodyToMono(AuthorizationResult.class);
    }

    /**
     * Get the default organization for a user.
     *
     * <p>Returns the organization where the user is owner or has EVENT_CREATE permission.</p>
     *
     * @param userId User ID (from JWT)
     * @return Organization ID or empty if none found
     */
    public Mono<String> getDefaultOrganization(String userId) {
        log.debug("Getting default organization for user: {}", userId);

        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/default-organization")
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.empty())
                .bodyToMono(OrganizationResponse.class)
                .map(OrganizationResponse::organizationId)
                .doOnSuccess(orgId -> {
                    if (orgId != null) {
                        log.debug("Default organization found: {}", orgId);
                    } else {
                        log.debug("No default organization found for user: {}", userId);
                    }
                });
    }

    /**
     * Find organization by owner ID.
     *
     * @param ownerId Owner user ID
     * @return Organization ID or empty if none found
     */
    public Mono<String> findOrganizationByOwner(String ownerId) {
        log.debug("Finding organization by owner: {}", ownerId);

        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/organization-by-owner")
                        .queryParam("ownerId", ownerId)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.empty())
                .bodyToMono(OrganizationResponse.class)
                .map(OrganizationResponse::organizationId);
    }

    /**
     * Require authorization - returns void if authorized, throws exception if denied.
     *
     * @param request Authorization request
     * @return Empty Mono if authorized
     * @throws AuthorizationDeniedException if not authorized
     */
    public Mono<Void> requireAuthorization(AuthorizationRequest request) {
        return checkAuthorization(request)
                .flatMap(result -> {
                    if (result.isAuthorized()) {
                        return Mono.empty();
                    }
                    return Mono.error(new AuthorizationDeniedException(
                            result.getReason() != null ? result.getReason() : "Authorization denied"
                    ));
                });
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

        return identityServiceWebClient.get()
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

        return identityServiceWebClient.get()
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

        return identityServiceWebClient.get()
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
     * Response DTO for organization endpoints.
     */
    private record OrganizationResponse(String organizationId) {}

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
     * Exception thrown when authorization is denied.
     */
    public static class AuthorizationDeniedException extends RuntimeException {
        public AuthorizationDeniedException(String message) {
            super(message);
        }
    }
}
