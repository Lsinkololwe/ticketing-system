package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;

/**
 * Outcome of ensuring an account. {@code status == PROVISIONING} means the workflow is still
 * running: {@code retryAfterSeconds} says when to ask again with the same proof.
 */
public record EnsureResult(String accountId, AccountState status, boolean isNew, String loginHandle,
                           Integer retryAfterSeconds) {
}
