package com.pml.keycloak.eventlistener;

import com.pml.keycloak.client.IdentityServiceClient;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for creating UserSyncEventListener instances.
 *
 * This factory is registered as a Keycloak SPI via:
 * META-INF/services/org.keycloak.events.EventListenerProviderFactory
 *
 * Configuration:
 * Environment variables required:
 * - IDENTITY_SERVICE_URL (or OTP_SERVICE_URL): URL to Identity Service
 * - OTP_CLIENT_ID: OAuth2 client ID for authentication
 * - OTP_CLIENT_SECRET: OAuth2 client secret
 * - KEYCLOAK_TOKEN_URL: Token endpoint URL
 *
 * Optional:
 * - USER_SYNC_REALM: Specific realm to process (empty = all realms)
 *
 * To enable in Keycloak:
 * 1. Add JAR to providers/ directory
 * 2. Go to Realm Settings > Events > Event Listeners
 * 3. Add "user-sync" listener
 */
public class UserSyncEventListenerFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListenerFactory.class);

    public static final String PROVIDER_ID = "user-sync";

    private IdentityServiceClient identityServiceClient;
    private String realmName;
    private boolean enabled;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        if (!enabled) {
            LOG.debug("UserSyncEventListener is disabled, returning no-op provider");
            return new NoOpEventListener();
        }

        return new UserSyncEventListener(identityServiceClient, realmName);
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Initializing UserSyncEventListenerFactory");

        // Read configuration from environment variables
        // Support both IDENTITY_SERVICE_URL and OTP_SERVICE_URL for backward compatibility
        String serviceUrl = System.getenv("IDENTITY_SERVICE_URL");
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            serviceUrl = System.getenv("OTP_SERVICE_URL");
        }

        // Read optional realm filter
        this.realmName = System.getenv("USER_SYNC_REALM");
        if (this.realmName == null || this.realmName.isEmpty()) {
            // Default to event-ticketing realm if not specified
            this.realmName = "event-ticketing";
        }

        // Validate configuration
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            LOG.warn("IDENTITY_SERVICE_URL/OTP_SERVICE_URL not configured. UserSyncEventListener will be disabled.");
            this.enabled = false;
            return;
        }

        // Validate OAuth2 configuration
        String clientId = System.getenv("OTP_CLIENT_ID");
        String clientSecret = System.getenv("OTP_CLIENT_SECRET");
        String tokenUrl = System.getenv("KEYCLOAK_TOKEN_URL");

        if (clientId == null || clientSecret == null || tokenUrl == null) {
            LOG.warn("OAuth2 credentials not fully configured. UserSyncEventListener will attempt requests without authentication.");
        }

        // Initialize client
        this.identityServiceClient = new IdentityServiceClient(serviceUrl);
        this.enabled = true;

        LOG.infof("UserSyncEventListenerFactory initialized - Service URL: %s, Realm: %s",
                serviceUrl, realmName);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        LOG.debug("UserSyncEventListenerFactory postInit completed");
    }

    @Override
    public void close() {
        LOG.debug("UserSyncEventListenerFactory closed");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * No-op event listener used when the factory is disabled.
     */
    private static class NoOpEventListener implements EventListenerProvider {
        @Override
        public void onEvent(org.keycloak.events.Event event) {
            // No-op
        }

        @Override
        public void onEvent(org.keycloak.events.admin.AdminEvent event, boolean includeRepresentation) {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }
    }
}
