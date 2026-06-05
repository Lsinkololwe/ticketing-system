package com.pml.identity.web.graphql.dto.auth;

import java.util.Set;

/**
 * GraphQL response for token validation.
 * Contains token validity status and extracted claims.
 */
public record TokenValidation(
    boolean valid,
    String userId,
    String email,
    Set<String> roles
) {}
