package com.pml.identity.repository;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.enums.OrganizationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Organization Repository
 */
@Repository
public interface OrganizationRepository extends ReactiveMongoRepository<Organization, String> {

    /**
     * Find organization by unique slug
     */
    Mono<Organization> findBySlug(String slug);

    /**
     * Check if slug exists
     */
    Mono<Boolean> existsBySlug(String slug);

    /**
     * Find organization by owner ID
     */
    Mono<Organization> findByOwnerId(String ownerId);

    /**
     * Check if owner already has an organization
     */
    Mono<Boolean> existsByOwnerId(String ownerId);

    /**
     * Find all organizations by status
     */
    Flux<Organization> findByStatus(OrganizationStatus status);

    /**
     * Find all active organizations
     */
    Flux<Organization> findByStatusOrderByCreatedAtDesc(OrganizationStatus status);

    /**
     * Find organizations by status with pagination
     */
    Flux<Organization> findByStatus(OrganizationStatus status, Pageable pageable);

    /**
     * Find all verified organizations
     */
    Flux<Organization> findByVerifiedTrue();

    /**
     * Search organizations by name (case-insensitive)
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    Flux<Organization> searchByName(String namePattern);

    /**
     * Search organizations by name with status filter
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'status': ?1 }")
    Flux<Organization> searchByNameAndStatus(String namePattern, OrganizationStatus status, Pageable pageable);

    /**
     * Count organizations by status
     */
    Mono<Long> countByStatus(OrganizationStatus status);

    /**
     * Count all active organizations
     */
    @Query(value = "{ 'status': 'ACTIVE' }", count = true)
    Mono<Long> countActive();

    /**
     * Find organizations by multiple statuses (for approval workflow)
     */
    Flux<Organization> findByStatusIn(java.util.List<OrganizationStatus> statuses);
}
