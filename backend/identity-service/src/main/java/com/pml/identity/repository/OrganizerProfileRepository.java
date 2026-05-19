package com.pml.identity.repository;

import com.pml.identity.domain.model.OrganizerProfile;
import com.pml.identity.domain.enums.OrganizerStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for OrganizerProfile documents.
 *
 * <p>Provides reactive CRUD operations and query methods
 * for organizer business profiles.</p>
 *
 * <h2>Field Naming</h2>
 * <p>The {@code userId} field IS the Keycloak user ID (sub claim).
 * Previously named {@code keycloakUserId}, renamed for consistency.</p>
 */
@Repository
public interface OrganizerProfileRepository extends ReactiveMongoRepository<OrganizerProfile, String> {

    /**
     * Find organizer profile by user ID (Keycloak user ID).
     *
     * @param userId The Keycloak user ID (sub claim)
     * @return The organizer profile if found
     */
    Mono<OrganizerProfile> findByUserId(String userId);

    /**
     * Check if profile exists for user.
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
     * @return Count of profiles with the given status
     */
    Mono<Long> countByStatus(OrganizerStatus status);

    /**
     * Find all verified organizers.
     *
     * @return Flux of verified profiles
     */
    Flux<OrganizerProfile> findByVerifiedTrue();

    /**
     * Find all profiles by status ordered by creation date (newest first).
     *
     * @param status The organizer status
     * @param pageable Pagination parameters
     * @return Flux of profiles ordered by createdAt descending
     */
    Flux<OrganizerProfile> findByStatusOrderByCreatedAtDesc(OrganizerStatus status, Pageable pageable);

    /**
     * Search organizers by company name (case insensitive).
     *
     * @param companyName Partial company name to search
     * @return Flux of matching profiles
     */
    Flux<OrganizerProfile> findByCompanyNameContainingIgnoreCase(String companyName);

    /**
     * Find organizers by city.
     *
     * @param city The city name
     * @return Flux of profiles in the given city
     */
    Flux<OrganizerProfile> findByCity(String city);

    /**
     * Find organizers by province.
     *
     * @param province The province name
     * @return Flux of profiles in the given province
     */
    Flux<OrganizerProfile> findByProvince(String province);

    /**
     * Find pending organizers ordered by creation date.
     *
     * @return Flux of profiles with PENDING_REVIEW status
     */
    default Flux<OrganizerProfile> findPendingOrganizers() {
        return findByStatusOrderByCreatedAtDesc(OrganizerStatus.PENDING_REVIEW, Pageable.unpaged());
    }

    /**
     * Delete profile by user ID.
     *
     * @param userId The Keycloak user ID
     * @return Mono completing when deletion is done
     */
    Mono<Void> deleteByUserId(String userId);
}
