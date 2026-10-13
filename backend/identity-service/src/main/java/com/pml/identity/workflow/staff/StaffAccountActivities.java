package com.pml.identity.workflow.staff;

import io.temporal.activity.ActivityInterface;

/**
 * The writes behind {@link StaffAccountWorkflow}. Every one is repeatable: a retry after a crash finds
 * its work done and returns the same answer. Inputs are ids and the sealed request, never a readable
 * address or password.
 */
@ActivityInterface(namePrefix = "StaffAccount")
public interface StaffAccountActivities {

    /**
     * Writes the record in PROVISIONING with the requested role, or finds the one an earlier attempt left;
     * returns its id, and whether it is already a finished account of this same request.
     */
    Claimed claim(String sealed);

    /** Creates the person in the staff realm unable to sign in, or reads back the user an earlier attempt made. */
    KeycloakUser createInKeycloak(String accountId, String sealed);

    /** Gives the record the Keycloak id as its own; returns the id the record now has. */
    String link(Link link);

    /** Grants the requested staff role in the staff realm and on the record. */
    void grantRole(String accountId);

    /** Opens the sign-in and makes the account ACTIVE. */
    void activate(String accountId);

    /** Removes the Keycloak user this process made, then the unfinished record. Never touches an ACTIVE account. */
    void compensate(String accountId);

    /** Best effort. */
    void notifyCreated(String accountId);

    record Claimed(String accountId, boolean finished) {
    }

    record KeycloakUser(String id, boolean createdHere) {
    }

    record Link(String accountId, String keycloakUserId, boolean createdHere) {
    }
}
