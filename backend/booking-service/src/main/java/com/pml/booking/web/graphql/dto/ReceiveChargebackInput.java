package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.ChargebackReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Input for receiving a chargeback from a payment provider.
 *
 * @param chargebackId External chargeback ID from payment provider
 * @param originalTransactionId Original payment transaction ID
 * @param ticketId Associated ticket ID
 * @param eventId Associated event ID
 * @param organizerId Event organizer ID
 * @param organizationId Organization ID for multi-tenant tracking
 * @param customerId Customer who initiated the chargeback
 * @param originalAmount Original transaction amount
 * @param chargebackAmount Chargeback amount (may differ from original)
 * @param chargebackFee Chargeback fee charged by provider (typically $15-25)
 * @param currency Currency code
 * @param reason Chargeback reason category
 * @param responseDeadline Deadline for responding to the chargeback
 *
 * @since 1.0.0
 */
public record ReceiveChargebackInput(
    String chargebackId,
    String originalTransactionId,
    String ticketId,
    String eventId,
    String organizerId,
    String organizationId,
    String customerId,
    BigDecimal originalAmount,
    BigDecimal chargebackAmount,
    BigDecimal chargebackFee,
    String currency,
    ChargebackReason reason,
    LocalDateTime responseDeadline
) {
    /**
     * Constructor with validation.
     */
    public ReceiveChargebackInput {
        if (chargebackId == null || chargebackId.isBlank()) {
            throw new IllegalArgumentException("Chargeback ID is required");
        }
        if (originalTransactionId == null || originalTransactionId.isBlank()) {
            throw new IllegalArgumentException("Original transaction ID is required");
        }
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("Ticket ID is required");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (organizerId == null || organizerId.isBlank()) {
            throw new IllegalArgumentException("Organizer ID is required");
        }
        if (chargebackAmount == null || chargebackAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Chargeback amount must be positive");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Chargeback reason is required");
        }
        if (responseDeadline == null) {
            throw new IllegalArgumentException("Response deadline is required");
        }
    }
}
