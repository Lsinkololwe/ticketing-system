package com.pml.identity.web.graphql.dto;

import com.pml.identity.domain.valueobject.OrganizationRole;

import java.time.Instant;

/**
 * What the holder of an invitation link is shown before they accept.
 *
 * <p>Five fields, and the narrowness is deliberate. An invitation token is a bearer
 * credential that arrives by email or WhatsApp and is then forwarded, screenshotted and pasted
 * into group chats — so everything reachable by the token is reachable by whoever the link
 * reached. This carries only what a stranger needs in order to decide whether to accept.
 *
 * <p>What is deliberately absent is the point of the type: the token itself, the invitee's
 * {@code email} and {@code phoneNumber}, the inviter's user id, the personal {@code message}, and
 * the invitation's own id. Returning the invitation document instead would disclose the invitee's
 * contact details to anyone the link was forwarded to.
 *
 * @param organizationName    which organization is inviting
 * @param organizationLogoUrl so the page is recognisable; nullable, not every organization has one
 * @param proposedRole        what the invitee is being offered
 * @param inviterDisplayName  a name, never the inviter's id or email
 * @param expiresAt           when the link stops working
 */
public record InvitationPreview(
    String organizationName,
    String organizationLogoUrl,
    OrganizationRole proposedRole,
    String inviterDisplayName,
    Instant expiresAt
) {}
