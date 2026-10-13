package com.pml.identity.auth.web;

import com.pml.identity.account.AccountEnsurer;
import com.pml.identity.account.AccountGuard;
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
 * The step after a buyer proved control of a contact: turn the proof into an account that can sign
 * in.
 *
 * <p>The proof is consumed, the account that owns the contact is found or created, and only for an
 * ACTIVE account, when the caller asked for it, a single-use login handle is issued. A buyer whose
 * account is still being set up gets 202 and is told when to ask again with the same proof;
 * repeating the call with the same proof is safe and never creates a second account (the workflow id
 * decides), and may issue a fresh handle. A suspended account, an account being merged and an
 * account that is no longer active are refused, each with its own code. Which code a state gets is
 * decided in one place, the account guard; this controller only asks it.</p>
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
            case SUSPENDED, MERGED, DELETED -> Mono.error(refusalFor(status));
        };
    }

    /** The refusal the account guard assigns to this state; the guard is the only place that decides. */
    private static TranslatedRefusal refusalFor(AccountState state) {
        ErrorCode code = AccountGuard.refusal(state, null)
                .orElseThrow(() -> new IllegalStateException("no refusal for state " + state));
        if (code == ErrorCode.ACCOUNT_NOT_ACTIVE) {
            return new TranslatedRefusal(code, "account is not active", Map.of("currentStatus", state.name()));
        }
        return new TranslatedRefusal(code, "account refused: " + state);
    }
}
