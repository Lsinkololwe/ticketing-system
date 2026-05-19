package com.pml.identity.web.graphql.dto.organization;

/**
 * Input for updating organization settings.
 */
public record UpdateOrganizationSettingsInput(
        Boolean allowMemberInvites,
        Boolean requireApprovalForEvents,
        Boolean notifyOnNewMember,
        Boolean notifyOnTicketSale,
        String defaultEventVisibility
) {}
