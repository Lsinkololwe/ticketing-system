package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Keycloak integration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    /**
     * Keycloak server URL (e.g., http://localhost:8084)
     */
    private String serverUrl = "http://localhost:8084";

    /**
     * Keycloak realm name
     */
    private String realm = "event-ticketing";

    /**
     * Admin username for Admin API access
     */
    private String adminUsername = "admin";

    /**
     * Admin password for Admin API access
     */
    private String adminPassword = "admin";

    /**
     * Admin realm (usually "master" for admin operations)
     */
    private String adminRealm = "master";

    /**
     * Client ID for the identity service
     */
    private String clientId = "identity-service";

    /**
     * Client secret for the identity service
     */
    private String clientSecret;

    /**
     * Token URI for obtaining tokens
     */
    public String getTokenUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    /**
     * Issuer URI for token validation
     */
    public String getIssuerUri() {
        return serverUrl + "/realms/" + realm;
    }

    /**
     * JWK Set URI for token validation
     */
    public String getJwkSetUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    }

    /**
     * Admin API base URL
     */
    public String getAdminApiUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }
}
