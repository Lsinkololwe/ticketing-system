package com.pml.identity.repository;

import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * User Repository
 *
 * Note: Users now have multiple roles stored in a 'roles' Set field.
 * The deprecated findByUserType methods are kept for backward compatibility.
 */
@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {

    Mono<User> findByUsername(String username);

    Mono<User> findByEmail(String email);

    Mono<User> findByPhoneNumber(String phoneNumber);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsername(String username);

    Mono<Boolean> existsByPhoneNumber(String phoneNumber);

    // ========================================================================
    // Multi-Role Queries
    // ========================================================================

    /**
     * Find all users who have a specific role.
     *
     * @param role the role to search for (as string, e.g., "CUSTOMER", "ORGANIZER")
     * @return Flux of users with the specified role
     */
    @Query("{ 'roles': ?0 }")
    Flux<User> findByRole(String role);

    /**
     * Find all users who have a specific role (using enum).
     *
     * @param role the role to search for
     * @return Flux of users with the specified role
     */
    default Flux<User> findByRole(UserType role) {
        return findByRole(role.name());
    }

    /**
     * Find all users who have any of the specified roles.
     *
     * @param roles list of role names to search for
     * @return Flux of users with any of the specified roles
     */
    @Query("{ 'roles': { $in: ?0 } }")
    Flux<User> findByRolesIn(List<String> roles);

    /**
     * Find all active users who have a specific role.
     *
     * @param role the role to search for
     * @return Flux of active users with the specified role
     */
    @Query("{ 'roles': ?0, 'active': true }")
    Flux<User> findByRoleAndActiveTrue(String role);

    /**
     * Find all active users who have a specific role (using enum).
     *
     * @param role the role to search for
     * @return Flux of active users with the specified role
     */
    default Flux<User> findByRoleAndActiveTrue(UserType role) {
        return findByRoleAndActiveTrue(role.name());
    }

    /**
     * Count users who have a specific role.
     *
     * @param role the role to count
     * @return Mono with the count
     */
    @Query(value = "{ 'roles': ?0 }", count = true)
    Mono<Long> countByRole(String role);

    /**
     * Count users who have a specific role (using enum).
     *
     * @param role the role to count
     * @return Mono with the count
     */
    default Mono<Long> countByRole(UserType role) {
        return countByRole(role.name());
    }

    /**
     * Count active users who have a specific role.
     *
     * @param role the role to count
     * @return Mono with the count
     */
    @Query(value = "{ 'roles': ?0, 'active': true }", count = true)
    Mono<Long> countByRoleAndActiveTrue(String role);

    // ========================================================================
    // Existing Queries
    // ========================================================================

    Flux<User> findByActiveTrue();

    Flux<User> findByEmailVerifiedFalse();

    Flux<User> findByPhoneVerifiedFalse();
}
