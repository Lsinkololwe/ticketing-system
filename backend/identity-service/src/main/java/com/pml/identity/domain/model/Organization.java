package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.valueobject.OrganizationStats;

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
 * An Organization is created automatically when an OrganizerProfile is approved.
 *
 * ARCHITECTURE NOTES:
 * ==================
 * 1. Each Organization has exactly ONE owner (ownerId)
 * 2. Organization is linked to Keycloak via a group structure:
 *    /organizations/{slug}/owners
 *    /organizations/{slug}/admins
 *    /organizations/{slug}/managers
 *    /organizations/{slug}/marketers
 *    /organizations/{slug}/contributors
 * 3. Events created by members are associated with the organization
 * 4. Financial data (escrow, payouts) is scoped to the organization
 *
 * CREATION FLOW:
 * ==============
 * 1. Admin approves OrganizerProfile
 * 2. System creates Organization with slug from companyName
 * 3. System creates OrganizationMember with OWNER role
 * 4. System creates Keycloak group structure
 * 5. System adds user to /organizations/{slug}/owners group
 */
@Document(collection = "organizations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    private String id;

    /**
     * Display name of the organization
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
     * URL to organization logo
     */
    private String logoUrl;

    /**
     * URL to organization banner image
     */
    private String bannerUrl;

    /**
     * Links to the OrganizerProfile that created this organization
     */
    @Indexed
    private String organizerProfileId;

    /**
     * Keycloak group ID for this organization
     */
    private String keycloakGroupId;

    /**
     * User ID of the organization owner (only ONE owner allowed)
     */
    @NotBlank(message = "Owner is required")
    @Indexed
    private String ownerId;

    /**
     * Organization status
     */
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    /**
     * Whether the business is verified
     */
    @Builder.Default
    private boolean verified = false;

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

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Check if organization is active
     */
    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    /**
     * Check if organization can create events
     */
    public boolean canCreateEvents() {
        return status == OrganizationStatus.ACTIVE;
    }

    /**
     * Check if organization can accept payments
     */
    public boolean canAcceptPayments() {
        return status == OrganizationStatus.ACTIVE && verified;
    }
}
