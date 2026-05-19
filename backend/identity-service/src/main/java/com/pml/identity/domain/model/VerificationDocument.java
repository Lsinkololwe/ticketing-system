package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.DocumentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Verification Document Model
 *
 * Stores documents uploaded by organizers for KYB verification.
 *
 * DOCUMENT TYPES:
 * ==============
 * - ID_DOCUMENT: National ID, passport, driver's license
 * - BUSINESS_LICENSE: Business registration certificate
 * - TAX_CERTIFICATE: Tax clearance certificate (TPIN)
 * - BANK_STATEMENT: Proof of bank account
 * - PROOF_OF_ADDRESS: Utility bill, lease agreement
 * - OTHER: Any other supporting document
 */
@Document(collection = "verification_documents")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class VerificationDocument {

    @Id
    private String id;

    /**
     * OrganizerProfile ID this document belongs to
     */
    @NotBlank(message = "Organizer profile ID is required")
    @Indexed
    private String organizerProfileId;

    /**
     * Type of document
     */
    @NotBlank(message = "Document type is required")
    private String documentType;

    /**
     * URL to the stored document
     */
    @NotBlank(message = "Document URL is required")
    private String documentUrl;

    /**
     * Original file name
     */
    private String fileName;

    /**
     * File size in bytes
     */
    private Long fileSize;

    /**
     * MIME type of the document
     */
    private String mimeType;

    /**
     * Document status
     */
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    /**
     * When the document was uploaded
     */
    @NotNull
    @CreatedDate
    private Instant uploadedAt;

    /**
     * When the document was verified
     */
    private Instant verifiedAt;

    /**
     * Admin who verified the document
     */
    private String verifiedById;

    /**
     * Reason for rejection (if rejected)
     */
    private String rejectionReason;

    /**
     * Check if document is approved
     */
    public boolean isApproved() {
        return status == DocumentStatus.APPROVED;
    }

    /**
     * Check if document is pending review
     */
    public boolean isPending() {
        return status == DocumentStatus.PENDING;
    }
}
