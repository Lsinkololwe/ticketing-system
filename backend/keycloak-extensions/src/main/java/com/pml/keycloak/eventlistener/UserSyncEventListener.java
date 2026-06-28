package com.pml.keycloak.eventlistener;

import com.pml.keycloak.client.IdentityServiceClient;
import com.pml.keycloak.client.IdentityServiceClient.KeycloakEventData;
import com.pml.keycloak.client.IdentityServiceClient.KeycloakUserData;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keycloak EventListener that synchronizes user changes to MongoDB via the Identity Service.
 *
 * OWASP Best Practice Implementation:
 * - Uses KeycloakSession for direct user data access (no admin credentials needed)
 * - Sends full user data to Identity Service (no callback required)
 * - No sensitive credentials stored in Identity Service
 *
 * This listener handles:
 * - User events (REGISTER, UPDATE_PROFILE, LOGIN, etc.)
 * - Admin events (CREATE, UPDATE, DELETE users via Admin Console/API)
 *
 * When events occur, this listener fetches user data directly from the session
 * and sends it to the Identity Service for storage in MongoDB.
 *
 * Configuration:
 * - Requires IDENTITY_SERVICE_URL environment variable
 * - Optional: OTP_CLIENT_ID/SECRET for authenticated sync endpoint
 */
