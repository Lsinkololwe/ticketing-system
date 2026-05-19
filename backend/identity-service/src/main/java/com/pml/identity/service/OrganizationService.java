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
 * Organizations are created automatically when an OrganizerProfile is approved.
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
     * Find organization by organizer profile ID.
     * Use this to find the Organization created from a specific OrganizerProfile.
     *
     * @param organizerProfileId The OrganizerProfile ID
     * @return The Organization if found
     */
    Mono<Organization> findByOrganizerProfileId(String organizerProfileId);

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
     * Search organizations by name
     */
    Flux<Organization> searchByName(String query, OrganizationStatus status, Pageable pageable);

    /**
     * Count organizations by status
     */
    Mono<Long> countByStatus(OrganizationStatus status);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create organization (internal - called when organizer is approved)
     */
    Mono<Organization> createFromOrganizerProfile(String organizerProfileId, String ownerId);

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
