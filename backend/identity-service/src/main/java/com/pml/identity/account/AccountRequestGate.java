package com.pml.identity.account;

import com.pml.identity.domain.model.User;
import com.pml.shared.error.TranslatedRefusal;
import reactor.core.publisher.Mono;

/**
 * The account gate every profile request passes: the account must be ACTIVE with nothing pending.
 *
 * <p>The database status decides, never Keycloak. A refused request gets a typed error and none of
 * the account's fields, so a suspended or half-created account cannot be read through a token that
 * was valid when it was issued.</p>
 */
public final class AccountRequestGate {

    private AccountRequestGate() {
    }

    public static Mono<User> admit(User account) {
        return AccountGuard.requestRefusal(account)
                .<Mono<User>>map(code -> Mono.error(new TranslatedRefusal(code, "account refused at the request gate")))
                .orElseGet(() -> Mono.just(account));
    }
}
