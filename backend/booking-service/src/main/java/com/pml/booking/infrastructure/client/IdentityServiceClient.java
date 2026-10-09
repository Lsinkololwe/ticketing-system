package com.pml.booking.infrastructure.client;

import com.pml.shared.security.InternalServiceWebClients;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
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
            InternalServiceWebClients clients,
            @Value("${services.identity.url:http://localhost:8083}") String identityServiceUrl) {
        this.webClient = clients.to(identityServiceUrl);
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
                .doOnError(e -> log.error("Failed to get user organizations: {}", e.getMessage()));
                // The error is NOT swallowed into an empty list. Doing so made an
                // identity-service outage indistinguishable from "this user
                // belongs to no organization" — and callers that scope money by
                // the result would then render an empty dashboard during an
                // outage, which reads as "you have no money" rather than as a
                // fault. Callers decide how to fail; they cannot decide if the
                // difference has already been erased.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CENTRALIZED AUTHORIZATION (OWASP A01:2021)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Delegate an authorization decision to identity-service, the single source of truth.
     *
     * <p>identity-service evaluates both entity-level questions — can the actor perform the
     * action (organization membership/role) and does the organization's lifecycle status permit
     * it. Used for money-movement gates such as payout requests, so a pending/unapproved
     * organization cannot initiate finance flows.</p>
     *
     * <p>Fails <b>closed</b>: if the check cannot be completed, the request is denied.</p>
     *
     * @param request authorization request (userId, requiredPermission, and organization/owner context)
     * @return the authorization result; denied on any transport/service error
     */
    public Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request) {
        log.debug("Checking authorization: permission={}, ownerId={}",
                request.getRequiredPermission(), request.getOrganizationOwnerId());

        return webClient.post()
                .uri("/api/internal/authorization/check")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthorizationResult.class)
                .onErrorResume(e -> {
                    log.error("Authorization check failed (denying by default): {}", e.getMessage());
                    return Mono.just(AuthorizationResult.denied("Authorization service unavailable"));
                });
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
            String role,
            boolean isActive
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // FINANCE LEAD ESCALATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /** The active finance leads' email addresses. An unreachable identity service is an error, so the escalating activity retries. */
    public reactor.core.publisher.Flux<com.pml.booking.infrastructure.client.dto.FinanceLeadContact> financeLeadContacts() {
        return webClient.get()
                .uri("/api/internal/finance-leads/contacts")
                .retrieve()
                .bodyToFlux(com.pml.booking.infrastructure.client.dto.FinanceLeadContact.class);
    }

    /** Asks identity to WhatsApp every finance lead; identity deduplicates on the discriminator, so a retry sends nothing new. */
    public Mono<Void> notifyFinanceLeads(String templateKey, String discriminator, String subjectId) {
        return webClient.post()
                .uri("/api/internal/finance-leads/notifications")
                .bodyValue(java.util.Map.of("templateKey", templateKey, "discriminator", discriminator, "subjectId", subjectId))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICATIONS AND CONTACT LOOKUP (identity owns contacts and delivery)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Asks identity to send one templated notification to one account's verified contact. Identity picks
     * the channel, deduplicates on {@code discriminator} (a retry sends nothing new) and answers with a
     * receipt that carries the destination masked.
     */
    public Mono<com.pml.booking.infrastructure.client.dto.NotificationReceipt> notifyUser(
            String templateKey, String discriminator, String userId, java.util.Map<String, Object> params) {
        return webClient.post()
                .uri("/api/internal/notifications/users")
                .bodyValue(java.util.Map.of("templateKey", templateKey, "discriminator", discriminator,
                        "userId", userId, "params", params))
                .retrieve()
                .bodyToMono(com.pml.booking.infrastructure.client.dto.NotificationReceipt.class);
    }

    /**
     * Asks identity to send one message to a set of accounts (the holders of an event's tickets). The
     * request carries ids only; identity resolves each account's verified contact and masks nothing it
     * returns beyond the count.
     */
    public Mono<com.pml.booking.infrastructure.client.dto.NotificationReceipt> notifyUsers(
            String templateKey, String discriminator, java.util.Collection<String> userIds,
            java.util.Map<String, Object> params) {
        return webClient.post()
                .uri("/api/internal/notifications/users/batch")
                .bodyValue(java.util.Map.of("templateKey", templateKey, "discriminator", discriminator,
                        "userIds", userIds, "params", params))
                .retrieve()
                .bodyToMono(com.pml.booking.infrastructure.client.dto.NotificationReceipt.class);
    }

    /**
     * Resolves a verified contact (a WhatsApp number or an email) to an account, or empty. Identity
     * answers with a display name reduced to first name and initial and the contact masked, never the
     * account's profile.
     */
    public Mono<com.pml.booking.infrastructure.client.dto.UserLookup> lookupByContact(String channel, String value) {
        return webClient.post()
                .uri("/api/internal/users/lookup")
                .bodyValue(java.util.Map.of("channel", channel, "value", value))
                .retrieve()
                .bodyToMono(com.pml.booking.infrastructure.client.dto.UserLookup.class)
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.NotFound.class,
                        unknown -> Mono.empty());
    }
}
