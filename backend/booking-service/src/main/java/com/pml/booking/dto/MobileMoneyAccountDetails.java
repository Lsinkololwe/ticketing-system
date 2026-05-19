package com.pml.booking.dto;

/**
 * Mobile Money Account Details
 *
 * Business Intent: Represents a mobile money account in Zambia. The phone number
 * identifies the account, and the provider indicates which mobile money network.
 *
 * Supported Providers in Zambia:
 * - MTN_MOMO_ZMB: MTN Mobile Money
 * - AIRTEL_ZMB: Airtel Money
 * - ZAMTEL_ZMB: Zamtel Kwacha
 *
 * @param phoneNumber Customer's phone number (format: 260XXXXXXXXX)
 * @param provider Mobile money provider code
 */
public record MobileMoneyAccountDetails(
        String phoneNumber,
        String provider
) {}
