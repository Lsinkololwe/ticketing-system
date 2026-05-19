package com.pml.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keycloak-specific JWT Granted Authorities Converter.
 *
 * <p>Following Spring Security best practices, this implements
 * {@code Converter<Jwt, Collection<GrantedAuthority>>} to extract authorities
 * from Keycloak JWT tokens.</p>
 *
 * <p>Keycloak tokens have a specific structure for roles:</p>
 * <ul>
 *   <li>Realm roles: {@code realm_access.roles} (array)</li>
 *   <li>Client roles: {@code resource_access.<client-id>.roles} (array)</li>
 *   <li>Scopes: {@code scope} (space-separated string)</li>
 * </ul>
 *
 * <p>This converter extracts all of these and converts them to Spring Security
 * {@link GrantedAuthority} objects with appropriate prefixes.</p>
 *
 * @see org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
 * @see org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
 */
public class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    // Keycloak default roles to skip
    private static final List<String> IGNORED_ROLES = List.of(
            "offline_access",
            "uma_authorization"
    );

    private String clientId;
    private boolean extractRealmRoles = true;
    private boolean extractClientRoles = true;
    private boolean extractScopes = true;

    /**
     * Default constructor - extracts realm roles, client roles, and scopes.
     */
    public KeycloakGrantedAuthoritiesConverter() {
    }

    /**
     * Constructor with client ID for extracting client-specific roles.
     *
     * @param clientId The Keycloak client ID to extract roles from resource_access
     */
    public KeycloakGrantedAuthoritiesConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (extractScopes) {
            authorities.addAll(extractScopes(jwt));
        }

        if (extractRealmRoles) {
            authorities.addAll(extractRealmRoles(jwt));
        }

        if (extractClientRoles && clientId != null) {
            authorities.addAll(extractClientRoles(jwt, clientId));
        }

        // Extract custom userType claim (application-specific)
        authorities.addAll(extractUserTypeAuthority(jwt));

        return authorities;
    }

    /**
     * Extracts scopes from the 'scope' claim.
     * Keycloak sends scopes as a space-separated string.
     */
    private Collection<GrantedAuthority> extractScopes(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return Collections.emptyList();
        }

        return List.of(scope.split(" ")).stream()
                .filter(s -> !s.isBlank())
                .map(s -> new SimpleGrantedAuthority(SCOPE_PREFIX + s))
                .collect(Collectors.toList());
    }

    /**
     * Extracts realm-level roles from 'realm_access.roles'.
     * These are roles assigned at the realm level in Keycloak.
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List)) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) rolesObj;
        return roles.stream()
                .filter(this::isValidRole)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .collect(Collectors.toList());
    }

    /**
     * Extracts client-level roles from 'resource_access.<client-id>.roles'.
     * These are roles assigned to a specific client in Keycloak.
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractClientRoles(Jwt jwt, String clientId) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) {
            return Collections.emptyList();
        }

        Object clientAccess = resourceAccess.get(clientId);
        if (!(clientAccess instanceof Map)) {
            return Collections.emptyList();
        }

        Map<String, Object> clientAccessMap = (Map<String, Object>) clientAccess;
        Object rolesObj = clientAccessMap.get("roles");
        if (!(rolesObj instanceof List)) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) rolesObj;
        return roles.stream()
                .filter(this::isValidRole)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .collect(Collectors.toList());
    }

    /**
     * Extracts userType from custom claim and converts to role.
     * This is application-specific for the event ticketing system.
     */
    private Collection<GrantedAuthority> extractUserTypeAuthority(Jwt jwt) {
        String userType = jwt.getClaimAsString("userType");
        if (userType == null || userType.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + userType));
    }

    /**
     * Validates if a role should be included (filters out Keycloak defaults).
     */
    private boolean isValidRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        // Skip default Keycloak roles and roles prefixed with 'default-roles-'
        if (role.startsWith("default-roles-")) {
            return false;
        }
        return !IGNORED_ROLES.contains(role);
    }

    // Setters for configuration

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setExtractRealmRoles(boolean extractRealmRoles) {
        this.extractRealmRoles = extractRealmRoles;
    }

    public void setExtractClientRoles(boolean extractClientRoles) {
        this.extractClientRoles = extractClientRoles;
    }

    public void setExtractScopes(boolean extractScopes) {
        this.extractScopes = extractScopes;
    }
}
