package com.pml.identity.infrastructure.temporal;

/**
 * Workflow ids are business ids.
 *
 * <p>One function per workflow type, so no call site builds an id by hand, and a repeated start
 * reaches the same execution.
 */
public final class WorkflowIds {

    private WorkflowIds() {
    }

    public static String organizerOnboarding(String organizationId) {
        return "org-onboarding/" + require(organizationId);
    }

    public static String ownershipTransfer(String transferId) {
        return "ownership/" + require(transferId);
    }

    public static String userSync(String keycloakUserId) {
        return "user-sync/" + require(keycloakUserId);
    }

    public static String reminder(String reminderId) {
        return "reminder/" + require(reminderId);
    }

    public static String notification(String deduplicationKey) {
        return "notify/" + require(deduplicationKey);
    }

    /**
     * The Keycloak group-mirror repair a Schedule starts. One id: the Schedule appends its own
     * timestamp to each run, and overlap policy SKIP keeps a slow pass from doubling up.
     */
    public static String groupMirrorRepair() {
        return "group-mirror-repair";
    }

    /**
     * The one account-ensure execution for a contact: {@code account-ensure/{contactKey}}.
     *
     * <p>The key is the keyed hash of the contact ({@code ContactHasher}), 64 lower-case hex
     * characters. Anything else is refused, so a raw email or phone number can never become a
     * workflow id and so appear in Temporal's history, visibility store and UI (D-46).
     */
    public static String accountEnsure(String contactKey) {
        return "account-ensure/" + requireContactKey(contactKey);
    }

    /** The one open contact change of an account: {@code contact-change/{accountId}}. The account id is opaque, never a contact. */
    public static String contactChange(String accountId) {
        return "contact-change/" + require(accountId);
    }

    private static final java.util.regex.Pattern CONTACT_KEY = java.util.regex.Pattern.compile("[0-9a-f]{64}");

    private static String requireContactKey(String contactKey) {
        if (contactKey == null || !CONTACT_KEY.matcher(contactKey).matches()) {
            throw new IllegalArgumentException("a contact key is the 64-character keyed hash of a contact");
        }
        return contactKey;
    }

    private static String require(String part) {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("a workflow id needs its business identifier");
        }
        return part;
    }
}
