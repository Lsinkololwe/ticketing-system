package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.BankAccount;

import java.util.List;
import java.util.Map;

public record UpdateBankAccountMutationResponse(
    boolean success,
    String message,
    BankAccount data,
    List<String> errors,
    Map<String, Object> metadata
) {}
