package com.pml.identity.domain.enums;

/**
 * Lifecycle of an account (CONTRACT 2). Replaces the legacy {@link AccountStatus}, which stays on
 * the document until the cleanup migration.
 *
 * <p>Only {@link #ACTIVE} accounts get a login handle, may sign in, or may hold a reservation.</p>
 */
public enum AccountState {
    /** A contact is claimed and the Keycloak user is not finished. */
    PROVISIONING,
    /** The Keycloak user exists with its role and attributes, and is linked. */
    ACTIVE,
    SUSPENDED,
    /** Folded into another account; see {@code mergedInto}. */
    MERGED,
    DELETED
}
