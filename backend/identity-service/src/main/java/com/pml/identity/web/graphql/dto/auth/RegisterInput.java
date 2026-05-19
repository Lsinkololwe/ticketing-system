package com.pml.identity.web.graphql.dto.auth;

import com.pml.shared.constants.UserType;

/**
 * GraphQL input for user registration.
 */
public record RegisterInput(
    String username,
    String email,
    String password,
    String firstName,
    String lastName,
    String phoneNumber,
    UserType userType
) {}
