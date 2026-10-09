package com.pml.identity.workflow.onboarding;

import io.temporal.activity.ActivityInterface;

/**
 * Steps 4 and 5, in Keycloak, and their compensations.
 *
 * <p>Each write is idempotent at Keycloak — granting a held role or joining a joined group changes
 * nothing — so a retried attempt is harmless. Each successful step raises the step marker.
 */
@ActivityInterface(namePrefix = "OnboardingKeycloak")
public interface OnboardingKeycloakActivities {

    /** Step 4. */
    void grantOrganizerRole(String organizationId);

    /** Compensation for step 4 · only a role this approval granted. */
    void revokeOrganizerRole(String organizationId);

    /** Step 5 · the tree is created where missing and the owner joins {@code owners}; returns the group id. */
    String ensureGroupTree(String organizationId);

    /** Compensation for step 5 · the owner leaves {@code owners}; the tree stays, harmless and reusable. */
    void removeOwnerFromGroups(String organizationId);
}
