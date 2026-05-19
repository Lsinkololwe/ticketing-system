package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PayoutRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for bulk payout operations.
 * Matches the BulkPayoutOperationResponse GraphQL type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkPayoutOperationResponse {
    private boolean success;
    private String message;
    private int processedCount;
    private int failedCount;
    private List<PayoutRequest> processedPayouts;
    private List<String> failedPayoutIds;
    private List<String> errors;
}
