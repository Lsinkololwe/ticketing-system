package com.pml.identity.service;

import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The pure rules behind the organization administration operations: commission bounds, which
 * profile fields are open for editing, when a deletion may be requested, and what state a payout
 * account is in. No I/O, so each is a layer-1 test.
 */
public final class OrganizationRules {

    /** A commission above this is refused; no organization is charged more than half a sale. */
    public static final double MAX_COMMISSION_PERCENT = 50.0;

    /** How long an organization stays recoverable after its owner asks for deletion. */
    public static final Duration DELETION_GRACE = Duration.ofDays(30);

    private OrganizationRules() {
    }

    /** The state of one payout account, as an administrator and the owner see it. */
    public enum PayoutAccountStatus { NONE, PENDING, VERIFIED, REJECTED, SUSPENDED }

    /**
     * Converts an administrator's percentage (5 means 5%) to the fraction the platform stores
     * (0.05), refusing anything outside {@code [0, 50]}. Exact decimal arithmetic, so 7.5 is
     * stored as 0.075 and not 0.07500000000000001.
     */
    public static double fractionOf(Double percent) {
        if (percent == null || percent.isNaN() || percent < 0 || percent > MAX_COMMISSION_PERCENT) {
            throw new TranslatedRefusal(ErrorCode.CONFIGURATION_VALUE_INVALID,
                    "commission rate must be between 0 and 50 percent",
                    Map.of("constraint", "commissionRate in [0, 50]"));
        }
        return BigDecimal.valueOf(percent).movePointLeft(2).doubleValue();
    }

    /** The percentage form of a stored fraction; null stays null. */
    public static Double percentOf(Double fraction) {
        return fraction == null ? null : BigDecimal.valueOf(fraction).movePointRight(2).doubleValue();
    }

    /**
     * Whether the identity fields a review depends on (tax id, registration number, legal type) may
     * still be edited. After approval they are fixed: changing them would let an approved
     * organization swap the entity that passed KYB for another.
     */
    public static boolean kybFieldsEditable(OrganizationStatus status) {
        return status == OrganizationStatus.DRAFT || status == OrganizationStatus.CHANGES_REQUESTED;
    }

    /** Refuses a deletion request in a state where it cannot be honoured. */
    public static void requireDeletable(OrganizationStatus status) {
        if (status == OrganizationStatus.PENDING_DELETION) {
            throw new TranslatedRefusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                    "a deletion request is already open", Map.of("currentStatus", status.name()));
        }
        if (status == OrganizationStatus.SUSPENDED) {
            throw new TranslatedRefusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                    "a suspended organization cannot be deleted by its owner", Map.of("currentStatus", status.name()));
        }
    }

    public static Instant deletionDate(Instant requestedAt) {
        return requestedAt.plus(DELETION_GRACE);
    }

    /** The account a payout would go to: the preferred method's, or whichever one is on file. */
    public static Object activeAccount(PayoutConfig config) {
        if (config == null) {
            return null;
        }
        if (config.getPreferredMethod() == PayoutMethod.MOBILE_MONEY && config.getMobileMoneyAccount() != null) {
            return config.getMobileMoneyAccount();
        }
        if (config.getPreferredMethod() == PayoutMethod.BANK_TRANSFER && config.getBankAccount() != null) {
            return config.getBankAccount();
        }
        return config.getBankAccount() != null ? config.getBankAccount() : config.getMobileMoneyAccount();
    }

    public static PayoutAccountStatus statusOf(boolean configured, boolean verified, boolean suspended,
                                               String rejectionReason) {
        if (!configured) {
            return PayoutAccountStatus.NONE;
        }
        if (suspended) {
            return PayoutAccountStatus.SUSPENDED;
        }
        if (verified) {
            return PayoutAccountStatus.VERIFIED;
        }
        return rejectionReason != null && !rejectionReason.isBlank()
                ? PayoutAccountStatus.REJECTED : PayoutAccountStatus.PENDING;
    }

    public static PayoutAccountStatus statusOf(PayoutBankDetails bank) {
        return statusOf(bank != null && bank.getAccountNumber() != null, bank != null && bank.isVerified(),
                bank != null && bank.isSuspended(), bank == null ? null : bank.getRejectionReason());
    }

    public static PayoutAccountStatus statusOf(MobileMoneyAccount wallet) {
        return statusOf(wallet != null && wallet.getPhoneNumber() != null, wallet != null && wallet.isVerified(),
                wallet != null && wallet.isSuspended(), wallet == null ? null : wallet.getRejectionReason());
    }

    /** The status of the account a payout would use. */
    public static PayoutAccountStatus statusOf(PayoutConfig config) {
        Object account = activeAccount(config);
        if (account instanceof PayoutBankDetails bank) {
            return statusOf(bank);
        }
        if (account instanceof MobileMoneyAccount wallet) {
            return statusOf(wallet);
        }
        return PayoutAccountStatus.NONE;
    }

    /** Trims and bounds an administrator-supplied reason; blank is refused. */
    public static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a reason is required",
                    Map.of());
        }
        String trimmed = reason.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
