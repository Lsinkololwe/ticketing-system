package com.pml.identity.security.revocation;

import java.util.Map;

/**
 * Why an access was cut. Recorded on every revocation and on its audit row, so "who removed
 * this session and for what" is answerable without reading free text.
 */
public enum RevocationTrigger {

    /** The person, or Keycloak on their behalf, ended a session. */
    LOGOUT("The person signed out of the session"),
    CREDENTIAL_CHANGE("A password or credential of the account was changed"),
    CONTACT_CHANGED("The sign-in contact of the account was changed"),
    ACCOUNT_SUSPENDED("The account was suspended"),
    ACCOUNT_ERASED("The account was deleted or erased"),
    MEMBER_REMOVED("The member was removed from an organization"),
    GRANT_REVOKED("An event access grant of the member was revoked"),
    ORGANIZATION_SUSPENDED("The organization the member belongs to was suspended"),
    /** An administrator cut the access by hand; the written reason is mandatory. */
    ADMIN_REVOKE("An administrator revoked the access"),
    SUSPECTED_COMPROMISE("The access was cut on suspicion of compromise");

    /** The shortest written reason a person may give for a manual revocation. */
    public static final int MIN_WRITTEN_REASON = 20;

    /** Reason codes the web apps and Keycloak listener send, mapped to what they mean. */
    private static final Map<String, RevocationTrigger> CODES = Map.ofEntries(
            Map.entry("logout", LOGOUT),
            Map.entry("user_logout", LOGOUT),
            Map.entry("keycloak-logout", LOGOUT),
            Map.entry("keycloak-refresh-token-error", LOGOUT),
            Map.entry("account-suspended", ACCOUNT_SUSPENDED),
            Map.entry("account-deleted", ACCOUNT_ERASED),
            Map.entry("credential_change", CREDENTIAL_CHANGE),
            Map.entry("contact_change", CONTACT_CHANGED),
            Map.entry("member_removed", MEMBER_REMOVED),
            Map.entry("grant_revoked", GRANT_REVOKED),
            Map.entry("organization_suspended", ORGANIZATION_SUSPENDED));

    private final String description;

    RevocationTrigger(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** True for the triggers a person types a reason for. */
    public boolean requiresWrittenReason() {
        return this == ADMIN_REVOKE || this == SUSPECTED_COMPROMISE;
    }

    /** The trigger a machine reason code stands for; an unknown code is a manual revocation. */
    public static RevocationTrigger fromCode(String code) {
        if (code == null) {
            return ADMIN_REVOKE;
        }
        return CODES.getOrDefault(code.trim().toLowerCase(java.util.Locale.ROOT), ADMIN_REVOKE);
    }
}
