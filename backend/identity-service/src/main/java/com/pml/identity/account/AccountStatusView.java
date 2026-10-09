package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;

/** What the app callback and booking need to know about an account (CONTRACT 4.5). */
public record AccountStatusView(String accountId, AccountState status, String keycloakUserId) {
}
