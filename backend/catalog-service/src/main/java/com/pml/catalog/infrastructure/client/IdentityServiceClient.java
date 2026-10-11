package com.pml.catalog.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import com.pml.shared.security.InternalServiceWebClients;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
public class IdentityServiceClient {

    private final WebClient identityServiceWebClient;

    public IdentityServiceClient(InternalServiceWebClients clients,
                 @Value("${services.identity.url:http://localhost:8083}") String baseUrl) {
        this.identityServiceWebClient = clients.to(baseUrl);
    }

    /**
     * Check if a user is authorized to perform an action.
     *
     * @param request Authorization request
     * @return Authorization result
     */
    public Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request) {
        // Identity decides "may this user do X in THAT organization" and refuses a request that names none.
        // A caller that names neither an organization, an owner nor an event means "my own organization":
        // resolve it from the memberships identity holds (owner first, else the first active one).
        if (isBlank(request.getOrganizationId()) && isBlank(request.getOrganizationOwnerId()) && isBlank(request.getEventId())
                && !isBlank(request.getUserId())) {
            return getUserOrganizations(request.getUserId())
                    .map(memberships -> {
                        var active = memberships.organizations().stream().filter(OrganizationMembershipInfo::isActive).toList();
                        return active.stream().filter(m -> "OWNER".equals(m.role())).findFirst()
                                .or(() -> active.stream().findFirst())
                                .map(OrganizationMembershipInfo::organizationId).orElse(null);
                    })
                    .map(organizationId -> AuthorizationRequest.builder()
                            .userId(request.getUserId())
                            .organizationId(organizationId)
                            .requiredPermission(request.getRequiredPermission())
                            .build())
                    .defaultIfEmpty(request)
                    .flatMap(this::send);
        }
        return send(request);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Mono<AuthorizationResult> send(AuthorizationRequest request) {
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
     * The organization's public display name, for denormalizing onto an event at creation
     * (e.g. {@code Event.organizerName}). Display data, not an authorization decision: an
     * outage or an unknown id must not block event creation, so the caller is expected to fall
     * back rather than propagate this as a hard failure.
     */
    public Mono<String> getOrganizationName(String organizationId) {
        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/organization-name")
                        .queryParam("organizationId", organizationId)
                        .build())
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.bodyToMono(OrganizationNameResponse.class).map(OrganizationNameResponse::name)
                        : Mono.empty())
                .doOnError(e -> log.warn("Organization name lookup failed for {}: {}", organizationId, e.getMessage()));
    }

    public record OrganizationNameResponse(String name) {
    }

    /** The organizations a user actively belongs to, as identity (the source of truth) reports them. */
    public Mono<UserOrganizationsResponse> getUserOrganizations(String userId) {
        return identityServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/authorization/user-organizations")
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .bodyToMono(UserOrganizationsResponse.class);
    }

    public record UserOrganizationsResponse(java.util.List<OrganizationMembershipInfo> organizations) {
    }

    public record OrganizationMembershipInfo(String organizationId, String role, boolean isActive) {
    }

    /**
     * Response for same organization check.
     */
    public record SharedOrganizationResponse(
            boolean sharesOrganization,
            String sharedOrganizationId
    ) {
        public static SharedOrganizationResponse noSharedOrganization() {
            return new SharedOrganizationResponse(false, null);
        }
    }

    /**
     * Exception thrown when authorization is denied.
     */
    public static class AuthorizationDeniedException extends RuntimeException {
        public AuthorizationDeniedException(String message) {
            super(message);
        }
    }

    /**
     * Asks identity to send an event review's messages. An unreachable identity service or a refused
     * template is an error, so the announcing activity retries.
     */
    public Mono<Void> notifyApproval(String templateKey, String discriminator, String eventId, String organizerId) {
        java.util.Map<String, String> notice = new java.util.HashMap<>();
        notice.put("templateKey", templateKey);
        notice.put("discriminator", discriminator);
        notice.put("eventId", eventId);
        notice.put("organizerId", organizerId);
        return identityServiceWebClient.post()
                .uri("/api/internal/notifications/approvals")
                .bodyValue(notice)
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}
