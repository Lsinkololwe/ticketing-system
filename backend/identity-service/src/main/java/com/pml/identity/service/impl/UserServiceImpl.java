package com.pml.identity.service.impl;

import com.pml.identity.account.AccountService;
import com.pml.identity.account.ContactService;
import com.pml.identity.account.StaffAccountCreator;
import com.pml.identity.account.StaffAccountPassword;
import com.pml.identity.account.StaffAccountRequest;
import com.pml.identity.account.StaffPasswordReset;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.UserService;
import com.pml.shared.constants.UserType;
import com.pml.shared.util.Emails;
import com.pml.shared.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
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
    private final StaffAccountCreator staffAccounts;
    private final StaffPasswordReset staffPasswordReset;
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
                        .map(userRepository::findByEmail).orElse(Mono.empty())));
    }

    @Override
    public Mono<User> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Mono.empty();
        }
        return contacts.accountByContact(phoneNumber, ContactType.WHATSAPP)
                .switchIfEmpty(Mono.defer(() -> PhoneNumbers.parseMobile(phoneNumber, PhoneNumbers.DEFAULT_REGION)
                        .map(parsed -> userRepository.findByPhoneNumber(parsed.e164())).orElse(Mono.empty())));
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
    public Mono<StaffAccountPassword> createStaffUser(String email, String firstName, String lastName,
                                                      String phoneNumber, UserType role, String actorId) {
        return staffAccounts.create(new StaffAccountRequest(email, firstName, lastName, phoneNumber, role, actorId));
    }

    @Override
    public Mono<StaffAccountPassword> resetStaffTemporaryPassword(String userId, String actorId) {
        return staffPasswordReset.reset(userId, actorId);
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
