package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.valueobject.EventRole;

import java.time.Instant;
import java.util.Set;

/**
 * Input for granting event access.
 */
public record EventAccessGrantInput(
        String eventId,
        EventRole role,
        Set<String> customPermissions,
        String reason,
        Instant expiresAt
) {}
