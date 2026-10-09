package com.pml.booking.domain.enums;

/**
 * The actions that need two people: one proposes, a different one confirms. Each either moves platform
 * money or destroys a record, which is the line between these and the single-operator recovery tools.
 */
public enum RecoveryAction {

    /** Apply the provider's confirmed outcome to payment attempts stuck short of fulfilment. SUPER_ADMIN only. */
    FORCE_COMPLETE_PAYMENT_ATTEMPTS("PAYMENT_ATTEMPT", "SUPER_ADMIN"),
    /** Write an unrecoverable chargeback loss off as bad debt. */
    WRITE_OFF_CHARGEBACK("CHARGEBACK", "FINANCE"),
    /** Move money between the platform's own accounts above the single-approver limit. */
    TRANSFER_PLATFORM_FUNDS("PLATFORM_ACCOUNT", "FINANCE");

    private final String subjectType;
    private final String requiredRole;

    RecoveryAction(String subjectType, String requiredRole) {
        this.subjectType = subjectType;
        this.requiredRole = requiredRole;
    }

    public String subjectType() {
        return subjectType;
    }

    /** The role both the proposer and the confirmer must hold; ADMIN and SUPER_ADMIN satisfy FINANCE. */
    public String requiredRole() {
        return requiredRole;
    }
}
