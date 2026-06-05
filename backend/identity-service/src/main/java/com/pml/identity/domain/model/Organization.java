package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.BusinessType;
import com.pml.identity.domain.enums.KybStatus;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.valueobject.BusinessAddress;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.valueobject.OrganizationStats;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.identity.domain.valueobject.SocialLinks;

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
 * Organization Model
 *
 * Represents a business entity (tenant) in the multi-tenant architecture.
 * This is the SINGLE entity for event organizers - no separate OrganizerProfile.
 *
 * APPROVAL-BASED ONBOARDING:
 * ==========================
 * 1. User registers → User created (no organization yet)
 * 2. User applies to become organizer → Organization created (status=DRAFT)
 * 3. User fills business details (name, description, contact info)
 * 4. User submits for review → status=PENDING_REVIEW
 * 5. Admin reviews:
 *    - APPROVED → Can publish events
 *    - CHANGES_REQUESTED → User updates and resubmits
 *    - REJECTED → Cannot create events
 * 6. User can create DRAFT events during approval process
 * 7. Banking details can be added later when ready for payouts
 *
 * ARCHITECTURE NOTES:
 * ==================
 * - Each Organization has exactly ONE owner (ownerId)
 * - Organization is linked to Keycloak via a group structure:
 *   /organizations/{slug}/owners
 *   /organizations/{slug}/admins
 *   /organizations/{slug}/managers
 *   /organizations/{slug}/marketers
 *   /organizations/{slug}/contributors
 * - Events created by members are associated with the organization
 * - Financial data (escrow, payouts) is scoped to the organization
 * - Payouts go to the Organization (legal entity), not individuals
 *
 * @see OrganizationStatus Approval workflow (DRAFT → PENDING_REVIEW → APPROVED)
 * @see OrganizationType INDIVIDUAL vs BUSINESS organizations
 * @see PayoutConfig Payout settings (bank/mobile money)
 */
