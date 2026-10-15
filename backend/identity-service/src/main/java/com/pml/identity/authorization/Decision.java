package com.pml.identity.authorization;

/**
 * A decision and what produced it. {@code role} is the member's organization role or the grant's
 * event role (as a name) when the step turned on one; {@code organizationId} and
 * {@code organizationStatus} are set when an organization was consulted.
 */
public record Decision(DecisionStep step, String role, String organizationId, String organizationStatus) {

    public boolean allowed() {
        return step.allows();
    }

    public static Decision of(DecisionStep step) {
        return new Decision(step, null, null, null);
    }
}
