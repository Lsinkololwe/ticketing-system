package com.pml.identity.infrastructure.keycloak;

import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.model.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing users in Keycloak via Admin API.
 * Provides user CRUD operations synchronized with MongoDB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final KeycloakProperties keycloakProperties;
    private Keycloak keycloak;

    @PostConstruct
    public void init() {
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakProperties.getServerUrl())
                .realm(keycloakProperties.getAdminRealm())
                .grantType(OAuth2Constants.PASSWORD)
                .clientId("admin-cli")
                .username(keycloakProperties.getAdminUsername())
                .password(keycloakProperties.getAdminPassword())
                .build();
        log.info("Keycloak Admin Client initialized for realm: {}", keycloakProperties.getRealm());
    }

    /**
     * Create a user in Keycloak.
     *
     * @param user     The user entity from MongoDB
     * @param password The plain text password (will be hashed by Keycloak)
     * @return Mono with the Keycloak user ID
     */
    public Mono<String> createUser(User user, String password) {
        return Mono.fromCallable(() -> {
            log.info("Creating user in Keycloak: {}", user.getEmail());

            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setEnabled(true);
            keycloakUser.setUsername(user.getUsername());
            keycloakUser.setEmail(user.getEmail());
            keycloakUser.setFirstName(user.getFirstName());
            keycloakUser.setLastName(user.getLastName());
            keycloakUser.setEmailVerified(user.isEmailVerified());

            // Set custom attributes
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("userId", Collections.singletonList(user.getId()));

            // Store roles as comma-separated string for backup/reference
            String rolesStr = user.getRoles() != null && !user.getRoles().isEmpty()
                    ? user.getRoles().stream()
                        .map(Enum::name)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("CUSTOMER")
                    : "CUSTOMER";
            attributes.put("roles", Collections.singletonList(rolesStr));

            if (user.getPhoneNumber() != null) {
                attributes.put("phoneNumber", Collections.singletonList(user.getPhoneNumber()));
                attributes.put("phoneVerified", Collections.singletonList(String.valueOf(user.isPhoneVerified())));
            }
            keycloakUser.setAttributes(attributes);

            // Set password credential
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            keycloakUser.setCredentials(Collections.singletonList(credential));

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            Response response = usersResource.create(keycloakUser);
            int status = response.getStatus();

            if (status == 201) {
                // Extract Keycloak user ID from Location header
                String location = response.getHeaderString("Location");
                String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
                log.info("User created in Keycloak with ID: {}", keycloakUserId);

                // Assign all roles from the user's role set
                if (user.getRoles() != null) {
                    for (var role : user.getRoles()) {
                        assignRole(keycloakUserId, role.name());
                    }
                } else {
                    // Default to CUSTOMER role
                    assignRole(keycloakUserId, "CUSTOMER");
                }

                return keycloakUserId;
            } else if (status == 409) {
                log.warn("User already exists in Keycloak: {}", user.getEmail());
                // Find existing user
                List<UserRepresentation> existingUsers = usersResource.searchByEmail(user.getEmail(), true);
                if (!existingUsers.isEmpty()) {
                    return existingUsers.get(0).getId();
                }
                throw new RuntimeException("User already exists but could not be found");
            } else {
                String errorMessage = response.readEntity(String.class);
                log.error("Failed to create user in Keycloak. Status: {}, Error: {}", status, errorMessage);
                throw new RuntimeException("Failed to create user in Keycloak: " + errorMessage);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Update a user in Keycloak.
     *
     * ARCHITECTURE NOTE: This syncs profile data from MongoDB to Keycloak.
     * - firstName, lastName: Synced for display purposes
     * - emailVerified: Synced for verification status
     * - isActive (MongoDB) → enabled (Keycloak): Controls authentication ability
     * - Attributes: roles, phoneNumber, phoneVerified
     *
     * Note: Role synchronization is handled separately via addRoleToUser/removeRoleFromUser
     * or syncUserRoles methods. This method only updates the 'roles' attribute for reference.
     *
     * @param user The updated user entity
     * @return Mono signaling completion
     */
    public Mono<Void> updateUser(User user) {
        return Mono.fromRunnable(() -> {
            log.info("Updating user in Keycloak: {}", user.getEmail());

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            // Find user by email
            List<UserRepresentation> users = usersResource.searchByEmail(user.getEmail(), true);
            if (users.isEmpty()) {
                log.warn("User not found in Keycloak: {}", user.getEmail());
                return;
            }

            UserRepresentation keycloakUser = users.get(0);
            UserResource userResource = usersResource.get(keycloakUser.getId());

            // Update user details
            // Note: isActive in MongoDB maps to enabled in Keycloak
            keycloakUser.setFirstName(user.getFirstName());
            keycloakUser.setLastName(user.getLastName());
            keycloakUser.setEnabled(user.isActive());
            keycloakUser.setEmailVerified(user.isEmailVerified());

            // Update attributes
            Map<String, List<String>> attributes = keycloakUser.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }

            // Store roles as comma-separated string for backup/reference
            String rolesStr = user.getRoles() != null && !user.getRoles().isEmpty()
                    ? user.getRoles().stream()
                        .map(Enum::name)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("CUSTOMER")
                    : "CUSTOMER";
            attributes.put("roles", Collections.singletonList(rolesStr));

            if (user.getPhoneNumber() != null) {
                attributes.put("phoneNumber", Collections.singletonList(user.getPhoneNumber()));
                attributes.put("phoneVerified", Collections.singletonList(String.valueOf(user.isPhoneVerified())));
            }
            keycloakUser.setAttributes(attributes);

            userResource.update(keycloakUser);
            log.info("User updated in Keycloak: {}", user.getEmail());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Sync user roles to Keycloak.
     * This method ensures the user has exactly the specified roles in Keycloak.
     *
     * @param keycloakUserId The Keycloak user ID
     * @param roles The set of roles the user should have
     * @return Mono signaling completion
     */
    public Mono<Void> syncUserRoles(String keycloakUserId, java.util.Set<com.pml.shared.constants.UserType> roles) {
        return getUserRoles(keycloakUserId)
                .flatMap(currentRoles -> {
                    // Convert current roles to UserType set
                    java.util.Set<String> targetRoleNames = roles.stream()
                            .map(Enum::name)
                            .collect(java.util.stream.Collectors.toSet());

                    java.util.Set<String> currentRoleNames = new java.util.HashSet<>(currentRoles);

                    // Roles to add
                    java.util.Set<String> rolesToAdd = new java.util.HashSet<>(targetRoleNames);
                    rolesToAdd.removeAll(currentRoleNames);

                    // Roles to remove
                    java.util.Set<String> rolesToRemove = new java.util.HashSet<>(currentRoleNames);
                    rolesToRemove.removeAll(targetRoleNames);

                    // Add new roles
                    reactor.core.publisher.Mono<Void> addRoles = reactor.core.publisher.Flux.fromIterable(rolesToAdd)
                            .flatMap(role -> addRoleToUser(keycloakUserId, role))
                            .then();

                    // Remove old roles
                    reactor.core.publisher.Mono<Void> removeRoles = reactor.core.publisher.Flux.fromIterable(rolesToRemove)
                            .flatMap(role -> removeRoleFromUser(keycloakUserId, role))
                            .then();

                    return reactor.core.publisher.Mono.when(addRoles, removeRoles);
                })
                .doOnSuccess(v -> log.info("Synced roles for user {}: {}", keycloakUserId, roles))
                .onErrorResume(e -> {
                    log.warn("Failed to sync roles for user {}: {}", keycloakUserId, e.getMessage());
                    return reactor.core.publisher.Mono.empty();
                });
    }

    /**
     * Delete a user from Keycloak.
     *
     * @param email The user's email
     * @return Mono signaling completion
     */
    public Mono<Void> deleteUser(String email) {
        return Mono.fromRunnable(() -> {
            log.info("Deleting user from Keycloak: {}", email);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                log.warn("User not found in Keycloak: {}", email);
                return;
            }

            String keycloakUserId = users.get(0).getId();
            usersResource.delete(keycloakUserId);
            log.info("User deleted from Keycloak: {}", email);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Find a user in Keycloak by email.
     *
     * @param email The user's email
     * @return Mono with optional UserRepresentation
     */
    public Mono<Optional<UserRepresentation>> findUserByEmail(String email) {
        return Mono.<Optional<UserRepresentation>>fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(users.get(0));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Find a user in Keycloak by ID.
     *
     * @param userId The Keycloak user ID
     * @return Mono with optional UserRepresentation
     */
    public Mono<Optional<UserRepresentation>> findUserById(String userId) {
        return Mono.<Optional<UserRepresentation>>fromCallable(() -> {
            try {
                RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
                UserResource userResource = realmResource.users().get(userId);
                UserRepresentation user = userResource.toRepresentation();
                return Optional.of(user);
            } catch (jakarta.ws.rs.NotFoundException e) {
                return Optional.empty();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Search users by query string (email, name, username).
     *
     * @param query      Search query
     * @param maxResults Maximum results to return
     * @return Flux of matching users
     */
    public Flux<UserRepresentation> searchUsers(String query, int maxResults) {
        return Mono.fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();
            return usersResource.search(query, 0, maxResults);
        }).subscribeOn(Schedulers.boundedElastic())
          .flatMapMany(Flux::fromIterable);
    }

    /**
     * Get all users with a specific realm role.
     *
     * @param roleName Role name
     * @return Flux of users with the role
     */
    public Flux<UserRepresentation> getUsersByRole(String roleName) {
        return Mono.fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            return realmResource.roles().get(roleName).getUserMembers();
        }).subscribeOn(Schedulers.boundedElastic())
          .flatMapMany(Flux::fromIterable);
    }

    /**
     * Add a realm role to a user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @param roleName       The role name to add
     * @return Mono signaling completion
     */
    public Mono<Void> addRoleToUser(String keycloakUserId, String roleName) {
        return Mono.fromRunnable(() -> {
            log.info("Adding role {} to user {}", roleName, keycloakUserId);
            assignRole(keycloakUserId, roleName);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Remove a realm role from a user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @param roleName       The role name to remove
     * @return Mono signaling completion
     */
    public Mono<Void> removeRoleFromUser(String keycloakUserId, String roleName) {
        return Mono.fromRunnable(() -> {
            log.info("Removing role {} from user {}", roleName, keycloakUserId);
            try {
                RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
                RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
                realmResource.users().get(keycloakUserId).roles().realmLevel()
                        .remove(Collections.singletonList(role));
                log.info("Removed role {} from user {}", roleName, keycloakUserId);
            } catch (Exception e) {
                log.warn("Failed to remove role {} from user {}: {}", roleName, keycloakUserId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Get all roles assigned to a user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return Mono with list of role names
     */
    public Mono<List<String>> getUserRoles(String keycloakUserId) {
        return Mono.fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            List<RoleRepresentation> roles = realmResource.users()
                    .get(keycloakUserId)
                    .roles()
                    .realmLevel()
                    .listEffective();
            return roles.stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !name.startsWith("default-roles-"))
                    .toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Update user attributes in Keycloak.
     *
     * @param keycloakUserId The Keycloak user ID
     * @param attributeName  The attribute name
     * @param attributeValue The attribute value
     * @return Mono signaling completion
     */
    public Mono<Void> updateUserAttribute(String keycloakUserId, String attributeName, String attributeValue) {
        return Mono.fromRunnable(() -> {
            log.info("Updating attribute {} for user {}", attributeName, keycloakUserId);
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UserResource userResource = realmResource.users().get(keycloakUserId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(attributeName, Collections.singletonList(attributeValue));
            user.setAttributes(attributes);

            userResource.update(user);
            log.info("Updated attribute {} for user {}", attributeName, keycloakUserId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Get all users with pagination.
     *
     * @param first      First result index
     * @param maxResults Maximum results to return
     * @return Flux of users
     */
    public Flux<UserRepresentation> getAllUsers(int first, int maxResults) {
        return Mono.fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            return realmResource.users().list(first, maxResults);
        }).subscribeOn(Schedulers.boundedElastic())
          .flatMapMany(Flux::fromIterable);
    }

    /**
     * Count total users in realm.
     *
     * @return Mono with user count
     */
    public Mono<Integer> countUsers() {
        return Mono.fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            return realmResource.users().count();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Send verification email to user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return Mono signaling completion
     */
    public Mono<Void> sendVerificationEmail(String keycloakUserId) {
        return Mono.fromRunnable(() -> {
            log.info("Sending verification email to user {}", keycloakUserId);
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            realmResource.users().get(keycloakUserId).sendVerifyEmail();
            log.info("Verification email sent to user {}", keycloakUserId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Send password reset email to user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return Mono signaling completion
     */
    public Mono<Void> sendPasswordResetEmail(String keycloakUserId) {
        return Mono.fromRunnable(() -> {
            log.info("Sending password reset email to user {}", keycloakUserId);
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            realmResource.users().get(keycloakUserId).executeActionsEmail(
                    Collections.singletonList("UPDATE_PASSWORD")
            );
            log.info("Password reset email sent to user {}", keycloakUserId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Find a user in Keycloak by username.
     *
     * @param username The username
     * @return Mono with optional UserRepresentation
     */
    public Mono<Optional<UserRepresentation>> findUserByUsername(String username) {
        return Mono.<Optional<UserRepresentation>>fromCallable(() -> {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByUsername(username, true);
            if (users.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(users.get(0));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Update user password in Keycloak.
     *
     * @param email       The user's email
     * @param newPassword The new password
     * @return Mono signaling completion
     */
    public Mono<Void> updatePassword(String email, String newPassword) {
        return Mono.fromRunnable(() -> {
            log.info("Updating password in Keycloak for: {}", email);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                throw new RuntimeException("User not found in Keycloak: " + email);
            }

            String keycloakUserId = users.get(0).getId();
            UserResource userResource = usersResource.get(keycloakUserId);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);

            userResource.resetPassword(credential);
            log.info("Password updated in Keycloak for: {}", email);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Assign a realm role to a user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @param roleName       The role name (e.g., "CUSTOMER", "ORGANIZER")
     */
    private void assignRole(String keycloakUserId, String roleName) {
        try {
            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            realmResource.users().get(keycloakUserId).roles().realmLevel()
                    .add(Collections.singletonList(role));
            log.info("Assigned role {} to user {}", roleName, keycloakUserId);
        } catch (Exception e) {
            log.warn("Failed to assign role {} to user {}: {}", roleName, keycloakUserId, e.getMessage());
        }
    }

    /**
     * Update user roles in Keycloak.
     *
     * @param email   The user's email
     * @param newRole The new role to assign
     * @return Mono signaling completion
     */
    public Mono<Void> updateUserRole(String email, String newRole) {
        return Mono.fromRunnable(() -> {
            log.info("Updating role for user {} to {}", email, newRole);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                throw new RuntimeException("User not found in Keycloak: " + email);
            }

            String keycloakUserId = users.get(0).getId();
            UserResource userResource = usersResource.get(keycloakUserId);

            // Remove existing realm roles (except default ones)
            List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();
            List<RoleRepresentation> rolesToRemove = currentRoles.stream()
                    .filter(r -> !r.getName().startsWith("default-roles-") &&
                            !r.getName().equals("offline_access") &&
                            !r.getName().equals("uma_authorization"))
                    .toList();
            if (!rolesToRemove.isEmpty()) {
                userResource.roles().realmLevel().remove(rolesToRemove);
            }

            // Assign new role
            assignRole(keycloakUserId, newRole);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Enable or disable a user in Keycloak.
     *
     * @param email   The user's email
     * @param enabled Whether the user should be enabled
     * @return Mono signaling completion
     */
    public Mono<Void> setUserEnabled(String email, boolean enabled) {
        return Mono.fromRunnable(() -> {
            log.info("Setting user {} enabled status to: {}", email, enabled);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
            UsersResource usersResource = realmResource.users();

            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            if (users.isEmpty()) {
                throw new RuntimeException("User not found in Keycloak: " + email);
            }

            UserRepresentation user = users.get(0);
            user.setEnabled(enabled);
            usersResource.get(user.getId()).update(user);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * End a user's session in Keycloak.
     *
     * @param sessionState The session state from the JWT
     * @return Mono signaling completion
     */
    public Mono<Void> endUserSession(String sessionState) {
        return Mono.fromRunnable(() -> {
            log.info("Ending user session: {}", sessionState);
            try {
                RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
                // Second parameter 'isOffline' - false for online sessions
                realmResource.deleteSession(sessionState, false);
                log.info("Session ended successfully: {}", sessionState);
            } catch (Exception e) {
                log.warn("Failed to end session {}: {}", sessionState, e.getMessage());
                // Session may have already expired, which is fine
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Update phone verification status for a user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @param verified       Whether the phone is verified
     * @return Mono signaling completion
     */
    public Mono<Void> updatePhoneVerified(String keycloakUserId, boolean verified) {
        return updateUserAttribute(keycloakUserId, "phoneVerified", String.valueOf(verified));
    }

    // ========================================================================
    // ORGANIZATION GROUP MANAGEMENT
    // ========================================================================

    /**
     * Create organization group structure in Keycloak.
     * Creates the main organization group and sub-groups for each role:
     * /organizations/{slug}/owners
     * /organizations/{slug}/admins
     * /organizations/{slug}/managers
     * /organizations/{slug}/marketers
     * /organizations/{slug}/contributors
     *
     * @param organizationSlug The organization's URL slug
     * @return Mono with the main group ID
     */
    public Mono<String> createOrganizationGroups(String organizationSlug) {
        return Mono.fromCallable(() -> {
            log.info("Creating Keycloak group structure for organization: {}", organizationSlug);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());

            // Find or create "organizations" parent group
            String organizationsGroupId = findOrCreateGroup(realmResource, "organizations", null);

            // Create organization-specific group under "organizations"
            String orgGroupId = findOrCreateGroup(realmResource, organizationSlug, organizationsGroupId);

            // Create role sub-groups
            String[] roleGroups = {"owners", "admins", "managers", "marketers", "contributors"};
            for (String roleGroup : roleGroups) {
                findOrCreateGroup(realmResource, roleGroup, orgGroupId);
            }

            log.info("Created Keycloak group structure for organization: {} with ID: {}", organizationSlug, orgGroupId);
            return orgGroupId;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Add a user to an organization role group in Keycloak.
     *
     * @param userId           The user ID (MongoDB ID)
     * @param organizationSlug The organization's URL slug
     * @param roleName         The role name (owners, admins, managers, etc.)
     * @return Mono signaling completion
     */
    public Mono<Void> addUserToOrganizationGroup(String userId, String organizationSlug, String roleName) {
        return Mono.fromRunnable(() -> {
            log.info("Adding user {} to organization {} group {}", userId, organizationSlug, roleName);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());

            // Find the user by MongoDB userId attribute
            List<UserRepresentation> users = realmResource.users()
                    .searchByAttributes("userId:" + userId);

            if (users.isEmpty()) {
                log.warn("User not found in Keycloak by userId attribute: {}", userId);
                return;
            }

            String keycloakUserId = users.get(0).getId();

            // Find the role group
            String groupPath = "/organizations/" + organizationSlug + "/" + roleName;
            try {
                org.keycloak.representations.idm.GroupRepresentation group =
                        realmResource.getGroupByPath(groupPath);
                if (group != null) {
                    realmResource.users().get(keycloakUserId).joinGroup(group.getId());
                    log.info("User {} added to group {}", userId, groupPath);
                } else {
                    log.warn("Group not found: {}", groupPath);
                }
            } catch (Exception e) {
                log.warn("Failed to add user to group {}: {}", groupPath, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Remove a user from an organization role group in Keycloak.
     *
     * @param userId           The user ID (MongoDB ID)
     * @param organizationSlug The organization's URL slug
     * @param roleName         The role name (owners, admins, managers, etc.)
     * @return Mono signaling completion
     */
    public Mono<Void> removeUserFromOrganizationGroup(String userId, String organizationSlug, String roleName) {
        return Mono.fromRunnable(() -> {
            log.info("Removing user {} from organization {} group {}", userId, organizationSlug, roleName);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());

            // Find the user by MongoDB userId attribute
            List<UserRepresentation> users = realmResource.users()
                    .searchByAttributes("userId:" + userId);

            if (users.isEmpty()) {
                log.warn("User not found in Keycloak by userId attribute: {}", userId);
                return;
            }

            String keycloakUserId = users.get(0).getId();

            // Find the role group
            String groupPath = "/organizations/" + organizationSlug + "/" + roleName;
            try {
                org.keycloak.representations.idm.GroupRepresentation group =
                        realmResource.getGroupByPath(groupPath);
                if (group != null) {
                    realmResource.users().get(keycloakUserId).leaveGroup(group.getId());
                    log.info("User {} removed from group {}", userId, groupPath);
                } else {
                    log.warn("Group not found: {}", groupPath);
                }
            } catch (Exception e) {
                log.warn("Failed to remove user from group {}: {}", groupPath, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Verify 2FA code for a user.
     * Note: This is a placeholder - actual implementation depends on 2FA provider.
     *
     * @param userId           The user ID
     * @param confirmationCode The 2FA code
     * @return Mono with true if valid, false otherwise
     */
    public Mono<Boolean> verify2FACode(String userId, String confirmationCode) {
        return Mono.fromCallable(() -> {
            log.info("Verifying 2FA code for user: {}", userId);
            // TODO: Implement actual 2FA verification
            // This could integrate with OtpService for SMS/WhatsApp OTP
            // or with Keycloak's built-in OTP verification
            // For now, return true to allow development flow
            log.warn("2FA verification not fully implemented - returning true for development");
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Delete organization groups from Keycloak.
     *
     * @param organizationSlug The organization's URL slug
     * @return Mono signaling completion
     */
    public Mono<Void> deleteOrganizationGroups(String organizationSlug) {
        return Mono.fromRunnable(() -> {
            log.info("Deleting Keycloak group structure for organization: {}", organizationSlug);

            RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());

            String groupPath = "/organizations/" + organizationSlug;
            try {
                org.keycloak.representations.idm.GroupRepresentation group =
                        realmResource.getGroupByPath(groupPath);
                if (group != null) {
                    realmResource.groups().group(group.getId()).remove();
                    log.info("Deleted organization group: {}", groupPath);
                }
            } catch (Exception e) {
                log.warn("Failed to delete organization group {}: {}", groupPath, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Find or create a group in Keycloak.
     *
     * @param realmResource The realm resource
     * @param groupName     The group name
     * @param parentGroupId The parent group ID (null for top-level)
     * @return The group ID
     */
    private String findOrCreateGroup(RealmResource realmResource, String groupName, String parentGroupId) {
        try {
            // Try to find existing group
            if (parentGroupId != null) {
                List<org.keycloak.representations.idm.GroupRepresentation> subGroups =
                        realmResource.groups().group(parentGroupId).getSubGroups(0, 100, true);
                for (org.keycloak.representations.idm.GroupRepresentation subGroup : subGroups) {
                    if (subGroup.getName().equals(groupName)) {
                        return subGroup.getId();
                    }
                }
            } else {
                List<org.keycloak.representations.idm.GroupRepresentation> topGroups =
                        realmResource.groups().groups(groupName, 0, 1);
                if (!topGroups.isEmpty() && topGroups.get(0).getName().equals(groupName)) {
                    return topGroups.get(0).getId();
                }
            }

            // Create new group
            org.keycloak.representations.idm.GroupRepresentation newGroup =
                    new org.keycloak.representations.idm.GroupRepresentation();
            newGroup.setName(groupName);

            Response response;
            if (parentGroupId != null) {
                response = realmResource.groups().group(parentGroupId).subGroup(newGroup);
            } else {
                response = realmResource.groups().add(newGroup);
            }

            if (response.getStatus() == 201) {
                String location = response.getHeaderString("Location");
                String groupId = location.substring(location.lastIndexOf('/') + 1);
                log.info("Created group {} with ID: {}", groupName, groupId);
                return groupId;
            } else {
                log.warn("Failed to create group {}: {}", groupName, response.getStatus());
                return null;
            }
        } catch (Exception e) {
            log.warn("Error finding/creating group {}: {}", groupName, e.getMessage());
            return null;
        }
    }
}
