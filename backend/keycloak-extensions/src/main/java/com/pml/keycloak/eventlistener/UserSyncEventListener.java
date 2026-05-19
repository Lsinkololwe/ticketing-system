package com.pml.keycloak.eventlistener;

import com.pml.keycloak.client.IdentityServiceClient;
import com.pml.keycloak.client.IdentityServiceClient.KeycloakEventData;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.time.Instant;
import java.util.Set;

/**
 * Keycloak EventListener that synchronizes user changes to MongoDB via the Identity Service.
 *
 * This listener handles:
 * - User events (REGISTER, UPDATE_PROFILE, LOGIN, etc.)
 * - Admin events (CREATE, UPDATE, DELETE users via Admin Console/API)
 *
 * When events occur, this listener calls the Identity Service webhook endpoints
 * to ensure MongoDB stays in sync with Keycloak as the source of truth.
 *
 * Configuration:
 * - Requires IDENTITY_SERVICE_URL environment variable (or OTP_SERVICE_URL for backward compatibility)
 * - Requires OTP_CLIENT_ID and OTP_CLIENT_SECRET for OAuth2 authentication
 * - Requires KEYCLOAK_TOKEN_URL for token endpoint
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

    private final IdentityServiceClient identityServiceClient;
    private final String realmName;

    public UserSyncEventListener(IdentityServiceClient identityServiceClient, String realmName) {
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

        IdentityServiceClient.SyncResult result = identityServiceClient.syncUser(userId, eventType.name());

        if (result.isSuccess()) {
            LOG.infof("Successfully synced user %s for event %s", userId, eventType);
        } else {
            LOG.warnf("Failed to sync user %s for event %s: %s",
                    userId, eventType, result.getMessage());
        }
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

        KeycloakEventData eventData = createAdminEventData(event, userId, operationType);

        switch (operationType) {
            case CREATE:
                // New user created via admin console
                IdentityServiceClient.SyncResult createResult = identityServiceClient.syncUser(userId, "ADMIN_CREATE");
                logSyncResult("CREATE", userId, createResult);
                break;

            case UPDATE:
                // User updated via admin console
                IdentityServiceClient.SyncResult updateResult = identityServiceClient.syncUser(userId, "ADMIN_UPDATE");
                logSyncResult("UPDATE", userId, updateResult);
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

        // Sync user to update role information
        IdentityServiceClient.SyncResult result = identityServiceClient.syncUser(userId, "ROLE_MAPPING_CHANGE");
        logSyncResult("ROLE_MAPPING", userId, result);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

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

        // Optionally filter by realm name if configured
        if (realmName != null && !realmName.isEmpty() && !realmName.equals(event.getRealmId())) {
            // Admin events use realmId instead of realmName
            // This comparison may need adjustment based on how Keycloak represents the realm
            LOG.debugf("Ignoring admin event from realm %s", event.getRealmId());
            return false;
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
