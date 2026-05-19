package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.AccountSubType;
import com.pml.booking.domain.enums.AccountType;

/**
 * Input for creating or updating a Chart of Accounts entry.
 *
 * @param accountCode Unique account code (e.g., "1000", "2010-EVT001")
 * @param accountName Human-readable account name
 * @param accountType Primary account classification (ASSET, LIABILITY, etc.)
 * @param subType Detailed sub-classification (optional)
 * @param parentAccountCode Parent account code for hierarchical structure (optional)
 * @param currency Account currency (default: ZMW)
 * @param description Account description (optional)
 *
 * @since 1.0.0
 */
public record CreateChartOfAccountsInput(
    String accountCode,
    String accountName,
    AccountType accountType,
    AccountSubType subType,
    String parentAccountCode,
    String currency,
    String description
) {
    /**
     * Constructor with validation.
     */
    public CreateChartOfAccountsInput {
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("Account code is required");
        }
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("Account name is required");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("Account type is required");
        }
    }
}
