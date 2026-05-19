package com.pml.booking.web.graphql.dto;

import java.util.List;
import java.util.Map;

public record DeleteBankAccountMutationResponse(
    boolean success,
    String message,
    List<String> errors,
    Map<String, Object> metadata
) {}
