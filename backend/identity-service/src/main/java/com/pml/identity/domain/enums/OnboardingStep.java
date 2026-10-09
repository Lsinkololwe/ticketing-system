package com.pml.identity.domain.enums;

/**
 * The six steps of an organization's approval, in the order they run.
 *
 * <p>{@link #marker()} is the value written to {@code identity_organizations.approvalSagaStep}
 * when the step completes; compensation writes {@code 0}.
 */
public enum OnboardingStep {

    ACTIVATE(1, "activate the organization"),
    OWNER_MEMBERSHIP(2, "create the OWNER membership"),
    USER_TYPE(3, "make the owner an ORGANIZER"),
    REALM_ROLE(4, "grant the ORGANIZER realm role"),
    GROUP_TREE(5, "create the group tree and add the owner"),
    APPROVED(6, "stage identity.OrganizationApproved");

    private final int marker;
    private final String description;

    OnboardingStep(int marker, String description) {
        this.marker = marker;
        this.description = description;
    }

    public int marker() {
        return marker;
    }

    public String description() {
        return description;
    }

    /**
     * The id of the membership row step 2 creates when the owner has none.
     *
     * <p>Deterministic, so a retried step finds the row it made, and compensation removes that row
     * and never an owner membership that existed before the approval.
     */
    public static String ownerMembershipId(String organizationId) {
        // A 24-hex string: the collection's validator types _id as an ObjectId, and Spring Data
        // converts a hex id to one. Derived from the organization id so it stays deterministic.
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("approval-owner-" + organizationId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
