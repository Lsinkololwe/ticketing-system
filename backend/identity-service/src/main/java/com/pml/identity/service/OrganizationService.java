package com.pml.identity.service;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.enums.OrganizationStatus;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Organization Service Interface
 *
 * Manages organization lifecycle and operations.
 *
 * PROGRESSIVE ONBOARDING (Industry Standard):
 * Organizations are created LAZILY via OrganizationOnboardingService when a user
 * creates their first event. This interface manages existing organizations.
 *
 * @see OrganizationOnboardingService For lazy organization creation
 */
public interface OrganizationService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find organization by ID
     */
    Mono<Organization> findById(String id);

    /**
     * Find organization by slug
     */
    Mono<Organization> findBySlug(String slug);

    /**
     * Find organization by owner ID
     */
    Mono<Organization> findByOwnerId(String ownerId);

    /**
     * Find all organizations with pagination (admin only)
     */
    Flux<Organization> findAll(Pageable pageable);

    /**
     * Find all organizations (admin only)
     */
    Flux<Organization> findAll();

    /**
     * Find organizations by status
     */
    Flux<Organization> findByStatus(OrganizationStatus status, Pageable pageable);

    /**
     * Find organizations by status (no pagination)
     */
    Flux<Organization> findByStatus(OrganizationStatus status);

    /**
     * Find organizations in approval workflow (DRAFT, PENDING_REVIEW, CHANGES_REQUESTED, APPROVED, REJECTED)
     */
    Flux<Organization> findInApprovalWorkflow();

    /**
     * Search organizations by name
     */
    Flux<Organization> searchByName(String query, OrganizationStatus status, Pageable pageable);

    /**
     * Count organizations by status
     */
    Mono<Long> countByStatus(OrganizationStatus status);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // NOTE: Organization creation is now handled by OrganizationOnboardingService
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Update organization details
     */
    Mono<Organization> update(String id, String name, String description, String logoUrl, String bannerUrl);

    /**
     * Update organization settings
     */
    Mono<Organization> updateSettings(String id, OrganizationSettings settings);

    /**
     * Update organization status
     */
    Mono<Organization> updateStatus(String id, OrganizationStatus status);

    /**
     * Suspend organization (admin action)
     */
    Mono<Organization> suspend(String id, String reason);

    /**
     * Unsuspend organization (admin action)
     */
    Mono<Organization> unsuspend(String id);

    /**
     * Transfer ownership (updates ownerId)
     */
    Mono<Organization> transferOwnership(String id, String newOwnerId);

    /**
     * Update organization statistics (called asynchronously)
     */
    Mono<Organization> updateStats(String id, int memberCount, int totalEvents, int totalTicketsSold);

    // ─────────────────────────────────────────────────────────────────────
    // Utility Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generate unique slug from name
     */
    Mono<String> generateUniqueSlug(String name);

    /**
     * Check if slug is available
     */
    Mono<Boolean> isSlugAvailable(String slug);
}
