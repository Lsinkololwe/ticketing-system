package com.pml.identity.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the Keycloak {@code user-sync} listener tells identity-service (CONTRACT 4.6): which user,
 * in which realm, what happened, and the two flags a delivery can carry for free.
 *
 * <p>There is no attribute map, no name, no role and no phone: those are personal data that would
 * sit in logs and in Temporal history, and identity-service reads the user's current state from
 * Keycloak by id anyway, which also makes out-of-order events converge.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakEventDto {

    /** The listener's id for this event, used to drop redeliveries. */
    private String eventId;

    /** {@code LOGIN, LOGOUT, REFRESH_TOKEN_ERROR, DELETE, UPDATE_PROFILE, UPDATE_EMAIL, VERIFY_EMAIL, REGISTER, ADMIN_*}. */
    @NotBlank(message = "Event type is required")
    private String eventType;

    /** The Keycloak user id. */
    @NotBlank(message = "User ID is required")
    private String userId;

    /** The account id for a buyer, the staff username for staff. */
    private String username;

    /** {@code myticketzm} or {@code myticketzm-admin}. */
    private String realm;

    private Boolean enabled;

    private Boolean emailVerified;

    /** Epoch milliseconds. */
    private Long timestamp;

    /**
     * The Keycloak SSO session id, the access token's {@code sid} claim. Sent only with
     * {@code LOGOUT} and {@code REFRESH_TOKEN_ERROR}; it is an opaque id, not personal data.
     */
    private String sid;
}
