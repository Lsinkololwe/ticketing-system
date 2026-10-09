package com.pml.identity.web.graphql.dto.platform;

public record PlatformRulesApproval(
        int slaHours,
        int warnHours,
        boolean autoEscalation,
        int escalationDelayHours,
        boolean requireCommentsOnRejection,
        boolean requireCommentsOnChangesRequested,
        boolean allowSelfApproval
) {}
