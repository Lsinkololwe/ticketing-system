package com.pml.identity.account;

import reactor.core.publisher.Mono;

/** Reads an account's lifecycle state (CONTRACT 12). */
public interface AccountStatusLookup {

    /** Empty when no such account exists. */
    Mono<AccountStatusView> byAccountId(String accountId);
}
