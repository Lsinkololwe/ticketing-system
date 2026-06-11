package com.pml.identity.domain.valueobject;

import com.pml.identity.domain.enums.PayoutMethod;
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
     * Preferred payout method
     */
    @Builder.Default
    private PayoutMethod preferredMethod = PayoutMethod.MOBILE_MONEY;

    /**
     * Payout schedule
     */
    @Builder.Default
    private PayoutSchedule schedule = PayoutSchedule.WEEKLY;

    /**
     * Commission rate charged to this organization (e.g., 0.05 = 5%)
     * Set by platform admin, can be negotiated for high-volume organizers
     */
    @Builder.Default
    private Double commissionRate = 0.05;

    /**
     * Minimum payout amount (in ZMW)
     */
    @Builder.Default
    private Double minimumPayoutAmount = 100.0;

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
    public boolean canProcessPayouts() {
        return isConfigured() && verified;
    }
}
