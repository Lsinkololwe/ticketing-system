package com.pml.identity.domain.enums;

/**
 * Method for receiving payouts.
 * Designed for Zambia/Africa market with mobile money support.
 */
public enum PayoutMethod {
    /**
     * Bank transfer to a verified bank account
     */
    BANK_TRANSFER,

    /**
     * Mobile money transfer (MTN, Airtel, Zamtel)
     */
    MOBILE_MONEY
}
