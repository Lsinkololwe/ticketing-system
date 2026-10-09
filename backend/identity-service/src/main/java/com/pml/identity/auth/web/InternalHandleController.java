package com.pml.identity.auth.web;

import com.pml.identity.account.AccountStatusLookup;
import com.pml.identity.auth.proof.LoginHandleService;
import com.pml.identity.domain.enums.AccountState;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Handle redemption (CONTRACT 4.4) and account status (CONTRACT 4.5). */
@RestController
@RequestMapping("/api/internal/auth")
public class InternalHandleController {

    private final LoginHandleService handles;
    private final AccountStatusLookup statuses;

    public InternalHandleController(LoginHandleService handles, AccountStatusLookup statuses) {
        this.handles = handles;
        this.statuses = statuses;
    }

    @PostMapping("/handles/redeem")
    public Mono<ResponseEntity<AuthDtos.RedeemResponse>> redeem(@RequestBody Mono<AuthDtos.RedeemRequest> body) {
        return body.switchIfEmpty(Mono.error(InternalChallengeController.malformed()))
                .flatMap(request -> handles.redeem(request.handle(), request.clientId()))
                .flatMap(redeemed -> statuses.byAccountId(redeemed.accountId())
                        .switchIfEmpty(Mono.error(() -> notActive(null)))
                        .flatMap(view -> view.status() == AccountState.ACTIVE
                                ? Mono.just(ResponseEntity.ok(new AuthDtos.RedeemResponse(view.accountId())))
                                : Mono.error(notActive(view.status()))));
    }

    @GetMapping("/accounts/{accountId}/status")
    public Mono<ResponseEntity<AuthDtos.StatusResponse>> status(@PathVariable String accountId) {
        return statuses.byAccountId(accountId)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "no such account")))
                .map(view -> ResponseEntity.ok(new AuthDtos.StatusResponse(view.accountId(),
                        view.status().name(), view.keycloakUserId())));
    }

    private static TranslatedRefusal notActive(AccountState current) {
        return new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "account is not active",
                current == null ? Map.of() : Map.of("currentStatus", current.name()));
    }
}
