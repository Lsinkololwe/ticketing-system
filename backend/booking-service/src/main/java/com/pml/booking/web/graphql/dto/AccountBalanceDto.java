package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * One row of the trial balance. Maps to the schema's {@code AccountBalance}.
 *
 * @param accountCode   the chart-of-accounts code, e.g. {@code 1010}
 * @param accountName   human-readable name for the report
 * @param accountType   ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE
 * @param debitBalance  total debits posted to the account
 * @param creditBalance total credits posted to the account
 * @param netBalance    the net in the account's own normal direction, so a correct row is
 *                      positive whichever side the account normally sits
 */
public record AccountBalanceDto(
    String accountCode,
    String accountName,
    String accountType,
    BigDecimal debitBalance,
    BigDecimal creditBalance,
    BigDecimal netBalance
) {}
