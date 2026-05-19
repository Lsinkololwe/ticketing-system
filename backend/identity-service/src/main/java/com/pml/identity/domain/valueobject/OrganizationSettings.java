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
     * Whether managers can request payouts
     */
    @Builder.Default
    private boolean managersCanRequestPayouts = false;

    /**
     * Whether marketers can view financial data
     */
    @Builder.Default
    private boolean marketersCanViewFinancials = false;

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
