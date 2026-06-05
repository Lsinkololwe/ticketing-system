package com.pml.identity.service;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.User;
import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import reactor.core.publisher.Mono;

/**
 * Service for handling organization onboarding with approval workflow.
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
 */
public interface OrganizationOnboardingService {

    // =========================================================================
    // USER OPERATIONS
    // =========================================================================

    /**
     * Apply to become an organizer.
     * Creates a new organization in DRAFT status.
     *
     * @param userId The user's Keycloak ID
     * @param input Initial organization details (name, description, etc.)
     * @return The newly created organization in DRAFT status
     */
    Mono<Organization> applyToBeOrganizer(String userId, OrganizationApplicationInput input);

    /**
     * Update organization application details.
     * Only allowed when status is DRAFT or CHANGES_REQUESTED.
     *
     * @param organizationId The organization ID
     * @param input Updated organization details
     * @return The updated organization
     */
    Mono<Organization> updateApplication(String organizationId, OrganizationApplicationInput input);

    /**
     * Submit organization application for admin review.
     * Changes status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW.
     *
     * @param organizationId The organization ID
     * @return The organization with PENDING_REVIEW status
     */
    Mono<Organization> submitForReview(String organizationId);

    /**
     * Get or create an organization for a user.
     * If user has no organization, creates one in DRAFT status.
     *
     * @param userId The user's Keycloak ID
     * @return The user's organization (existing or newly created)
     */
    Mono<Organization> getOrCreateOrganization(String userId);

    /**
     * Create an organization for a user in DRAFT status.
     *
     * @param user The user to create an organization for
     * @return The newly created organization in DRAFT status
     */
    Mono<Organization> createOrganization(User user);

    /**
     * Upgrade an individual organization to a business organization.
     *
     * @param organizationId The organization to upgrade
     * @param businessName The formal business name
     * @return The upgraded organization
     */
    Mono<Organization> upgradeToBusinessOrganization(String organizationId, String businessName);

    /**
     * Check if a user has an organization.
     *
     * @param userId The user's Keycloak ID
     * @return true if the user has an organization
     */
    Mono<Boolean> hasOrganization(String userId);

    /**
     * Get the organization for a user, if it exists.
     *
     * @param userId The user's Keycloak ID
     * @return The organization, or empty if user has no organization
     */
    Mono<Organization> findOrganizationByOwnerId(String userId);

    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================

    /**
     * Approve an organization application.
     * Changes status from PENDING_REVIEW to APPROVED.
     *
     * @param organizationId The organization ID
     * @param adminId The admin's Keycloak ID
     * @return The approved organization
     */
    Mono<Organization> approve(String organizationId, String adminId);

    /**
     * Request changes to an organization application.
     * Changes status from PENDING_REVIEW to CHANGES_REQUESTED.
     *
     * @param organizationId The organization ID
     * @param reason The reason for requesting changes
     * @param adminId The admin's Keycloak ID
     * @return The organization with CHANGES_REQUESTED status
     */
    Mono<Organization> requestChanges(String organizationId, String reason, String adminId);

    /**
     * Reject an organization application.
     * Changes status to REJECTED.
     *
     * @param organizationId The organization ID
     * @param reason The reason for rejection
     * @param adminId The admin's Keycloak ID
     * @return The rejected organization
     */
    Mono<Organization> reject(String organizationId, String reason, String adminId);
}
