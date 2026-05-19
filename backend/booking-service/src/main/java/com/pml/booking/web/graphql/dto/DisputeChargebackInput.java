package com.pml.booking.web.graphql.dto;

/**
 * Input for disputing a chargeback with evidence.
 *
 * @param ticketValidationProof Evidence of ticket validation (e.g., QR scan timestamp)
 * @param customerCommunicationLog Log of communications with customer
 * @param deliveryConfirmation Confirmation of ticket delivery (email, SMS)
 * @param termsAcceptanceProof Proof of terms acceptance
 * @param additionalDocuments Any additional supporting documents
 * @param notes Internal notes about the dispute
 *
 * @since 1.0.0
 */
public record DisputeChargebackInput(
    String ticketValidationProof,
    String customerCommunicationLog,
    String deliveryConfirmation,
    String termsAcceptanceProof,
    String additionalDocuments,
    String notes
) {
    /**
     * Constructor with validation.
     */
    public DisputeChargebackInput {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("Notes are required when disputing a chargeback");
        }
    }

    /**
     * Check if any evidence has been provided.
     */
    public boolean hasEvidence() {
        return (ticketValidationProof != null && !ticketValidationProof.isBlank()) ||
               (customerCommunicationLog != null && !customerCommunicationLog.isBlank()) ||
               (deliveryConfirmation != null && !deliveryConfirmation.isBlank()) ||
               (termsAcceptanceProof != null && !termsAcceptanceProof.isBlank()) ||
               (additionalDocuments != null && !additionalDocuments.isBlank());
    }
}