@Document(collection = "organizations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    private String id;

    // ─────────────────────────────────────────────────────────────────────
    // Basic Information
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Display name of the organization.
     * For INDIVIDUAL orgs, defaults to "{firstName} {lastName}" initially.
     */
    @NotBlank(message = "Organization name is required")
    private String name;

    /**
     * URL-friendly unique identifier (e.g., "acme-events")
     */
    @NotBlank(message = "Slug is required")
    @Indexed(unique = true)
    private String slug;

    /**
     * Description of the organization
     */
    private String description;

    /**
     * Short tagline for the organization
     */
    private String tagline;

    /**
     * URL to organization logo
     */
    private String logoUrl;

    /**
     * URL to organization banner image
     */
    private String bannerUrl;

    /**
     * Company website URL
     */
    private String website;

    /**
     * Social media links
     */
    private SocialLinks socialLinks;

    // ─────────────────────────────────────────────────────────────────────
    // Organization Type & Status
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Type of organization: INDIVIDUAL or BUSINESS.
     * INDIVIDUAL orgs are created lazily on first event creation.
     * BUSINESS orgs have formal KYB requirements.
     */
    @Builder.Default
    private OrganizationType type = OrganizationType.INDIVIDUAL;

    /**
     * Keycloak group ID for this organization
     */
    private String keycloakGroupId;

    /**
     * User ID of the organization owner (only ONE owner allowed).
     * This is the Keycloak user ID (sub claim).
     */
    @NotBlank(message = "Owner is required")
    @Indexed
    private String ownerId;

    /**
     * Organization status in approval workflow.
     * New organizations start as DRAFT and must be approved before publishing events.
     */
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.DRAFT;

    // ─────────────────────────────────────────────────────────────────────
    // Know Your Business (KYB) - Business Registration
    // ─────────────────────────────────────────────────────────────────────

    /**
     * KYB verification status.
     * Determines what actions the organization can take.
     */
    @Builder.Default
    private KybStatus kybStatus = KybStatus.NOT_STARTED;

    /**
     * Legal business type (LLC, Sole Proprietor, etc.)
     */
    private BusinessType businessType;

    /**
     * Tax identification number (TPIN in Zambia)
     */
    private String taxId;

    /**
     * Business registration number (PACRA number in Zambia)
     */
    private String businessRegistrationNumber;

    /**
     * Year the business was established
     */
    private Integer yearEstablished;

    // ─────────────────────────────────────────────────────────────────────
    // Contact Information
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Business phone number (may differ from owner's personal phone)
     */
    private String businessPhone;

    /**
     * Business email (may differ from owner's personal email)
     */
    @Indexed
    private String businessEmail;

    /**
     * Physical business address
     */
    private BusinessAddress businessAddress;

    // ─────────────────────────────────────────────────────────────────────
    // Payout Configuration
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Payout configuration (method, schedule, accounts)
     */
    @Builder.Default
    private PayoutConfig payoutConfig = new PayoutConfig();

    // ─────────────────────────────────────────────────────────────────────
    // Verification Flags
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Whether the organization's business details have been verified by admin
     */
    @Builder.Default
    private boolean verified = false;

    /**
     * Whether all KYB documents are verified
     */
    @Builder.Default
    private boolean documentsVerified = false;

    /**
     * Whether payout account (bank/mobile money) is verified
     */
    @Builder.Default
    private boolean payoutAccountVerified = false;

    /**
     * When the organization was verified
     */
    private Instant verifiedAt;

    /**
     * Admin who verified the organization (Keycloak user ID)
     */
    private String verifiedBy;

    // ─────────────────────────────────────────────────────────────────────
    // Review Information (internal)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reason for rejection or changes requested (for KYB review)
     */
    private String rejectionReason;

    /**
     * Admin who reviewed the KYB application (Keycloak user ID)
     */
    private String reviewedBy;

    /**
     * When the KYB was reviewed
     */
    private Instant reviewedAt;

    /**
     * Admin notes (internal, not shown to organization)
     */
    private String adminNotes;

    /**
     * When KYB was submitted for review
     */
    private Instant kybSubmittedAt;

    /**
     * When organization application was submitted for review
     */
    private Instant submittedAt;

    /**
     * When organization was approved
     */
    private Instant approvedAt;

    // ─────────────────────────────────────────────────────────────────────
    // Settings & Stats
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Organization settings (embedded document)
     */
    @Builder.Default
    private OrganizationSettings settings = new OrganizationSettings();

    /**
     * Statistics (denormalized for performance)
     */
    @Builder.Default
    private OrganizationStats stats = new OrganizationStats();

    // ─────────────────────────────────────────────────────────────────────
    // Timestamps
    // ─────────────────────────────────────────────────────────────────────

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if organization is active/approved and operational.
     */
    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE || status == OrganizationStatus.APPROVED;
    }

    /**
     * Check if organization is in the approval workflow.
     */
    public boolean isInApprovalWorkflow() {
        return status != null && status.isInApprovalWorkflow();
    }

    /**
     * Check if organization application was approved.
     */
    public boolean isApproved() {
        return status == OrganizationStatus.APPROVED || status == OrganizationStatus.ACTIVE;
    }

    /**
     * Check if organization can create draft events.
     * Allowed during approval process and after approval.
     */
    public boolean canCreateDraftEvents() {
        return status != null && status.canCreateDraftEvents();
    }

    /**
     * Check if organization can publish events.
     * Only allowed after approval.
     */
    public boolean canPublishEvents() {
        return status != null && status.canPublishEvents();
    }

    /**
     * Check if organization can accept payments.
     * Requires approval and payout account setup.
     */
    public boolean canAcceptPayments() {
        return isApproved()
            && payoutConfig != null
            && payoutConfig.canProcessPayouts();
    }

    /**
     * Check if organization can receive payouts.
     * Requires approval, verified payout account, and KYB if configured.
     */
    public boolean canReceivePayouts() {
        return isApproved()
            && payoutAccountVerified
            && payoutConfig != null
            && payoutConfig.isConfigured();
    }

    /**
     * Check if all verifications are complete.
     */
    public boolean isFullyVerified() {
        return verified && documentsVerified && payoutAccountVerified;
    }

    /**
     * Check if organization details can be edited.
     */
    public boolean canBeEdited() {
        return status != null && status.canBeEdited();
    }

    /**
     * Check if organization can submit for review.
     */
    public boolean canSubmitForReview() {
        return status == OrganizationStatus.DRAFT || status == OrganizationStatus.CHANGES_REQUESTED;
    }

    /**
     * Check if organization is an individual (sole proprietor).
     */
    public boolean isIndividual() {
        return type == OrganizationType.INDIVIDUAL;
    }

    /**
     * Check if organization is a formal business.
     */
    public boolean isBusiness() {
        return type == OrganizationType.BUSINESS;
    }

    /**
     * Get display name.
     */
    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : "Organization " + id;
    }
}
