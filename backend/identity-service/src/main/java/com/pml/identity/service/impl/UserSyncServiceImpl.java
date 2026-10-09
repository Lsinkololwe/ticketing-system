package com.pml.identity.service.impl;

import com.pml.identity.account.AccountService;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.account.KeycloakUserAdminPort.KeycloakUserView;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * See {@link com.pml.identity.service.UserSyncService} for the rules this implements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncServiceImpl implements com.pml.identity.service.UserSyncService {

    private final ReactiveMongoTemplate template;
    private final KeycloakUserAdminPort keycloak;
    private final AccountService accounts;
    private final KeycloakProperties properties;
    private final Clock clock;

    @Override
    public Mono<User> syncUser(String realm, String keycloakUserId) {
        String realmName = realmName(realm);
        boolean staff = properties.getStaffRealm().equals(realmName);
        return keycloak.readUser(realmName, keycloakUserId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Keycloak holds no user {} in realm {}; nothing to sync", keycloakUserId, realmName);
                    return Mono.empty();
                }))
                .flatMap(kcUser -> accounts.bySubject(kcUser.id())
                        .flatMap(account -> staff || isKeycloakAuthoritative(account)
                                ? updateFromKeycloak(account, kcUser, staff)
                                : link(account, kcUser))
                        .switchIfEmpty(Mono.defer(() -> staff ? createStaff(kcUser) : adoptBuyer(kcUser))));
    }

    /** Staff and legacy accounts take their roles and standing from Keycloak; accounts born of a contact are ours. */
    private static boolean isKeycloakAuthoritative(User account) {
        return !"OTP".equals(account.getCreatedVia());
    }

    // ---- staff: Keycloak is the source -------------------------------------------------------------

    private Mono<User> createStaff(KeycloakUserView kcUser) {
        Instant now = clock.instant();
        User staff = User.builder()
                .id(kcUser.id())
                .keycloakUserId(kcUser.id())
                .username(kcUser.username())
                .email(kcUser.email())
                .firstName(kcUser.firstName())
                .lastName(kcUser.lastName())
                .emailVerified(kcUser.emailVerified())
                .roles(rolesOf(kcUser))
                .createdVia(AccountService.STAFF_SYNC)
                .createdAt(now)
                .updatedAt(now)
                .build();
        AccountStates.apply(staff, kcUser.enabled() ? AccountState.ACTIVE : AccountState.SUSPENDED);
        log.info("Creating staff account for Keycloak user {}", kcUser.id());
        return template.insert(staff)
                .onErrorResume(DuplicateKeyException.class, concurrent -> accounts.bySubject(kcUser.id()));
    }

    private Mono<User> updateFromKeycloak(User account, KeycloakUserView kcUser, boolean staff) {
        account.setKeycloakUserId(kcUser.id());
        account.setRoles(rolesOf(kcUser));
        account.setUpdatedAt(clock.instant());
        if (staff) {
            account.setEmail(kcUser.email());
            account.setFirstName(kcUser.firstName());
            account.setLastName(kcUser.lastName());
            account.setEmailVerified(kcUser.emailVerified());
            AccountState state = AccountStates.of(account);
            if (!kcUser.enabled() && state == AccountState.ACTIVE) {
                AccountStates.apply(account, AccountState.SUSPENDED);
            } else if (kcUser.enabled() && state == AccountState.SUSPENDED) {
                AccountStates.apply(account, AccountState.ACTIVE);
            }
        }
        return template.save(account);
    }

    // ---- buyers: ours ------------------------------------------------------------------------------

    /** A buyer-realm user with no account: linked if its username is an unlinked account's id, else an orphan. */
    private Mono<User> adoptBuyer(KeycloakUserView kcUser) {
        String username = kcUser.username();
        Mono<User> byUsername = username == null ? Mono.empty() : template.findById(username, User.class);
        return byUsername
                .filter(account -> account.getKeycloakUserId() == null || account.getKeycloakUserId().isBlank())
                .flatMap(account -> link(account, kcUser))
                .switchIfEmpty(Mono.defer(() -> orphan(kcUser)));
    }

    private Mono<User> link(User account, KeycloakUserView kcUser) {
        if (kcUser.id().equals(account.getKeycloakUserId())) {
            return Mono.just(account);
        }
        if (account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank()) {
            log.warn("Account {} is linked to another Keycloak user; user {} left alone", account.getId(), kcUser.id());
            return Mono.just(account);
        }
        account.setKeycloakUserId(kcUser.id());
        account.setUpdatedAt(clock.instant());
        return template.save(account);
    }

    private Mono<User> orphan(KeycloakUserView kcUser) {
        log.warn("Keycloak user {} in the buyer realm has no account; recorded, not created", kcUser.id());
        return template.insert(AccountEvent.builder()
                        .id("orphan:" + kcUser.id())
                        .kind("ORPHAN_KEYCLOAK_USER")
                        .at(clock.instant())
                        .data(Map.of("keycloakUserId", kcUser.id()))
                        .build())
                .onErrorResume(DuplicateKeyException.class, seen -> Mono.empty())
                .then(Mono.<User>empty());
    }

    // ---- delete, login, failure --------------------------------------------------------------------

    @Override
    public Mono<Void> markDeleted(String realm, String keycloakUserId) {
        return accounts.bySubject(keycloakUserId)
                .flatMap(account -> accounts.delete(account.getId(), "keycloak", false))
                .then();
    }

    @Override
    public Mono<User> updateLastLogin(String realm, String keycloakUserId) {
        return accounts.bySubject(keycloakUserId).flatMap(account -> {
            account.setLastLoginAt(clock.instant());
            return template.save(account);
        });
    }

    @Override
    public Mono<Void> recordFailure(String realm, String keycloakUserId, String kind, String reason) {
        log.error("Keycloak {} change for user {} could not be applied: {}", kind, keycloakUserId, reason);
        return template.insert(AccountEvent.builder()
                        .id("sync-failed:" + keycloakUserId + ":" + clock.millis())
                        .kind("SYNC_FAILED")
                        .at(clock.instant())
                        .data(Map.of("keycloakUserId", keycloakUserId, "change", String.valueOf(kind),
                                "reason", String.valueOf(reason)))
                        .build())
                .then();
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private String realmName(String realm) {
        return realm == null || realm.isBlank() ? properties.getRealm() : realm;
    }

    /** CUSTOMER plus every realm role that is a platform role. Attributes are never consulted. */
    private static Set<UserType> rolesOf(KeycloakUserView kcUser) {
        Set<UserType> roles = EnumSet.of(UserType.CUSTOMER);
        for (String name : kcUser.realmRoles()) {
            try {
                roles.add(UserType.valueOf(name));
            } catch (IllegalArgumentException notAPlatformRole) {
                // default-roles-*, offline_access, uma_authorization ... carry no platform meaning
            }
        }
        return roles;
    }
}
