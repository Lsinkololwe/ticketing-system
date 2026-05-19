package com.pml.identity.service;

import com.pml.identity.domain.model.OrganizerProfile;
import com.pml.identity.domain.enums.OrganizerStatus;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for OrganizerProfile operations.
 *
 * <p>This service manages the organizer application and approval workflow.
 * All GraphQL resolvers should use this service instead of accessing
 * the repository directly.</p>
 *
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li>CRUD operations for OrganizerProfile</li>
 *   <li>Application workflow (draft → pending → approved/rejected)</li>
 *   <li>Verification status management</li>
 *   <li>Integration with OrganizationService for approval workflow</li>
 * </ul>
 *
 * @see OrganizerProfile
 * @see OrganizationService For post-approval Organization creation
 */
public interface OrganizerProfileService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find organizer profile by ID.
     *
     * @param id The profile ID
     * @return The profile if found
     */
    Mono<OrganizerProfile> findById(String id);

    /**
     * Find organizer profile by user ID (Keycloak user ID).
     *
     * @param userId The Keycloak user ID
     * @return The profile if found
     */
    Mono<OrganizerProfile> findByUserId(String userId);

    /**
     * Check if a profile exists for the given user.
     *
     * @param userId The Keycloak user ID
     * @return true if profile exists
     */
    Mono<Boolean> existsByUserId(String userId);

    /**
     * Find all profiles by status.
     *
     * @param status The organizer status
     * @return Flux of profiles with the given status
     */
    Flux<OrganizerProfile> findByStatus(OrganizerStatus status);

    /**
     * Find all profiles by status with pagination.
     *
     * @param status The organizer status
     * @param pageable Pagination parameters
     * @return Flux of profiles with the given status
     */
    Flux<OrganizerProfile> findByStatus(OrganizerStatus status, Pageable pageable);

    /**
     * Count profiles by status.
     *
     * @param status The organizer status
     * @return Count of profiles
     */
    Mono<Long> countByStatus(OrganizerStatus status);

    /**
     * Search organizers by company name.
     *
     * @param companyName Partial company name to search
     * @return Flux of matching profiles
     */
    Flux<OrganizerProfile> searchByCompanyName(String companyName);

    /**
     * Find all organizer profiles.
     *
     * @return Flux of all profiles
     */
    Flux<OrganizerProfile> findAll();

    // ─────────────────────────────────────────────────────────────────────
    // Application Workflow
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create a new organizer profile application (status: DRAFT).
     *
     * @param userId The Keycloak user ID
     * @return The created profile
     * @throws IllegalStateException if profile already exists for user
     */
    Mono<OrganizerProfile> applyToBeOrganizer(String userId);

    /**
     * Update organizer profile details.
     *
     * @param id The profile ID
     * @param profile Updated profile data
     * @return The updated profile
     */
    Mono<OrganizerProfile> updateProfile(String id, OrganizerProfile profile);

    /**
     * Submit the application for review (status: PENDING_REVIEW).
     *
     * @param id The profile ID
     * @return The updated profile
     * @throws IllegalStateException if profile is not in DRAFT status
     */
    Mono<OrganizerProfile> submitForReview(String id);

    /**
     * Request changes from the organizer (status: CHANGES_REQUESTED).
     *
     * @param id The profile ID
     * @param reason Reason for requesting changes
     * @param adminId The admin making the request
     * @return The updated profile
     */
    Mono<OrganizerProfile> requestChanges(String id, String reason, String adminId);

    /**
     * Approve the organizer application (status: APPROVED).
     * This also creates the Organization entity.
     *
     * @param id The profile ID
     * @param adminId The admin approving the application
     * @return The updated profile
     * @throws IllegalStateException if profile is not in PENDING_REVIEW status
     */
    Mono<OrganizerProfile> approve(String id, String adminId);

    /**
     * Reject the organizer application (status: REJECTED).
     *
     * @param id The profile ID
     * @param reason Reason for rejection
     * @param adminId The admin rejecting the application
     * @return The updated profile
     */
    Mono<OrganizerProfile> reject(String id, String reason, String adminId);

    /**
     * Suspend an approved organizer (status: SUSPENDED).
     *
     * @param id The profile ID
     * @param reason Reason for suspension
     * @param adminId The admin suspending the organizer
     * @return The updated profile
     */
    Mono<OrganizerProfile> suspend(String id, String reason, String adminId);

    /**
     * Unsuspend a suspended organizer (status: APPROVED).
     *
     * @param id The profile ID
     * @param adminId The admin unsuspending the organizer
     * @return The updated profile
     */
    Mono<OrganizerProfile> unsuspend(String id, String adminId);

    // ─────────────────────────────────────────────────────────────────────
    // Verification
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Mark the business as verified.
     *
     * @param id The profile ID
     * @param adminId The admin performing verification
     * @return The updated profile
     */
    Mono<OrganizerProfile> verifyBusiness(String id, String adminId);

    /**
     * Mark all documents as verified.
     *
     * @param id The profile ID
     * @param adminId The admin performing verification
     * @return The updated profile
     */
    Mono<OrganizerProfile> verifyDocuments(String id, String adminId);

    /**
     * Mark bank account as verified.
     *
     * @param id The profile ID
     * @param adminId The admin performing verification
     * @return The updated profile
     */
    Mono<OrganizerProfile> verifyBankAccount(String id, String adminId);

    // ─────────────────────────────────────────────────────────────────────
    // Delete Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Delete an organizer profile.
     * Only allowed for profiles in DRAFT or REJECTED status.
     *
     * @param id The profile ID
     * @return Mono completing when deletion is done
     */
    Mono<Void> delete(String id);

    /**
     * Delete organizer profile by user ID.
     *
     * @param userId The Keycloak user ID
     * @return Mono completing when deletion is done
     */
    Mono<Void> deleteByUserId(String userId);
}
