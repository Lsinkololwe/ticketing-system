package com.pml.identity.service.impl;

import com.pml.identity.account.AccountService;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.ContactService;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.service.UserService;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.util.Emails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Accounts as the rest of identity reads them. Keycloak is never searched by email or phone from
 * here; the only email lookup is the contacts collection, and the legacy document field for
 * accounts that predate it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ContactService contacts;
    private final AccountService accounts;
    private final KeycloakUserAdminPort keycloak;
    private final KeycloakProperties keycloakProperties;
    private final ContactHasher contactHasher;
    private final Clock clock;

    // ---- reads -------------------------------------------------------------------------------------

    /**
     * By account id, else by Keycloak user id. A token's {@code sub} is the Keycloak id, which equals
     * the account id only for accounts that predate ET-IDN-004, so code that passes the subject on as
     * "the user id" (memberships, invitations, entity references) keeps finding the account.
     */
    @Override
    public Mono<User> findById(String id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> userRepository.findByKeycloakUserId(id)));
    }

    @Override
    public Mono<User> findBySubject(String subject) {
        return accounts.bySubject(subject);
    }

    @Override
    public Mono<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Mono<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Mono.empty();
        }
        return contacts.accountByContact(email, ContactType.EMAIL)
                .switchIfEmpty(Mono.defer(() -> Emails.normalize(email)
                        .map(userRepository::findByEmail).orElse(Mono.empty())))
                .switchIfEmpty(Mono.defer(() -> userRepository.findByEmail(email.trim())));
    }

    @Override
    public Mono<User> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Mono.empty();
        }
        return contacts.accountByContact(phoneNumber, ContactType.WHATSAPP)
                .switchIfEmpty(Mono.defer(() -> userRepository.findByPhoneNumber(phoneNumber.trim())));
    }

    @Override
    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Flux<User> findByRole(UserType role) {
        return userRepository.findByRole(role);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return findByEmail(email).hasElement();
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    // ---- writes ------------------------------------------------------------------------------------

    @Override
    public Mono<User> createStaffUser(String email, String firstName, String lastName, String temporaryPassword,
                                      String actorId) {
        String normalized = Emails.normalize(email).orElse(null);
        if (normalized == null) {
            return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_INVALID, "a staff user needs a valid email"));
        }
        return findByEmail(normalized)
                .flatMap(existing -> Mono.<User>error(new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT,
                        "a user with this email already exists")))
                .switchIfEmpty(Mono.defer(() -> keycloak.createStaffUser(normalized, normalized, firstName, lastName,
                                temporaryPassword)
                        .flatMap(keycloakUserId -> {
                            var now = clock.instant();
                            User staff = User.builder()
                                    .id(keycloakUserId)
                                    .keycloakUserId(keycloakUserId)
                                    .username(normalized)
                                    .email(normalized)
                                    .firstName(firstName)
                                    .lastName(lastName)
                                    .roles(EnumSet.of(UserType.CUSTOMER))
                                    .createdVia(AccountService.STAFF_ADMIN)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .createdBy(actorId)
                                    .build();
                            AccountStates.apply(staff, AccountState.ACTIVE);
                            return userRepository.save(staff);
                        })
                        .onErrorMap(DuplicateKeyException.class, duplicate -> new TranslatedRefusal(
                                ErrorCode.RESOURCE_CONFLICT, "a user with this email already exists"))));
    }

    @Override
    public Mono<User> createStaffUser(String email, String firstName, String lastName, String temporaryPassword,
                                      String phoneNumber, UserType role, String actorId) {
        Optional<ContactHasher.Normalized> phone = phoneNumber == null || phoneNumber.isBlank()
                ? Optional.empty()
                : contactHasher.normalize(phoneNumber, ContactType.WHATSAPP, null, null);
        if (phoneNumber != null && !phoneNumber.isBlank() && phone.isEmpty()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.PHONE_NUMBER_INVALID, "the staff member's number is not valid"));
        }
        return createStaffUser(email, firstName, lastName, temporaryPassword, actorId)
                .flatMap(created -> {
                    if (phone.isEmpty()) {
                        return Mono.just(created);
                    }
                    created.setPhoneNumber(phone.get().value());
                    created.setPhoneCountry(phone.get().region());
                    return userRepository.save(created)
                            .onErrorMap(DuplicateKeyException.class, duplicate -> new TranslatedRefusal(
                                    ErrorCode.RESOURCE_CONFLICT, "that number belongs to another account"));
                })
                .flatMap(created -> role == null || role == UserType.CUSTOMER
                        ? Mono.just(created)
                        : accounts.addRole(created.getId(), role, actorId));
    }

    @Override
    public Mono<User> deleteUser(String id, String actorId) {
        return accounts.delete(id, actorId, true);
    }

    @Override
    public Mono<User> updateProfile(String id, User profileData) {
        return userRepository.findById(id).flatMap(existing -> {
            if (profileData.getFirstName() != null) {
                existing.setFirstName(profileData.getFirstName());
            }
            if (profileData.getLastName() != null) {
                existing.setLastName(profileData.getLastName());
            }
            if (profileData.getDisplayName() != null) {
                existing.setDisplayName(profileData.getDisplayName());
            }
            if (profileData.getGender() != null) {
                existing.setGender(profileData.getGender());
            }
            existing.setUpdatedAt(clock.instant());
            return userRepository.save(existing);
        });
    }

    // ---- roles -------------------------------------------------------------------------------------

    @Override
    public Mono<User> addRole(String userId, UserType role, String addedBy) {
        return accounts.addRole(userId, role, addedBy);
    }

    @Override
    public Mono<User> removeRole(String userId, UserType role, String removedBy) {
        return accounts.removeRole(userId, role, removedBy);
    }

    @Override
    public Mono<User> setRoles(String userId, Set<UserType> roles, String updatedBy) {
        return accounts.setRoles(userId, roles, updatedBy);
    }

    @Override
    public Mono<Set<UserType>> getRoles(String userId) {
        return accounts.rolesOf(userId);
    }
}
