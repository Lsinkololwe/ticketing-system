package com.pml.identity.web.graphql.dto.user;

import com.pml.identity.domain.model.User;

/**
 * Response type for user mutation operations.
 *
 * OWASP Compliance:
 * - A01:2021 Broken Access Control: Response includes user data only when operation succeeds
 * - A09:2021 Security Logging: Success/failure status enables audit logging
 */
public record UserMutationResponse(
        boolean success,
        String message,
        User user
) {
    /**
     * Create a successful response with the updated user.
     */
    public static UserMutationResponse success(User user) {
        return new UserMutationResponse(true, null, user);
    }

    /**
     * Create a successful response with a message.
     */
    public static UserMutationResponse success(User user, String message) {
        return new UserMutationResponse(true, message, user);
    }

    /**
     * Create a failure response with an error message.
     */
    public static UserMutationResponse failure(String message) {
        return new UserMutationResponse(false, message, null);
    }
}
