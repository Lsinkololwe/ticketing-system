package com.pml.identity.web.graphql.dto.auth;

/**
 * GraphQL input for user registration.
 *
 * All new users are automatically assigned the CUSTOMER role by default.
 * Additional roles can be granted after registration through the admin panel.
 */
public record RegisterInput(
    String username,
    String email,
    String password,
    String firstName,
    String lastName,
    String phoneNumber
) {}
