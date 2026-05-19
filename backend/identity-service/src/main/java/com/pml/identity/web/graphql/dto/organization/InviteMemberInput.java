package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.valueobject.OrganizationRole;

import java.util.List;

/**
 * Input for inviting a team member.
 */
public record InviteMemberInput(
        String email,
        String phoneNumber,
        String inviteeName,
        OrganizationRole role,
        String message,
        List<EventAccessGrantInput> eventAccessGrants
) {}
