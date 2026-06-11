package com.pml.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bank account details for payout configuration.
 * Embedded document within Organization.
 * NOTE: Full BankAccount entity is owned by Booking Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutBankDetails {

    /**
     * Name of the bank (e.g., "Zanaco", "Standard Chartered", "FNB Zambia")
     */
    private String bankName;

    /**
     * Bank code / SWIFT code
     */
    private String bankCode;

    /**
     * Branch name
     */
    private String branchName;

    /**
     * Branch code
     */
    private String branchCode;

    /**
     * Account number
     */
    private String accountNumber;

    /**
     * Account holder name (must match business/individual name)
     */
    private String accountHolderName;

    /**
     * Account type (CHECKING, SAVINGS, BUSINESS)
     */
    @Builder.Default
    private String accountType = "BUSINESS";

    /**
     * Whether this bank account has been verified
     */
    @Builder.Default
    private boolean verified = false;

    /**
     * Last 4 digits of account number (for display)
     */
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
