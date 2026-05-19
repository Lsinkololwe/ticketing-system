package com.pml.identity.web.graphql.dto.auth;

import com.pml.identity.domain.model.User;

/**
 * Response for phone OTP verification.
 */
public record PhoneAuthPayload(
        boolean success,
        String message,
        String accessToken,
        String refreshToken,
        User user
) {}
