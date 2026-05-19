package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.OrganizerStatus;
import com.pml.identity.domain.valueobject.SocialLinks;

import com.pml.identity.domain.base.Identifiable;
import com.pml.identity.domain.base.Timestamped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Organizer Profile Model
 *
 * <p>Stores business-specific data for event organizers (KYB data).
 * This represents the "application" that gets reviewed and approved.</p>
 *
 * <h2>Architecture Notes</h2>
 * <ul>
 *   <li>The {@code userId} field IS the Keycloak user ID (sub claim)</li>
 *   <li>logoUrl/bannerUrl belong in {@link Organization} (public branding), NOT here</li>
 *   <li>To get the linked Organization, query via {@code Organization.organizerProfileId}</li>
 *   <li>Personal identity data (name, email) is in {@link User}, not here</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>User applies to become organizer → OrganizerProfile created (status: DRAFT)</li>
 *   <li>User completes profile and uploads documents (status: PENDING_REVIEW)</li>
 *   <li>Admin reviews and approves (status: APPROVED)</li>
 *   <li>System creates Organization with {@code organizerProfileId} pointing here</li>
 * </ol>
 *
 * @see Organization For the public business entity created on approval
 * @see User For personal identity data
 * @see VerificationDocument For KYB documents
 */
@Document(collection = "organizer_profiles")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerProfile implements Identifiable<String>, Timestamped {

    @Id
    private String id;

    /**
     * User ID - MUST match Keycloak user ID (sub claim).
     * This links the profile to the User who applied.
     *
     * <p>NOTE: This IS the Keycloak user ID. Previously named keycloakUserId,
     * renamed for consistency with other models.</p>
     */
    @NotBlank(message = "User ID is required")
    @Indexed(unique = true)
    private String userId;

    // ─────────────────────────────────────────────────────────────────────
    // Business Information
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Company/Organization name
     */
    private String companyName;

    /**
     * Description of the company/organizer
     */
    private String companyDescription;

    /**
     * Short tagline for the organizer
     */
    private String tagline;

    /**
     * Company website URL
     */
    private String website;

    /**
     * Social media links
     */
    private SocialLinks socialLinks;

    // ─────────────────────────────────────────────────────────────────────
    // Business Registration (KYB)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tax identification number (TPIN in Zambia)
     */
    private String taxId;

    /**
     * Business registration number (PACRA number in Zambia)
     */
    private String businessRegistrationNumber;

    /**
     * Business type (e.g., LLC, Sole Proprietor, Corporation)
     */
    private String businessType;

    /**
     * Year the business was established
     */
    private Integer yearEstablished;

    // ─────────────────────────────────────────────────────────────────────
    // Contact Information
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Business phone number (may differ from personal)
     */
    private String businessPhone;

    /**
     * Business email (may differ from personal)
     */
    private String businessEmail;

    /**
     * Physical business address
     */
    private String businessAddress;

    /**
     * City where business is located
     */
    private String city;

    /**
     * Province/State
     */
    private String province;

    /**
     * Country of operation
     */
    private String country;

    /**
     * Postal code
     */
    private String postalCode;

    // ─────────────────────────────────────────────────────────────────────
    // Financial Settings (set by admin)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * ID of the default bank account for payouts
     */
    private String defaultBankAccountId;

    /**
     * Commission rate charged to this organizer (e.g., 0.05 = 5%)
     */
    @Builder.Default
    private Double commissionRate = 0.05;

    /**
     * Payout schedule (DAILY, WEEKLY, MONTHLY)
     */
    @Builder.Default
    private String payoutSchedule = "WEEKLY";

    // ─────────────────────────────────────────────────────────────────────
    // Application Status
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Application/account status
     */
    @Builder.Default
    private OrganizerStatus status = OrganizerStatus.DRAFT;

    /**
     * Reason for rejection or changes requested
     */
    private String rejectionReason;

    // ─────────────────────────────────────────────────────────────────────
    // Verification Flags
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Whether the organizer's business details have been verified
     */
    @Builder.Default
    private boolean verified = false;

    /**
     * When the organizer was verified
     */
    private Instant verifiedAt;

    /**
     * Admin who verified the organizer (Keycloak user ID)
     */
    private String verifiedBy;

    /**
     * Whether all documents are verified
     */
    @Builder.Default
    private boolean documentsVerified = false;

    /**
     * Whether bank account is verified
     */
    @Builder.Default
    private boolean bankVerified = false;

    // ─────────────────────────────────────────────────────────────────────
    // Review Information (internal)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Admin who reviewed the application (Keycloak user ID)
     */
    private String reviewedBy;

    /**
     * When the application was reviewed
     */
    private Instant reviewedAt;

    /**
     * Admin notes (internal, not shown to organizer)
     */
    private String adminNotes;

    // ─────────────────────────────────────────────────────────────────────
    // Timestamps
    // ─────────────────────────────────────────────────────────────────────

    /**
     * When application was submitted for review
     */
    private Instant submittedAt;

    /**
     * When application was approved
     */
    private Instant approvedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if organizer is approved and can create events.
     *
     * @return true if status is APPROVED
     */
    public boolean canCreateEvents() {
        return status == OrganizerStatus.APPROVED;
    }

    /**
     * Check if organizer application is pending review.
     *
     * @return true if status is PENDING_REVIEW
     */
    public boolean isPendingReview() {
        return status == OrganizerStatus.PENDING_REVIEW;
    }

    /**
     * Check if organizer application is in draft state.
     *
     * @return true if status is DRAFT
     */
    public boolean isDraft() {
        return status == OrganizerStatus.DRAFT;
    }

    /**
     * Check if all verifications are complete.
     *
     * @return true if verified, documentsVerified, and bankVerified are all true
     */
    public boolean isFullyVerified() {
        return verified && documentsVerified && bankVerified;
    }

    /**
     * Get display name (company name or fallback).
     *
     * @return Company name if available, otherwise "Organizer {id}"
     */
    public String getDisplayName() {
        return companyName != null && !companyName.isBlank()
            ? companyName
            : "Organizer " + id;
    }
}
