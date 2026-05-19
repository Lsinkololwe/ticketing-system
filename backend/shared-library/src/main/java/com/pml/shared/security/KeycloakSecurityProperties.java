package com.pml.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Keycloak security integration.
 *
 * <p>These properties complement Spring Boot's OAuth2 Resource Server properties
 * with Keycloak-specific settings.</p>
 *
 * <h3>Example configuration:</h3>
 * <pre>{@code
 * keycloak:
 *   security:
 *     client-id: booking-service
 *     expected-audiences:
 *       - booking-service
 *       - account
 *     extract-realm-roles: true
 *     extract-client-roles: true
 *     extract-scopes: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "keycloak.security")
public class KeycloakSecurityProperties {

    /**
     * The Keycloak client ID for this service.
     * Used for extracting client-specific roles from resource_access.
     */
    private String clientId;

    /**
     * Expected audience values for token validation.
     * Tokens must contain at least one of these values in the 'aud' claim.
     */
    private List<String> expectedAudiences = new ArrayList<>();

    /**
     * Whether to extract realm-level roles from realm_access.roles.
     * Default: true
     */
    private boolean extractRealmRoles = true;

    /**
     * Whether to extract client-level roles from resource_access.<client-id>.roles.
     * Default: true
     */
    private boolean extractClientRoles = true;

    /**
     * Whether to extract scopes from the scope claim.
     * Default: true
     */
    private boolean extractScopes = true;

    // Getters and Setters

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getExpectedAudiences() {
        return expectedAudiences;
    }

    public void setExpectedAudiences(List<String> expectedAudiences) {
        this.expectedAudiences = expectedAudiences;
    }

    public boolean isExtractRealmRoles() {
        return extractRealmRoles;
    }

    public void setExtractRealmRoles(boolean extractRealmRoles) {
        this.extractRealmRoles = extractRealmRoles;
    }

    public boolean isExtractClientRoles() {
        return extractClientRoles;
    }

    public void setExtractClientRoles(boolean extractClientRoles) {
        this.extractClientRoles = extractClientRoles;
    }

    public boolean isExtractScopes() {
        return extractScopes;
    }

    public void setExtractScopes(boolean extractScopes) {
        this.extractScopes = extractScopes;
    }
}
