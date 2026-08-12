package com.pml.identity.security.revocation;

import com.pml.shared.security.revocation.RevocationDecision;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * The one place a revocation can be created, and the durable read other services fall back to.
 *
 * <p>Routing every write through the owning service keeps a single writer and a single key
 * layout across the backend services and the frontend applications.</p>
 *
 * <h2>Failure semantics</h2>
 * <p>Write failures return 5xx rather than 200, so a caller can retry or warn the user instead
 * of assuming a revocation landed.</p>
 *
 * <p>Authorization is inherited from {@code SecurityConfig}: {@code /api/internal/**} requires an
 * internal scope or the service role.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/revocations")
@RequiredArgsConstructor
public class InternalRevocationController {

    private final MongoRevocationStore store;

    // =========================================================================
    // requests / responses
    // =========================================================================

    /** A revocation to create. */
    public record RevokeRequest(
            @NotNull RevocationType type,
            @NotBlank String value,
            @NotBlank String reason,
            String revokedBy) {
    }

    /**
     * Revokes everything identifying one sign-out at once.
     *
     * <p>The access token's {@code jti} covers the token in hand, {@code sid} covers every token
     * minted for that SSO session, and {@code sub} covers every token held by the user. A logout
     * supplies whichever of the three it knows.</p>
     */
    public record LogoutRequest(
            String jti,
            String sid,
            String sub,
            @NotBlank String reason,
            String revokedBy) {
    }

    public record RevocationResponse(String id, RevocationType type, String reason,
                                     Instant revokedAt, Instant expiresAt) {
        static RevocationResponse of(RevocationRecord record) {
            return new RevocationResponse(record.getId(), record.getType(), record.getReason(),
                    record.getRevokedAt(), record.getExpiresAt());
        }
    }

    public record LogoutResponse(boolean revoked, List<RevocationResponse> records) {
    }

    /** Identifiers to resolve. Mirrors {@code HttpDurableRevocationStore.CheckRequest}. */
    public record CheckRequest(@NotEmpty List<Entry> identifiers) {
        public record Entry(@NotNull RevocationType type, @NotBlank String value) {
        }
    }

    public record CheckResponse(RevocationDecision decision) {
    }

    // =========================================================================
    // endpoints
    // =========================================================================

    /**
     * Creates a single revocation.
     *
     * @return 201 with the persisted record; a 5xx if it could not be persisted
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
    public Mono<ResponseEntity<RevocationResponse>> revoke(@Valid @RequestBody RevokeRequest request) {
        return store.revoke(request.type(), request.value(), request.reason(),
                        request.revokedBy() == null ? "unknown" : request.revokedBy())
                .map(record -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(RevocationResponse.of(record)));
    }

    /**
     * Revokes a sign-out's token, session and (optionally) user in one call.
     *
     * <p>All supplied identifiers must persist; the call fails as a whole if any write does, so
     * a partial result is never reported as a completed sign-out.</p>
     */
    @PostMapping("/logout")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
    public Mono<ResponseEntity<LogoutResponse>> logout(@Valid @RequestBody LogoutRequest request) {
        List<RevocationIdentifier> identifiers =
                RevocationIdentifier.from(request.jti(), request.sid(), request.sub());

        if (identifiers.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new LogoutResponse(false, List.of())));
        }

        String revokedBy = request.revokedBy() == null ? "unknown" : request.revokedBy();

        return Flux.fromIterable(identifiers)
                .concatMap(identifier -> store.revoke(identifier.type(), identifier.value(),
                        request.reason(), revokedBy))
                .map(RevocationResponse::of)
                .collectList()
                .doOnSuccess(records -> log.info(
                        "[Revocation] Logout revoked {} identifier(s), reason={}",
                        records.size(), request.reason()))
                .map(records -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new LogoutResponse(true, records)));
    }

    /**
     * The durable read behind {@code HttpDurableRevocationStore}.
     *
     * <p>Errors surface as 5xx so the caller can distinguish "not revoked" from "could not
     * tell".</p>
     */
    @PostMapping("/check")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', "
            + "'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
    public Mono<CheckResponse> check(@Valid @RequestBody CheckRequest request) {
        List<RevocationIdentifier> identifiers = request.identifiers().stream()
                .map(entry -> new RevocationIdentifier(entry.type(), entry.value()))
                .toList();

        return store.check(identifiers).map(CheckResponse::new);
    }

    /** Liveness of the durable store, for {@code HttpDurableRevocationStore.ping()}. */
    @GetMapping("/health")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', "
            + "'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
    public Mono<ResponseEntity<Void>> health() {
        return store.ping().thenReturn(ResponseEntity.noContent().build());
    }
}
