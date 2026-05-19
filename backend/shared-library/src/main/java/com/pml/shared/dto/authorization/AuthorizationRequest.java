package com.pml.shared.dto.authorization;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authorization Request
 *
 * <p>Request to check if a user is authorized to perform an action on a resource.
 * Used for cross-service authorization between catalog-service and identity-service.</p>
 *
 * <h2>Security Note</h2>
 * <p>The userId should ALWAYS be extracted from the JWT token by the calling service,
 * never accepted from client input. This is enforced by the service architecture.</p>
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRequest {

    /**
     * User ID requesting access (from JWT sub claim).
     * This must be set by the calling service from SecurityContextUtils.
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Organization ID to check membership in.
     * Used for organization-level permissions.
     */
    private String organizationId;

    /**
     * Owner ID of the organization (organizerId from Event).
     * If organizationId is not available, we find the organization by ownerId.
     */
    private String organizationOwnerId;

    /**
     * Event ID for event-specific permissions.
     * Used to check EventAccessGrant.
     */
    private String eventId;

    /**
     * The permission or action being requested.
     * Examples: "EVENT_CREATE", "EVENT_EDIT", "EVENT_DELETE", "EVENT_PUBLISH"
     */
    @NotBlank(message = "Required permission is required")
    private String requiredPermission;

    /**
     * Resource type being accessed.
     * Examples: "EVENT", "ORGANIZATION", "TICKET"
     */
    private String resourceType;

    /**
     * Resource ID being accessed.
     */
    private String resourceId;
}
