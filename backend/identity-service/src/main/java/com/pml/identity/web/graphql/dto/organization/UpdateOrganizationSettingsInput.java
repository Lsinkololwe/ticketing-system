package com.pml.identity.web.graphql.dto.organization;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Changes to an organization's settings. Every field is optional; a null field leaves the stored
 * value as it is. Field names match {@code UpdateOrganizationSettingsInput} in the schema, which is
 * how the GraphQL input is bound to this record.
 */
public record UpdateOrganizationSettingsInput(
        @Pattern(regexp = "PUBLIC|PRIVATE|UNLISTED", message = "defaultEventVisibility must be PUBLIC, PRIVATE or UNLISTED")
        String defaultEventVisibility,
        Boolean requireEventApproval,
        Boolean allowMembersToInvite,
        Boolean inviteRequiresApproval,
        @Min(1) @Max(10_000)
        Integer maxTeamMembers,
        Boolean managersCanViewFinancials,
        Boolean adminsCanRequestPayouts,
        Boolean notifyOwnerOnMemberJoin,
        Boolean notifyOwnerOnEventCreated,
        Boolean notifyOwnerOnPayoutRequest
) {}