public class UserSyncEventListener implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListener.class);

    // Events that should trigger a user sync
    private static final Set<EventType> SYNC_EVENTS = Set.of(
            EventType.REGISTER,
            EventType.UPDATE_PROFILE,
            EventType.UPDATE_EMAIL,
            EventType.VERIFY_EMAIL,
            EventType.UPDATE_TOTP,
            EventType.REMOVE_TOTP
    );

    // Events that should just update lastLoginAt
    private static final Set<EventType> LOGIN_EVENTS = Set.of(
            EventType.LOGIN,
            EventType.LOGIN_ERROR,
            EventType.LOGOUT
    );

    private final KeycloakSession session;
    private final IdentityServiceClient identityServiceClient;
    private final String realmName;

    public UserSyncEventListener(KeycloakSession session, IdentityServiceClient identityServiceClient, String realmName) {
        this.session = session;
        this.identityServiceClient = identityServiceClient;
        this.realmName = realmName;
        LOG.infof("UserSyncEventListener initialized for realm: %s", realmName);
    }

    @Override
    public void onEvent(Event event) {
        // Only process events for our configured realm
        if (!shouldProcessEvent(event)) {
            return;
        }

        String userId = event.getUserId();
        EventType eventType = event.getType();

        LOG.debugf("Processing event: type=%s, userId=%s, realm=%s",
                eventType, userId, event.getRealmName());

        try {
            if (SYNC_EVENTS.contains(eventType)) {
                // Full user sync needed
                handleUserSyncEvent(userId, eventType, event);
            } else if (LOGIN_EVENTS.contains(eventType)) {
                // Just update login timestamp
                handleLoginEvent(userId, eventType, event);
            } else {
                // Log other events for debugging
                LOG.debugf("Ignoring event type: %s for user: %s", eventType, userId);
            }
        } catch (Exception e) {
            // Don't let sync failures break the authentication flow
            LOG.errorf(e, "Failed to process event %s for user %s: %s",
                    eventType, userId, e.getMessage());
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // Only process user-related admin events
        if (!shouldProcessAdminEvent(event)) {
            return;
        }

        ResourceType resourceType = event.getResourceType();
        OperationType operationType = event.getOperationType();
        String resourcePath = event.getResourcePath();

        LOG.debugf("Processing admin event: operation=%s, resource=%s, path=%s",
                operationType, resourceType, resourcePath);

        try {
            if (resourceType == ResourceType.USER) {
                handleUserAdminEvent(event, operationType, resourcePath);
            } else if (resourceType == ResourceType.REALM_ROLE_MAPPING) {
                // Role assignment change - need to sync user
                handleRoleMappingChange(event, operationType, resourcePath);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process admin event %s for resource %s: %s",
                    operationType, resourcePath, e.getMessage());
        }
    }

    @Override
    public void close() {
        LOG.debug("UserSyncEventListener closed");
    }

    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================

    private void handleUserSyncEvent(String userId, EventType eventType, Event event) {
        LOG.infof("Syncing user %s due to event: %s", userId, eventType);

        // Get realm from session context
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            // Try to get realm by name
            realm = session.realms().getRealmByName(realmName);
        }

        if (realm == null) {
            LOG.errorf("Could not find realm for user sync: %s", realmName);
            return;
        }

        // Fetch user data directly from Keycloak session (OWASP best practice - no admin credentials needed)
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            LOG.warnf("User not found in Keycloak: %s", userId);
            return;
        }

        // Build full user data payload
        KeycloakUserData userData = buildUserData(user, realm, eventType.name());

        // Send full user data to Identity Service
        IdentityServiceClient.SyncResult result = identityServiceClient.syncUserWithData(userData);

        if (result.isSuccess()) {
            LOG.infof("Successfully synced user %s for event %s", userId, eventType);
        } else {
            LOG.warnf("Failed to sync user %s for event %s: %s",
                    userId, eventType, result.getMessage());
        }
    }

    /**
     * Build user data payload from Keycloak UserModel.
     * Extracts all relevant user information including roles and attributes.
     */
    private KeycloakUserData buildUserData(UserModel user, RealmModel realm, String eventType) {
        KeycloakUserData data = new KeycloakUserData();
        data.setId(user.getId());
        data.setUsername(user.getUsername());
        data.setEmail(user.getEmail());
        data.setFirstName(user.getFirstName());
        data.setLastName(user.getLastName());
        data.setEmailVerified(user.isEmailVerified());
        data.setEnabled(user.isEnabled());
        data.setEventType(eventType);
        data.setTimestamp(Instant.now().toEpochMilli());

        // Extract realm roles
        Set<String> roles = user.getRealmRoleMappingsStream()
                .map(RoleModel::getName)
                .collect(Collectors.toSet());
        data.setRoles(roles);

        // Extract attributes
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes != null) {
            data.setAttributes(attributes);

            // Extract specific attributes for convenience.
            // Phone attributes are written under different keys depending on the source:
            //  - PhoneOtpAuthenticator writes snake_case: phone_number / phone_verified
            //  - KeycloakService (admin API) writes camelCase: phoneNumber / phoneVerified
            // Read both so phone data syncs regardless of which path created the user.
            String phoneNumber = firstAttributeValue(attributes, "phone_number", "phoneNumber");
            if (phoneNumber != null) {
                data.setPhoneNumber(phoneNumber);
            }

            String phoneVerified = firstAttributeValue(attributes, "phone_verified", "phoneVerified");
            if (phoneVerified != null) {
                data.setPhoneVerified(Boolean.parseBoolean(phoneVerified));
            }

            // Extract accountType (from registration)
            List<String> accountTypes = attributes.get("accountType");
            if (accountTypes != null && !accountTypes.isEmpty()) {
                data.setAccountTypes(accountTypes);
            }
        }

        return data;
    }

    private void handleLoginEvent(String userId, EventType eventType, Event event) {
        if (eventType == EventType.LOGIN) {
            LOG.debugf("Recording login for user: %s", userId);

            IdentityServiceClient.SyncResult result = identityServiceClient.notifyLogin(userId);

            if (result.isSuccess()) {
                LOG.debugf("Successfully recorded login for user %s", userId);
            } else {
                LOG.warnf("Failed to record login for user %s: %s",
                        userId, result.getMessage());
            }
        }
    }

    private void handleUserAdminEvent(AdminEvent event, OperationType operationType, String resourcePath) {
        String userId = extractUserIdFromPath(resourcePath);
        if (userId == null) {
            LOG.warnf("Could not extract user ID from path: %s", resourcePath);
            return;
        }

        LOG.infof("Processing admin %s for user: %s", operationType, userId);

        switch (operationType) {
            case CREATE:
            case UPDATE:
                // Sync user using full data from session (OWASP best practice)
                syncUserWithFullData(userId, "ADMIN_" + operationType.name());
                break;

            case DELETE:
                // User deleted via admin console
                IdentityServiceClient.SyncResult deleteResult = identityServiceClient.notifyUserDeleted(userId);
                logSyncResult("DELETE", userId, deleteResult);
                break;

            default:
                LOG.debugf("Ignoring admin operation: %s for user: %s", operationType, userId);
        }
    }

    private void handleRoleMappingChange(AdminEvent event, OperationType operationType, String resourcePath) {
        // Resource path format: users/{userId}/role-mappings/realm or users/{userId}/role-mappings/clients/{clientId}
        String userId = extractUserIdFromPath(resourcePath);
        if (userId == null) {
            LOG.warnf("Could not extract user ID from role mapping path: %s", resourcePath);
            return;
        }

        LOG.infof("Processing role mapping change for user: %s (operation: %s)", userId, operationType);

        // Sync user with full data to update role information (OWASP best practice)
        syncUserWithFullData(userId, "ROLE_MAPPING_CHANGE");
    }

    /**
     * Sync user using full data from Keycloak session.
     * This is the OWASP-compliant approach - no admin credentials needed in Identity Service.
     */
    private void syncUserWithFullData(String userId, String eventType) {
        // Get realm from session context
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            realm = session.realms().getRealmByName(realmName);
        }

        if (realm == null) {
            LOG.errorf("Could not find realm for user sync: %s", realmName);
            return;
        }

        // Fetch user data directly from Keycloak session
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            LOG.warnf("User not found in Keycloak: %s", userId);
            return;
        }

        // Build full user data payload
        KeycloakUserData userData = buildUserData(user, realm, eventType);

        // Send full user data to Identity Service
        IdentityServiceClient.SyncResult result = identityServiceClient.syncUserWithData(userData);
        logSyncResult(eventType, userId, result);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Return the first non-empty value among the given attribute keys, or null.
     * Used to tolerate both snake_case and camelCase attribute naming conventions.
     */
    private String firstAttributeValue(Map<String, List<String>> attributes, String... keys) {
        if (attributes == null) {
            return null;
        }
        for (String key : keys) {
            List<String> values = attributes.get(key);
            if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }

    private boolean shouldProcessEvent(Event event) {
        // Check if event has required data
        if (event.getUserId() == null) {
            return false;
        }

        // Optionally filter by realm name if configured
        if (realmName != null && !realmName.isEmpty() && !realmName.equals(event.getRealmName())) {
            LOG.debugf("Ignoring event from realm %s (configured realm: %s)",
                    event.getRealmName(), realmName);
            return false;
        }

        return true;
    }

    private boolean shouldProcessAdminEvent(AdminEvent event) {
        ResourceType resourceType = event.getResourceType();

        // Only process user and role mapping events
        if (resourceType != ResourceType.USER && resourceType != ResourceType.REALM_ROLE_MAPPING) {
            return false;
        }

        // Filter by realm - admin events use realmId (UUID), so we need to look up the realm
        if (realmName != null && !realmName.isEmpty()) {
            String eventRealmId = event.getRealmId();
            // Try to get realm from session and compare names
            RealmModel eventRealm = session.realms().getRealm(eventRealmId);
            if (eventRealm == null || !realmName.equals(eventRealm.getName())) {
                LOG.debugf("Ignoring admin event from realm %s (configured realm: %s)",
                        eventRealm != null ? eventRealm.getName() : eventRealmId, realmName);
                return false;
            }
        }

        return true;
    }

    /**
     * Extract user ID from admin event resource path.
     * Path formats:
     * - users/{userId}
     * - users/{userId}/role-mappings/realm
     * - users/{userId}/role-mappings/clients/{clientId}
     */
    private String extractUserIdFromPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return null;
        }

        // Path starts with "users/"
        if (resourcePath.startsWith("users/")) {
            String afterUsers = resourcePath.substring(6); // Remove "users/"
            int slashIndex = afterUsers.indexOf('/');
            if (slashIndex > 0) {
                return afterUsers.substring(0, slashIndex);
            } else {
                return afterUsers;
            }
        }

        return null;
    }

    private KeycloakEventData createAdminEventData(AdminEvent event, String userId, OperationType operationType) {
        KeycloakEventData eventData = new KeycloakEventData();
        eventData.setUserId(userId);
        eventData.setEventType("ADMIN_EVENT");
        eventData.setOperationType(operationType.name());
        eventData.setResourceType(event.getResourceType().name());
        eventData.setResourcePath(event.getResourcePath());
        eventData.setTimestamp(Instant.now().toEpochMilli());
        eventData.setRealmId(event.getRealmId());

        if (event.getAuthDetails() != null) {
            eventData.setClientId(event.getAuthDetails().getClientId());
            eventData.setIpAddress(event.getAuthDetails().getIpAddress());
        }

        return eventData;
    }

    private void logSyncResult(String operation, String userId, IdentityServiceClient.SyncResult result) {
        if (result.isSuccess()) {
            LOG.infof("Successfully processed %s for user %s", operation, userId);
        } else {
            LOG.warnf("Failed to process %s for user %s: %s",
                    operation, userId, result.getMessage());
        }
    }
}
