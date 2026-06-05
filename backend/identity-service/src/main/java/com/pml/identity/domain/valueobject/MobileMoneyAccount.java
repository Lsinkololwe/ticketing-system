package com.pml.identity.domain.valueobject;

import com.pml.identity.domain.enums.MobileMoneyProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mobile money account details for payouts.
 * Designed for Zambian mobile money providers.
 * Embedded document within Organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyAccount {

    /**
     * Mobile money provider (MTN, AIRTEL, ZAMTEL)
     */
    private MobileMoneyProvider provider;

    /**
     * Phone number registered with mobile money (E.164 format)
     */
    private String phoneNumber;

    /**
     * Account holder name (registered name with provider)
     */
    private String accountHolderName;

    /**
     * Whether this mobile money account has been verified
     */
    @Builder.Default
    private boolean verified = false;

    /**
     * Get masked phone number for display
     */
    public String getMaskedPhoneNumber() {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        // Show country code and last 4 digits: +260****1234
        int length = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(length - 4);
    }
}
