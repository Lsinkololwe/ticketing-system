package com.pml.shared.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * What each platform role is meant to hold, written out by hand.
 *
 * <p>The production sets are declared in {@link PlatformRole#grants()}; these are the same rules stated
 * separately, so a change to one that is not a change to the other fails a test. They are the business
 * rule: a super administrator holds everything; an administrator everything except the four things only
 * a super administrator or finance does; finance a money-and-audit set that reaches no organization's
 * events or team.
 */
final class PlatformGrantExpectations {

    private PlatformGrantExpectations() {
    }

    /** What an administrator does not hold. */
    static final Set<Permission> ADMIN_EXCLUDED = EnumSet.of(
            Permission.PLATFORM_CONFIGURE, Permission.PAYMENT_APPROVE,
            Permission.STAFF_MANAGE, Permission.REFUND_APPROVE);

    /** What finance holds. */
    static final Set<Permission> FINANCE = EnumSet.of(
            Permission.FINANCIAL_VIEW, Permission.PAYOUT_APPROVE, Permission.TICKET_REFUND,
            Permission.TRANSACTION_RECOVER, Permission.AUDIT_VIEW, Permission.ORGANIZATION_VIEW,
            Permission.EVENT_VIEW,
            Permission.LEDGER_VIEW,
            Permission.RECONCILIATION_VIEW,
            Permission.ESCROW_VIEW,
            Permission.CHARGEBACK_VIEW,
            Permission.CHARGEBACK_RECOVER,
            Permission.PAYMENT_VIEW,
            Permission.PAYOUT_VIEW,
            Permission.PAYOUT_MANAGE,
            Permission.REFUND_VIEW,
            Permission.REFUND_MANAGE,
            Permission.REFUND_APPROVE,
            Permission.COMMISSION_VIEW,
            Permission.SETTLEMENT_VIEW,
            Permission.PLATFORM_ACCOUNT_TRANSFER,
            Permission.STATISTICS_VIEW,
            Permission.REPORT_CREATE,
            Permission.BANK_ACCOUNT_VIEW,
            Permission.BANK_ACCOUNT_APPROVE,
            Permission.RESERVATION_VIEW,
            Permission.TICKET_VIEW,
            Permission.BUYER_VIEW);

    static Set<Permission> admin() {
        return EnumSet.complementOf(EnumSet.copyOf(ADMIN_EXCLUDED));
    }
}
