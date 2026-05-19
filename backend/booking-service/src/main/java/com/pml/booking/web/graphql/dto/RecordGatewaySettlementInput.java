package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Input for recording a gateway settlement in the accounting system.
 *
 * <p><b>Business Context:</b></p>
 * <p>When the payment gateway (PawaPay) settles funds to our bank account,
 * we need to record this transaction to clear our Gateway Receivable and
 * record the actual bank deposit and fees.</p>
 *
 * <p><b>Accounting Flow (IN/OUT):</b></p>
 * <pre>
 * Example: Gateway settles K10,000 with K200 fees:
 *
 *   DR Bank Account (1011)            K9,800  [IN - net amount received]
 *   DR Gateway Fees Expense (5010)      K200  [IN - fee cost to platform]
 *      CR Gateway Receivable (1021)          K10,000  [OUT - receivable cleared]
 * </pre>
 *
 * <p><b>Trigger Points:</b></p>
 * <ul>
 *   <li>PawaPay webhook: Settlement notification received</li>
 *   <li>Admin dashboard: Manual settlement recording after bank reconciliation</li>
 *   <li>Scheduled job: Auto-record settlements from gateway API</li>
 * </ul>
 *
 * @param settlementId   Unique settlement ID from gateway (e.g., "PAW-SETTLE-20260420")
 * @param grossAmount    Gross amount before fees (total of all transactions in settlement)
 * @param feeAmount      Fees deducted by gateway (gateway processing fees)
 * @param netAmount      Net amount received in bank (grossAmount - feeAmount)
 * @param settlementDate Date and time of settlement
 * @param bankReference  Bank transaction reference (for reconciliation)
 * @param currency       Currency code (e.g., "ZMW")
 *
 * @since 1.0.0
 */
public record RecordGatewaySettlementInput(
    String settlementId,
    BigDecimal grossAmount,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    LocalDateTime settlementDate,
    String bankReference,
    String currency
) {
    /**
     * Constructor with validation.
     *
     * <p>Validates that:</p>
     * <ul>
     *   <li>All required fields are present</li>
     *   <li>Amounts are non-negative</li>
     *   <li>netAmount = grossAmount - feeAmount (within tolerance)</li>
     * </ul>
     */
    public RecordGatewaySettlementInput {
        if (settlementId == null || settlementId.isBlank()) {
            throw new IllegalArgumentException("Settlement ID is required");
        }
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Gross amount must be non-negative");
        }
        if (feeAmount == null || feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee amount must be non-negative");
        }
        if (netAmount == null || netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Net amount must be non-negative");
        }
        if (settlementDate == null) {
            throw new IllegalArgumentException("Settlement date is required");
        }
        if (bankReference == null || bankReference.isBlank()) {
            throw new IllegalArgumentException("Bank reference is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }

        // Validate net = gross - fee
        BigDecimal expectedNet = grossAmount.subtract(feeAmount);
        BigDecimal tolerance = new BigDecimal("0.01");
        if (netAmount.subtract(expectedNet).abs().compareTo(tolerance) > 0) {
            throw new IllegalArgumentException(String.format(
                "Net amount (%s) does not match gross (%s) - fee (%s) = %s",
                netAmount, grossAmount, feeAmount, expectedNet
            ));
        }
    }
}
