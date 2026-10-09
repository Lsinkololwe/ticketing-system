package com.pml.identity.domain.valueobject;

import com.pml.shared.constants.PayoutMethod;
import com.pml.identity.domain.enums.PayoutSchedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payout configuration for an organization.
 * Embedded document within Organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutConfig {

    /**
     * Preferred payout method.
     * Seeded from the {@code platform_configuration} document at organization creation —
     * no default is baked into the entity.
     */
    private PayoutMethod preferredMethod;

    /**
     * Payout schedule.
     * Seeded from the {@code platform_configuration} document at organization creation —
     * no default is baked into the entity.
     */
    private PayoutSchedule schedule;

    /**
     * Commission rate charged to this organization (e.g., 0.05 = 5%).
     * Seeded from the {@code platform_configuration} document at organization creation;
     * can be negotiated for high-volume organizers. No default is baked into the entity.
     */
    private Double commissionRate;

    /** The administrator who last set this organization's commission rate; null while it is the platform default. */
    private String commissionSetBy;

    private java.time.Instant commissionSetAt;

    /**
     * Minimum payout amount, in ZMW.
     *
     * <p>{@code BigDecimal} because it is compared against an escrow balance that is also a
     * BigDecimal, and a {@code Double} threshold has to be converted to make that comparison.
     * K0.10 has no exact binary representation, so a payout of precisely the minimum can be
     * refused — a rejection with no explanation anyone can find in the numbers.</p>
     */
    private java.math.BigDecimal minimumPayoutAmount;

    /**
     * Bank account for payouts (if preferredMethod = BANK_TRANSFER)
     */
    private PayoutBankDetails bankAccount;

    /**
     * Mobile money account for payouts (if preferredMethod = MOBILE_MONEY)
     */
    private MobileMoneyAccount mobileMoneyAccount;

    /**
     * Whether payout configuration is complete and verified
     */
    @Builder.Default
    private boolean verified = false;

    /**
     * Check if payout method is configured
     */
    public boolean isConfigured() {
        if (preferredMethod == PayoutMethod.BANK_TRANSFER) {
            return bankAccount != null && bankAccount.getAccountNumber() != null;
        } else if (preferredMethod == PayoutMethod.MOBILE_MONEY) {
            return mobileMoneyAccount != null && mobileMoneyAccount.getPhoneNumber() != null;
        }
        return false;
    }

    /**
     * Check if payouts can be processed
     */
    /** True when an administrator has frozen payouts to the account a payout would use. */
    public boolean isAccountSuspended() {
        if (preferredMethod == com.pml.shared.constants.PayoutMethod.MOBILE_MONEY) {
            return mobileMoneyAccount != null && mobileMoneyAccount.isSuspended();
        }
        return bankAccount != null && bankAccount.isSuspended();
    }

    public boolean canProcessPayouts() {
        return isConfigured() && verified && !isAccountSuspended();
    }
}
