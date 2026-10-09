package com.pml.identity.auth.web;

import com.pml.identity.account.AccountEnsurer;
import com.pml.identity.account.ConsentGrant;
import com.pml.identity.account.EnsureCommand;
import com.pml.identity.account.EnsureResult;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.auth.proof.LoginHandleService;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.domain.enums.AccountState;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/internal/auth/accounts/ensure} (CONTRACT 4.3, 13): consume the proof, find or
 * create the account, and - only for an ACTIVE account and {@code issueHandle=true} - issue the
 * login handle. Repeating the call with the same proof is idempotent (the workflow id decides) and
 * may issue a fresh handle.
 */
@RestController
@RequestMapping("/api/internal/auth/accounts")
public class InternalEnsureController {

    static final int DEFAULT_RETRY_AFTER_SECONDS = 2;

    private final ProofService proofs;
    private final AccountEnsurer ensurer;
    private final LoginHandleService handles;

    public InternalEnsureController(ProofService proofs, AccountEnsurer ensurer, LoginHandleService handles) {
        this.proofs = proofs;
        this.ensurer = ensurer;
        this.handles = handles;
    }

    @PostMapping("/ensure")
    public Mono<ResponseEntity<Object>> ensure(@RequestBody Mono<AuthDtos.EnsureRequest> body) {
        return body.switchIfEmpty(Mono.error(InternalChallengeController.malformed()))
                .flatMap(request -> {
                    if (request.proof() == null || request.proof().isBlank()
                            || request.clientId() == null || request.clientId().isBlank()) {
                        return Mono.error(InternalChallengeController.malformed());
                    }
                    boolean issueHandle = Boolean.TRUE.equals(request.issueHandle());
                    return proofs.markConsumed(request.proof())
                            .flatMap(proof -> ensurer.ensure(command(request, proof, issueHandle))
                                    .flatMap(result -> respond(request.proof(), request.clientId(), issueHandle, result)));
                });
    }

    private static EnsureCommand command(AuthDtos.EnsureRequest request, ProofRecord proof, boolean issueHandle) {
        List<ConsentGrant> consents = request.consents() == null ? List.of()
                : request.consents().stream()
                .filter(c -> c != null && c.purpose() != null && c.version() != null)
                .map(c -> new ConsentGrant(c.purpose(), c.version()))
                .toList();
        return new EnsureCommand(proof.proofId(), proof.contactKey(), proof.type(), request.clientId(),
                issueHandle, request.displayName(), consents);
    }

    private Mono<ResponseEntity<Object>> respond(String proofId, String clientId, boolean issueHandle, EnsureResult result) {
        AccountState status = result.status();
        if (status == null) {
            return Mono.error(new IllegalStateException("ensure returned no status"));
        }
        return switch (status) {
            case ACTIVE -> {
                Mono<String> handle = issueHandle ? handles.issue(result.accountId(), clientId) : Mono.empty();
                yield proofs.recordAccount(proofId, result.accountId())
                        .then(handle.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
                        .map(issued -> ResponseEntity.<Object>ok(new AuthDtos.EnsureActiveResponse(
                                result.accountId(), AccountState.ACTIVE.name(), result.isNew(), issued.orElse(null))));
            }
            case PROVISIONING -> Mono.just(ResponseEntity.<Object>status(HttpStatus.ACCEPTED)
                    .body(new AuthDtos.EnsureProvisioningResponse(result.accountId(), AccountState.PROVISIONING.name(),
                            result.retryAfterSeconds() != null ? result.retryAfterSeconds() : DEFAULT_RETRY_AFTER_SECONDS)));
            case SUSPENDED -> Mono.error(new TranslatedRefusal(ErrorCode.ACCOUNT_SUSPENDED, "account suspended"));
            case MERGED -> Mono.error(new TranslatedRefusal(ErrorCode.ACCOUNT_MERGING, "account merged"));
            case DELETED -> Mono.error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "account deleted",
                    Map.of("currentStatus", AccountState.DELETED.name())));
        };
    }
}
