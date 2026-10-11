package com.pml.identity.infrastructure.keycloak;

import com.pml.identity.account.KeycloakAccountPort;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.config.KeycloakProperties;
import com.pml.shared.constants.UserType;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The only writer of Keycloak users and roles (CONTRACT 1, 9).
 *
 * <h2>What changed from the email-keyed client</h2>
 * <ul>
 *   <li>Every operation is addressed by Keycloak id, or by {@code username = accountId}. Nothing
 *       looks a user up by email: an email is one person's contact, not an identity, and adopting
 *       "the user with this email" on a 409 is how one account ends up signed in as another.</li>
 *   <li>A 409 on create is answered by reading back <em>the user with that exact username</em>,
 *       which can only be this account's own earlier attempt.</li>
 *   <li>Keycloak's {@code PUT /users/{id}} replaces the user (attributes included), so every update
 *       first reads the full representation from the server and sends the merged result.</li>
 *   <li>There is no delete. A removed person is disabled; an erasure is a separate, audited step.</li>
 *   <li>Writes raise. A caller that never sees the failure cannot retry it.</li>
 * </ul>
 *
 * <p>Calls are blocking; each is moved onto {@code boundedElastic}.
 */
@Slf4j
@Service
public class KeycloakService implements KeycloakAccountPort, KeycloakUserAdminPort {

    /** The attribute that links a Keycloak user to its account. */
    public static final String ACCOUNT_ID_ATTRIBUTE = "accountId";

    private final KeycloakProperties properties;
    private final Supplier<Keycloak> clientFactory;
    private volatile Keycloak client;

    @Autowired
    public KeycloakService(KeycloakProperties properties) {
        this(properties, () -> build(properties));
    }

