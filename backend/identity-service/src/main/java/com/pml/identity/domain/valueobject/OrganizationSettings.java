package com.pml.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Organization settings - embedded document within Organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationSettings {

    /**
     * Default visibility for new events: PUBLIC, PRIVATE, UNLISTED
     */
    @Builder.Default
    private String defaultEventVisibility = "PUBLIC";

    /**
     * Whether events need owner/admin approval before publishing
     */
    @Builder.Default
    private boolean requireEventApproval = false;

    /**
     * Whether non-owners can invite members
     */
    @Builder.Default
    private boolean allowMembersToInvite = false;

    /**
     * Whether invites need owner approval
     */
    @Builder.Default
    private boolean inviteRequiresApproval = false;

    /**
     * Maximum team members (null = unlimited)
     */
    private Integer maxTeamMembers;

    /**
     * Whether members with the MANAGER role see revenue, escrow and commission figures.
     * Off for a new organization; the owner turns it on.
     */
    @Builder.Default
    private boolean managersCanViewFinancials = false;

    /**
     * Whether members with the ADMIN role may request payouts. The owner always may.
     * Off for a new organization; the owner turns it on.
     */
    @Builder.Default
    private boolean adminsCanRequestPayouts = false;

    /**
     * Notify owner when a member joins
     */
    @Builder.Default
    private boolean notifyOwnerOnMemberJoin = true;

    /**
     * Notify owner when an event is created
     */
    @Builder.Default
    private boolean notifyOwnerOnEventCreated = true;

    /**
     * Notify owner when a payout is requested
     */
    @Builder.Default
    private boolean notifyOwnerOnPayoutRequest = true;
}
