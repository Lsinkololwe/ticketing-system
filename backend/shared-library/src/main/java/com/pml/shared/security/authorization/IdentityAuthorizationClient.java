package com.pml.shared.security.authorization;

import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.dto.authorization.BatchAuthorizationRequest;
import com.pml.shared.dto.authorization.BatchAuthorizationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The one way catalog and booking ask identity who may do what, over {@code /api/internal/authorization/*}.
 *
 * <p>Identity is the authority for organization and event permissions. This client never guesses:
 * an answer it cannot get (timeout, 5xx, a body it cannot read) is a refusal, and a batch is refused
 * entry by entry, never as an empty list a caller could read as "nothing to check".</p>
 */
public class IdentityAuthorizationClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityAuthorizationClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String UNAVAILABLE = "Authorization service unavailable";

    private final WebClient identity;

    public IdentityAuthorizationClient(WebClient identity) {
        this.identity = identity;
    }

    /** One decision. Any failure to obtain it is a refusal. */
    public Mono<AuthorizationResult> check(AuthorizationRequest request) {
        return identity.post().uri("/api/internal/authorization/check").bodyValue(request)
                .exchangeToMono(response -> response.bodyToMono(AuthorizationResult.class))
                .timeout(TIMEOUT)
                .switchIfEmpty(Mono.fromSupplier(() -> AuthorizationResult.denied(UNAVAILABLE)))
                .onErrorResume(error -> {
                    log.error("Authorization check failed (denying): {}", error.toString());
                    return Mono.just(AuthorizationResult.denied(UNAVAILABLE));
                });
    }

    /**
     * Many decisions in one call, answered in the order asked. More than
     * {@link BatchAuthorizationRequest#MAX_CHECKS} are split into calls of that size.
     */
    public Mono<List<AuthorizationResult>> checkMany(List<AuthorizationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Mono.just(List.of());
        }
        List<List<AuthorizationRequest>> chunks = new ArrayList<>();
        for (int from = 0; from < requests.size(); from += BatchAuthorizationRequest.MAX_CHECKS) {
            chunks.add(requests.subList(from, Math.min(requests.size(), from + BatchAuthorizationRequest.MAX_CHECKS)));
        }
        return Flux.fromIterable(chunks).concatMap(this::oneBatch)
                .collectList()
                .map(parts -> parts.stream().flatMap(List::stream).toList());
    }

    private Mono<List<AuthorizationResult>> oneBatch(List<AuthorizationRequest> chunk) {
        return identity.post().uri("/api/internal/authorization/check-many")
                .bodyValue(new BatchAuthorizationRequest(chunk))
                .retrieve().bodyToMono(BatchAuthorizationResponse.class)
                .timeout(TIMEOUT)
                .map(BatchAuthorizationResponse::results)
                .filter(results -> results != null && results.size() == chunk.size())
                .switchIfEmpty(Mono.fromSupplier(() -> denyAll(chunk.size())))
                .onErrorResume(error -> {
                    log.error("Batch authorization failed (denying all {}): {}", chunk.size(), error.toString());
                    return Mono.just(denyAll(chunk.size()));
                });
    }

    private static List<AuthorizationResult> denyAll(int count) {
        List<AuthorizationResult> denied = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            denied.add(AuthorizationResult.denied(UNAVAILABLE));
        }
        return denied;
    }

    /** Access to one event by a person's grant, or by membership of its organization. */
    public Mono<AuthorizationResult> eventAccess(String userId, String eventId, String organizationId, String permission) {
        return identity.get().uri(uri -> {
                    var builder = uri.path("/api/internal/authorization/event-access")
                            .queryParam("userId", userId).queryParam("eventId", eventId)
                            .queryParam("permission", permission);
                    if (organizationId != null) {
                        builder.queryParam("organizationId", organizationId);
                    }
                    return builder.build();
                })
                .exchangeToMono(response -> response.bodyToMono(AuthorizationResult.class))
                .timeout(TIMEOUT)
                .switchIfEmpty(Mono.fromSupplier(() -> AuthorizationResult.denied(UNAVAILABLE)))
                .onErrorResume(error -> Mono.just(AuthorizationResult.denied(UNAVAILABLE)));
    }

    /** Whether two people belong to one organization; "no" when identity cannot say. */
    public Mono<SharedOrganization> sameOrganization(String requestingUserId, String targetOrganizerId) {
        return identity.get().uri(uri -> uri.path("/api/internal/authorization/check-same-organization")
                        .queryParam("requestingUserId", requestingUserId)
                        .queryParam("targetOrganizerId", targetOrganizerId).build())
                .retrieve().bodyToMono(SharedOrganization.class)
                .timeout(TIMEOUT)
                .onErrorResume(error -> Mono.just(new SharedOrganization(false, null)));
    }

    /**
     * The organizations a person belongs to. An outage is an error, not an empty list: "belongs to
     * nothing" and "identity did not answer" must stay different, or a dashboard scoped by this answer
     * reads an outage as "you have no money".
     */
    public Mono<UserOrganizations> userOrganizations(String userId) {
        return identity.get().uri(uri -> uri.path("/api/internal/authorization/user-organizations")
                        .queryParam("userId", userId).build())
                .retrieve().bodyToMono(UserOrganizations.class)
                .timeout(TIMEOUT);
    }

    /** The organization's display name, or empty when none is on file. Never used for a decision. */
    public Mono<String> organizationName(String organizationId) {
        return identity.get().uri(uri -> uri.path("/api/internal/authorization/organization-name")
                        .queryParam("organizationId", organizationId).build())
                .retrieve().bodyToMono(OrganizationName.class)
                .timeout(TIMEOUT)
                .map(OrganizationName::name)
                .onErrorResume(error -> Mono.empty());
    }

    /** The digest of the permission catalogue identity was built with. */
    public Mono<String> catalogueFingerprint() {
        return identity.get().uri("/api/internal/authorization/catalogue-fingerprint")
                .retrieve().bodyToMono(java.util.Map.class)
                .timeout(TIMEOUT)
                .map(body -> String.valueOf(body.get("fingerprint")));
    }

    public record SharedOrganization(boolean sharesOrganization, String sharedOrganizationId) {
    }

    public record UserOrganizations(List<Membership> organizations) {
    }

    public record Membership(String organizationId, String role, boolean isActive) {
    }

    public record OrganizationName(String name) {
    }
}
