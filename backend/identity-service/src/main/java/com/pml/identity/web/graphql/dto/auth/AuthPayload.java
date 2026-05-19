package com.pml.identity.web.graphql.dto.auth;

import com.pml.identity.domain.model.User;

/**
 * GraphQL response for authentication operations (login, refreshToken).
 * Contains JWT tokens and user information.
 */
public record AuthPayload(
    String accessToken,
    String refreshToken,
    int expiresIn,
    User user
) {}
