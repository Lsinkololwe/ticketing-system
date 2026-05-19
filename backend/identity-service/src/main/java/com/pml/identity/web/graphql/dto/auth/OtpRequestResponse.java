package com.pml.identity.web.graphql.dto.auth;

/**
 * Response for OTP request mutation.
 */
public record OtpRequestResponse(
        boolean success,
        String message,
        int expiresIn
) {}
