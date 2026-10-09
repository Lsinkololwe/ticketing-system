package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.valueobject.EventRole;

import java.time.Instant;
import java.util.Set;

/**
 * One person in a bulk event-access grant; the event comes from the mutation, not from this input.
 */
public record BulkEventAccessGrantInput(
        @jakarta.validation.constraints.NotBlank String userId,
        EventRole role,
        Set<String> customPermissions,
        String reason,
        Instant expiresAt
) {}
