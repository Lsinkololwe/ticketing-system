package com.pml.shared.security.revocation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * The durable half for services that do <em>not</em> own the revocation data.
 *
 * <h2>Who uses this</h2>
 * <p>identity-service owns the {@code token_revocations} collection and binds
 * {@link DurableRevocationStore} directly to MongoDB. Booking, catalog and any service added
 * later bind it here, reaching the same system of record over HTTP instead of holding a second
 * copy. Being a separate process over a separate protocol, its availability is independent of
 * Redis.</p>
 *
 * <h2>Error handling</h2>
 * <p>Errors propagate so that {@link CachedRevocationCheck} can report
 * {@link RevocationDecision#UNKNOWN} rather than an unverified {@code ACTIVE}.</p>
 *
 * <h2>Wiring</h2>
 * <pre>{@code
 * @Bean
 * DurableRevocationStore durableRevocationStore(WebClient.Builder builder,
 *                                               ReactiveOAuth2AuthorizedClientManager clients) {
 *     return new HttpDurableRevocationStore(builder
 *             .baseUrl(identityServiceUrl)
 *             .filter(new ServerOAuth2AuthorizedClientExchangeFilterFunction(clients))
 *             .build());
 * }
 * }</pre>
 */
@RequiredArgsConstructor
public class HttpDurableRevocationStore implements DurableRevocationStore {

    /** Request body for {@code POST /api/internal/revocations/check}. */
    public record CheckRequest(List<Entry> identifiers) {
        public record Entry(String type, String value) {
        }
    }

    /** Response body for {@code POST /api/internal/revocations/check}. */
    public record CheckResponse(RevocationDecision decision) {
    }

    private final WebClient client;

    @Override
    public Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers) {
        CheckRequest request = new CheckRequest(identifiers.stream()
                .map(id -> new CheckRequest.Entry(id.type().name(), id.value()))
                .toList());

        return client.post()
                .uri("/api/internal/revocations/check")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CheckResponse.class)
                .map(CheckResponse::decision)
                // A malformed or empty body is not evidence the token is fine.
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "identity-service returned no revocation decision")));
    }

    @Override
    public Mono<Void> ping() {
        return client.get()
                .uri("/api/internal/revocations/health")
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    @Override
    public String name() {
        return "identity-service";
    }
}