    /** For tests: a client the caller built. */
    public KeycloakService(KeycloakProperties properties, Supplier<Keycloak> clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    private static Keycloak build(KeycloakProperties properties) {
        if (isBlank(properties.getServerUrl())) {
            throw new KeycloakWriteFailed("keycloak.server-url is not configured");
        }
        if (isBlank(properties.getAdminUsername()) || isBlank(properties.getAdminPassword())) {
            throw new KeycloakWriteFailed("keycloak.admin-username and keycloak.admin-password are not configured");
        }
        return KeycloakBuilder.builder()
                .serverUrl(properties.getServerUrl())
                .realm(properties.getAdminRealm())
                .grantType(OAuth2Constants.PASSWORD)
                .clientId("admin-cli")
                .username(properties.getAdminUsername())
                .password(properties.getAdminPassword())
                .build();
    }

    private Keycloak keycloak() {
        Keycloak current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = clientFactory.get();
                    client = current;
                    log.info("Keycloak admin client ready for realm {}", properties.getRealm());
                }
            }
        }
        return current;
    }

    private RealmResource buyers() {
        return keycloak().realm(properties.getRealm());
    }

    private RealmResource realm(String name) {
        return keycloak().realm(name == null || name.isBlank() ? properties.getRealm() : name);
    }

    private static <T> Mono<T> blocking(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ========================================================================
    // KeycloakAccountPort · what the account workflow activities use
    // ========================================================================

    @Override
    public Mono<String> createUser(String accountId, boolean enabled, UserType role) {
        return blocking(() -> {
            if (isBlank(accountId)) {
                throw new IllegalArgumentException("an account id is required to create a Keycloak user");
            }
            UsersResource users = buyers().users();
            UserRepresentation user = new UserRepresentation();
            // The id is Keycloak's to mint: a supplied one is ignored on create (verified against 26.5.2),
            // so the token's sub is the Keycloak id, not the account id. Accounts link to it by keycloakUserId.
            user.setUsername(accountId);
            user.setEnabled(enabled);
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put(ACCOUNT_ID_ATTRIBUTE, List.of(accountId));
            user.setAttributes(attributes);

            String keycloakUserId;
            try (Response response = users.create(user)) {
                int status = response.getStatus();
                if (status == 201) {
                    String location = response.getHeaderString("Location");
                    keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
                } else if (status == 409) {
                    // Exactly this username, which only this account's own earlier attempt can hold.
                    keycloakUserId = exactUser(users, accountId)
                            .map(UserRepresentation::getId)
                            .orElseThrow(() -> new KeycloakWriteFailed(
                                    "Keycloak answered 409 for account " + accountId + " but holds no such username"));
                } else {
                    throw new KeycloakWriteFailed("Keycloak refused to create the user of account " + accountId
                            + ": HTTP " + status);
                }
            }
            if (role != null) {
                grantRole(buyers(), keycloakUserId, role.name());
            }
            return keycloakUserId;
        });
    }

    @Override
    public Mono<Void> applyAttributesAndRoles(String keycloakUserId, AccountAttributes attributes, Set<UserType> roles) {
        return blocking(() -> {
            RealmResource realm = buyers();
            UserResource resource = realm.users().get(keycloakUserId);
            UserRepresentation user = fullRepresentation(resource, keycloakUserId);

            Map<String, List<String>> merged = user.getAttributes() == null
                    ? new HashMap<>() : new HashMap<>(user.getAttributes());
            merged.put(ACCOUNT_ID_ATTRIBUTE, List.of(attributes.accountId()));
            if (!isBlank(attributes.locale())) {
                merged.put("locale", List.of(attributes.locale()));
            }
            if (!isBlank(attributes.displayName())) {
                merged.put("displayName", List.of(attributes.displayName()));
            }
            user.setAttributes(merged);
            if (!isBlank(attributes.email())) {
                user.setEmail(attributes.email());
                user.setEmailVerified(attributes.emailVerified());
            }
            resource.update(user);

            if (roles != null && !roles.isEmpty()) {
                Set<String> held = resource.roles().realmLevel().listAll().stream()
                        .map(RoleRepresentation::getName).collect(Collectors.toSet());
                for (UserType role : roles) {
                    if (!held.contains(role.name())) {
                        grantRole(realm, keycloakUserId, role.name());
                    }
                }
            }
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<Void> setEmail(String keycloakUserId, String email, boolean emailVerified) {
        return blocking(() -> {
            UserResource resource = buyers().users().get(keycloakUserId);
            UserRepresentation user = fullRepresentation(resource, keycloakUserId);
            boolean has = !isBlank(email);
            user.setEmail(has ? email : null);
            user.setEmailVerified(has && emailVerified);
            resource.update(user);
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<KeycloakAccount> findByUsername(String username) {
        return blocking(() -> exactUser(buyers().users(), username)
                .map(user -> new KeycloakAccount(user.getId(), user.getUsername(),
                        Boolean.TRUE.equals(user.isEnabled()), Boolean.TRUE.equals(user.isEmailVerified())))
                .orElse(null));
    }

    @Override
    public Mono<Void> setEnabled(String keycloakUserId, boolean enabled) {
        return setEnabled(null, keycloakUserId, enabled);
    }

    // ========================================================================
    // KeycloakUserAdminPort
    // ========================================================================

    @Override
    public Mono<Void> endSessions(String realmName, String keycloakUserId) {
        return blocking(() -> {
            try {
                realm(realmName).users().get(keycloakUserId).logout();
            } catch (NotFoundException absent) {
                log.info("Keycloak user {} is absent; it has no sessions to end", keycloakUserId);
            }
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public reactor.core.publisher.Flux<SessionView> sessions(String realmName, String keycloakUserId) {
        return blocking(() -> {
            try {
                return realm(realmName).users().get(keycloakUserId).getUserSessions().stream()
                        .map(session -> new SessionView(
                                session.getId(),
                                session.getIpAddress(),
                                session.getStart() <= 0 ? null : java.time.Instant.ofEpochMilli(session.getStart()),
                                session.getLastAccess() <= 0 ? null : java.time.Instant.ofEpochMilli(session.getLastAccess()),
                                session.getClients() == null ? java.util.List.<String>of()
                                        : java.util.List.copyOf(session.getClients().values())))
                        .toList();
            } catch (NotFoundException absent) {
                return java.util.List.<SessionView>of();
            }
        }).flatMapMany(reactor.core.publisher.Flux::fromIterable);
    }

    @Override
    public Mono<Void> endSession(String realmName, String sessionId) {
        return blocking(() -> {
            try {
                realm(realmName).deleteSession(sessionId, false);
            } catch (NotFoundException gone) {
                log.info("Keycloak session {} is already gone", sessionId);
            }
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<Void> grantRealmRole(String realmName, String keycloakUserId, String roleName) {
        return blocking(() -> {
            RealmResource realm = realm(realmName);
            // The caller holds the platform account id, which is the Keycloak username rather than its id.
            grantRole(realm, resolveUserId(realm, keycloakUserId).orElse(keycloakUserId), roleName);
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<Void> revokeRealmRole(String realmName, String keycloakUserId, String roleName) {
        return blocking(() -> {
            try {
                RealmResource realm = realm(realmName);
                RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
                realm.users().get(resolveUserId(realm, keycloakUserId).orElse(keycloakUserId)).roles().realmLevel()
                        .remove(Collections.singletonList(role));
            } catch (NotFoundException absent) {
                log.info("Role {} or user {} absent in Keycloak; nothing to revoke", roleName, keycloakUserId);
            }
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<Void> setEnabled(String realmName, String keycloakUserId, boolean enabled) {
        return blocking(() -> {
            UserResource resource = realm(realmName).users().get(keycloakUserId);
            UserRepresentation user = fullRepresentation(resource, keycloakUserId);
            user.setEnabled(enabled);
            resource.update(user);
            return Boolean.TRUE;
        }).then();
    }

    @Override
    public Mono<String> createStaffUser(String username, String email, String firstName, String lastName,
                                        String temporaryPassword) {
        return blocking(() -> {
            if (isBlank(username)) {
                throw new IllegalArgumentException("a staff user needs a username");
            }
            UsersResource users = realm(properties.getStaffRealm()).users();
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setRequiredActions(List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"));
            if (!isBlank(temporaryPassword)) {
                org.keycloak.representations.idm.CredentialRepresentation credential =
                        new org.keycloak.representations.idm.CredentialRepresentation();
                credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
                credential.setValue(temporaryPassword);
                credential.setTemporary(true);
                user.setCredentials(List.of(credential));
            }
            try (Response response = users.create(user)) {
                int status = response.getStatus();
                if (status == 201) {
                    String location = response.getHeaderString("Location");
                    return location.substring(location.lastIndexOf('/') + 1);
                }
                if (status == 409) {
                    return exactUser(users, username).map(UserRepresentation::getId)
                            .orElseThrow(() -> new KeycloakWriteFailed(
                                    "Keycloak answered 409 for a staff user but holds no such username"));
                }
                throw new KeycloakWriteFailed("Keycloak refused to create a staff user: HTTP " + status);
            }
        });
    }

    @Override
    public Mono<KeycloakUserView> readUser(String realmName, String keycloakUserId) {
        return blocking(() -> {
            try {
                UserResource resource = realm(realmName).users().get(keycloakUserId);
                return view(resource.toRepresentation(), realmRoleNames(resource));
            } catch (NotFoundException absent) {
                return null;
            }
        });
    }

    @Override
    public Mono<Optional<String>> readAttribute(String realmName, String keycloakUserId, String name) {
        return Mono.<Optional<String>>fromCallable(() -> {
            try {
                Map<String, List<String>> attributes = realm(realmName).users().get(keycloakUserId).toRepresentation().getAttributes();
                List<String> values = attributes == null ? null : attributes.get(name);
                return values == null || values.isEmpty() ? Optional.<String>empty() : Optional.of(values.get(0));
            } catch (NotFoundException absent) {
                return Optional.<String>empty();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ========================================================================
    // Groups · a projection of organization membership, written from MongoDB
    // ========================================================================

    /**
     * Creates {@code /organizations/{slug}} and its role subgroups where missing.
     *
     * @return the organization group's id
     */
    public Mono<String> ensureOrganizationGroupTree(String organizationSlug) {
        return blocking(() -> {
            RealmResource realm = buyers();
            String parent = requireGroup(findOrCreateGroup(realm, "organizations", null), "organizations");
            String organization = requireGroup(findOrCreateGroup(realm, organizationSlug, parent), organizationSlug);
            for (String roleGroup : com.pml.identity.domain.valueobject.OrganizationGroups.TREE) {
                requireGroup(findOrCreateGroup(realm, roleGroup, organization), roleGroup);
            }
            return organization;
        });
    }

    /** Adds a user to one role subgroup, raising when the group or the write is missing. */
    public Mono<Void> joinOrganizationGroup(String userId, String organizationSlug, String groupName) {
        return blocking(() -> {
            RealmResource realm = buyers();
            String path = "/organizations/" + organizationSlug + "/" + groupName;
            GroupRepresentation group;
            try {
                group = realm.getGroupByPath(path);
            } catch (NotFoundException absent) {
                throw new KeycloakWriteFailed("group " + path + " does not exist");
            }
            if (group == null) {
                throw new KeycloakWriteFailed("group " + path + " does not exist");
            }
            String keycloakUserId = resolveUserId(realm, userId)
                    .orElseThrow(() -> new KeycloakWriteFailed("no Keycloak user for account " + userId));
            realm.users().get(keycloakUserId).joinGroup(group.getId());
            return Boolean.TRUE;
        }).then();
    }

    /** Removes a user from one role subgroup; a group or user that does not exist holds nobody. */
    public Mono<Void> leaveOrganizationGroup(String userId, String organizationSlug, String groupName) {
        return blocking(() -> {
            RealmResource realm = buyers();
            String path = "/organizations/" + organizationSlug + "/" + groupName;
            try {
                GroupRepresentation group = realm.getGroupByPath(path);
                Optional<String> keycloakUserId = resolveUserId(realm, userId);
                if (group != null && keycloakUserId.isPresent()) {
                    realm.users().get(keycloakUserId.get()).leaveGroup(group.getId());
                }
            } catch (NotFoundException absent) {
                log.info("Group {} or user {} absent in Keycloak; nothing to leave", path, userId);
            }
            return Boolean.TRUE;
        }).then();
    }

    // ========================================================================
    // helpers
    // ========================================================================

    /**
     * The Keycloak id of an identity_users id: the id itself for an account that predates
     * ET-IDN-004, else the user whose username is the account id.
     */
    private Optional<String> resolveUserId(RealmResource realm, String userId) {
        if (isBlank(userId)) {
            return Optional.empty();
        }
        Optional<UserRepresentation> byUsername = exactUser(realm.users(), userId);
        if (byUsername.isPresent()) {
            return byUsername.map(UserRepresentation::getId);
        }
        try {
            return Optional.ofNullable(realm.users().get(userId).toRepresentation()).map(UserRepresentation::getId);
        } catch (NotFoundException absent) {
            return Optional.empty();
        }
    }

    private static Optional<UserRepresentation> exactUser(UsersResource users, String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        List<UserRepresentation> found = users.searchByUsername(username, true);
        return found.stream().filter(user -> username.equalsIgnoreCase(user.getUsername())).findFirst();
    }

    private static UserRepresentation fullRepresentation(UserResource resource, String keycloakUserId) {
        try {
            return resource.toRepresentation();
        } catch (NotFoundException absent) {
            throw new KeycloakWriteFailed("Keycloak holds no user " + keycloakUserId);
        }
    }

    private static void grantRole(RealmResource realm, String keycloakUserId, String roleName) {
        RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
        realm.users().get(keycloakUserId).roles().realmLevel().add(Collections.singletonList(role));
    }

    private static Set<String> realmRoleNames(UserResource resource) {
        Set<String> names = new HashSet<>();
        for (RoleRepresentation role : resource.roles().realmLevel().listAll()) {
            names.add(role.getName());
        }
        return names;
    }

    private static KeycloakUserView view(UserRepresentation user, Set<String> realmRoles) {
        return new KeycloakUserView(user.getId(), user.getUsername(), user.getEmail(), user.getFirstName(),
                user.getLastName(), Boolean.TRUE.equals(user.isEnabled()),
                Boolean.TRUE.equals(user.isEmailVerified()), realmRoles);
    }

    private static String requireGroup(String groupId, String name) {
        if (groupId == null) {
            throw new KeycloakWriteFailed("group " + name + " could not be found or created");
        }
        return groupId;
    }

    private String findOrCreateGroup(RealmResource realm, String groupName, String parentGroupId) {
        try {
            if (parentGroupId != null) {
                List<GroupRepresentation> subGroups = realm.groups().group(parentGroupId).getSubGroups(0, 100, true);
                for (GroupRepresentation subGroup : subGroups) {
                    if (subGroup.getName().equals(groupName)) {
                        return subGroup.getId();
                    }
                }
            } else {
                List<GroupRepresentation> topGroups = realm.groups().groups(groupName, 0, 1);
                if (!topGroups.isEmpty() && topGroups.get(0).getName().equals(groupName)) {
                    return topGroups.get(0).getId();
                }
            }

            GroupRepresentation newGroup = new GroupRepresentation();
            newGroup.setName(groupName);
            try (Response response = parentGroupId != null
                    ? realm.groups().group(parentGroupId).subGroup(newGroup)
                    : realm.groups().add(newGroup)) {
                if (response.getStatus() == 201) {
                    String location = response.getHeaderString("Location");
                    return location.substring(location.lastIndexOf('/') + 1);
                }
                log.warn("Failed to create group {}: {}", groupName, response.getStatus());
                return null;
            }
        } catch (RuntimeException e) {
            log.warn("Error finding/creating group {}: {}", groupName, e.getMessage());
            return null;
        }
    }
}
