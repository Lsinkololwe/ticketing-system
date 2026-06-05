package com.pml.identity.dto.sync;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DTO for receiving full user data from Keycloak EventListener.
 *
 * OWASP Best Practice:
 * - Keycloak sends complete user data directly
 * - Identity Service doesn't need admin credentials to call back to Keycloak
 * - Reduces attack surface by eliminating stored admin credentials
 * - Eliminates unnecessary round-trip to Keycloak
 *
 * This DTO matches the KeycloakUserData class in keycloak-extensions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakUserDataDto {

    /**
     * Keycloak user ID (will be used as MongoDB document ID).
     */
    @NotBlank(message = "User ID is required")
    private String id;

    /**
     * Username (often same as email).
     */
    private String username;

    /**
     * User's email address.
     */
    @Email(message = "Invalid email format")
    private String email;

    /**
     * User's first name.
     */
    private String firstName;

    /**
     * User's last name.
     */
    private String lastName;

    /**
     * Whether the email has been verified.
     */
    private boolean emailVerified;

    /**
     * Whether the user account is enabled.
     */
    private boolean enabled;

    /**
     * User's phone number (E.164 format).
     */
    private String phoneNumber;

    /**
     * Whether the phone number has been verified.
     */
    private boolean phoneVerified;

    /**
     * Realm roles assigned to the user (CUSTOMER, ORGANIZER, ADMIN, etc.).
     */
    private Set<String> roles;

    /**
     * Account types selected during registration (from accountType attribute).
     */
    private List<String> accountTypes;

    /**
     * All user attributes from Keycloak.
     */
    private Map<String, List<String>> attributes;

    /**
     * Type of event that triggered the sync (REGISTER, UPDATE_PROFILE, etc.).
     */
    @NotBlank(message = "Event type is required")
    private String eventType;

    /**
     * Timestamp when the event occurred (epoch millis).
     */
    private long timestamp;
}
