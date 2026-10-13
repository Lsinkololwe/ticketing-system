package com.pml.identity.account;

import com.pml.identity.domain.model.User;

/**
 * A staff account (the GraphQL field {@code user}) together with the one-time password issued for it by the call that is answering.
 *
 * <p>{@code temporaryPassword} is the only place the value ever leaves the process: it is present on the
 * response of the call that created the account (or reset its password) and is null on every other
 * answer, such as a repeat of a request that had already finished. {@link #toString()} leaves it out, so
 * the object in a log line, an assertion message or an exception cannot disclose it.</p>
 */
public record StaffAccountPassword(User user, String temporaryPassword) {

    @Override
    public String toString() {
        return "StaffAccountPassword[account=" + (user == null ? null : user.getId())
                + ", temporaryPassword=" + (temporaryPassword == null ? "none" : "issued") + "]";
    }
}
