package com.pml.booking.dto;

/**
 * Recipient Account Details
 *
 * Business Intent: Represents the recipient's mobile money account information
 * used for payouts and refunds. This is where funds are sent when processing
 * organizer payouts or customer refunds.
 *
 * @param type Account type (always "MMO" for Mobile Money Operator)
 * @param accountDetails The specific account information
 */
public record RecipientDetails(
        String type,
        MobileMoneyAccountDetails accountDetails
) {}
