package com.pml.identity.service;

import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Reads accounts and edits what an account may change about itself (ET-IDN-004).
 *
 * <p>An account is identified by its contacts, not by an email or a phone number stored on it: a
 * lookup by contact goes through {@code identity_contacts} by the contact's keyed hash. Contacts are
 * never written here - they are claimed by proving control of them - and status, suspension, deletion
 * and roles belong to {@link com.pml.identity.account.AccountService}, which this service delegates to
 * for roles so callers keep one facade.</p>
 */
public interface UserService {

    Mono<User> findById(String id);

    /** The account a token's {@code sub} belongs to. */
    Mono<User> findBySubject(String subject);

    Mono<User> findByUsername(String username);

    /** By the verified email contact; legacy accounts that still carry the address on the document are found too. */
    Mono<User> findByEmail(String email);

    /** By the verified WhatsApp contact; legacy accounts that still carry the number on the document are found too. */
    Mono<User> findByPhoneNumber(String phoneNumber);

    Flux<User> findAll();

    Flux<User> findByRole(UserType role);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsername(String username);

    /**
     * Creates a platform staff user: in the staff realm first (UPDATE_PASSWORD and CONFIGURE_TOTP
     * required at first sign-in), then the account. Buyers are never created here.
     */
    Mono<User> createStaffUser(String email, String firstName, String lastName, String temporaryPassword,
                               String actorId);

    /**
     * As {@link #createStaffUser(String, String, String, String, String)}, then records the person's
     * mobile number (normalised; a number that does not parse refuses the whole call before anything
     * is created) and grants {@code role} on top of the base CUSTOMER role when one is given.
     */
    Mono<User> createStaffUser(String email, String firstName, String lastName, String temporaryPassword,
                               String phoneNumber, com.pml.shared.constants.UserType role, String actorId);

    /** Soft-deletes the account (status DELETED, Keycloak disabled, sessions ended); nothing is erased. */
    Mono<User> deleteUser(String id, String actorId);

    /** Names and display name only; null leaves a field alone. Contacts are not editable here. */
    Mono<User> updateProfile(String id, User profileData);

    Mono<User> addRole(String userId, UserType role, String addedBy);

    Mono<User> removeRole(String userId, UserType role, String removedBy);

    Mono<User> setRoles(String userId, Set<UserType> roles, String updatedBy);

    Mono<Set<UserType>> getRoles(String userId);
}
