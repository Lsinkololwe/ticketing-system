package com.pml.booking.dto;

/**
 * Payer Account Details
 *
 * Business Intent: Represents the customer's mobile money account information
 * used for collecting payments. Zambia supports three mobile money providers:
 * MTN Mobile Money, Airtel Money, and Zamtel Kwacha.
 *
 * @param type Account type (always "MMO" for Mobile Money Operator)
 * @param accountDetails The specific account information
 */
public record PayerDetails(
        String type,
        MobileMoneyAccountDetails accountDetails
) {}
