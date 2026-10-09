package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.valueobject.OrganizationRole;

import java.util.Set;

/**
 * Input for updating a member's role and permissions.
 */
public record UpdateMemberRoleInput(
        OrganizationRole newRole,
        Set<String> customPermissions,
        Set<String> deniedPermissions
) {}
