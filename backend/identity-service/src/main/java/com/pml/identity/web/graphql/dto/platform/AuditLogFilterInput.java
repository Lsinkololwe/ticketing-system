package com.pml.identity.web.graphql.dto.platform;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AuditLogFilterInput(
        Instant from,
        Instant to,
        @Size(max = 100) String action,
        @Size(max = 100) String actorId,
        @Size(max = 100) String resourceType,
        @Size(max = 100) String resourceId,
        @Size(max = 50) String status,
        Boolean includeAccountEvents,
        @Size(max = 80) String service,
        @Size(max = 100) String reason
) {}
