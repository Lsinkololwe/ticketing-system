package com.pml.booking.web.graphql.dto;

public record UpdateBankAccountInput(
    String accountHolderName,
    String bankName,
    String bankCode,
    String branchName,
    String branchCode,
    String accountNumber,
    String accountType,
    String swiftCode,
    Boolean isDefault
) {}
