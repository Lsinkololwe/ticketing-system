package com.pml.identity.authorization;

/**
 * The step of the resolution order that produced a decision, and whether it allowed or refused.
 * The order is: platform role, event grant, organization membership, the member's denials, the
 * member's custom and role permissions, and the default, which is a refusal.
 */
public enum DecisionStep {

    /** A platform role grants the permission outright. */
    PLATFORM_ROLE(true),
    /** An active grant on the event includes the permission through its event role. */
    GRANT_ROLE(true),
    /** An active grant on the event carries the permission as a custom addition. */
    GRANT_CUSTOM(true),
    /** An active grant exists and does not include the permission; resolution ends here. */
    GRANT_EXHAUSTIVE(false),
    /** The member's role, under the organization's switches, includes the permission. */
    MEMBER_ROLE(true),
    /** The member carries the permission as a custom addition. */
    MEMBER_CUSTOM(true),
    /** The member's denial removes a permission the role or a custom addition would give. */
    MEMBER_DENIED(false),
    /** The caller is a member and nothing allowed the permission. */
    DEFAULT(false),
    /** The organization's lifecycle status refuses what the member's role allows. */
    ORGANIZATION_STATUS(false),
    NOT_A_MEMBER(false),
    ORGANIZATION_UNKNOWN(false),
    /** No active grant decided and no organization was named to fall back on. */
    NO_ORGANIZATION(false),
    PERMISSION_UNKNOWN(false);

    private final boolean allows;

    DecisionStep(boolean allows) {
        this.allows = allows;
    }

    /** Whether a decision produced by this step lets the action proceed. */
    public boolean allows() {
        return allows;
    }
}
