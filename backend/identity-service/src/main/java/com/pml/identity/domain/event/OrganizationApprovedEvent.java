package com.pml.identity.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Domain event published when an organization is approved
 *
 * This event is consumed by:
 * - Catalog Service: Enable event publishing for the organization
 * - Booking Service: Enable payment processing
 * - Notification Service: Send approval notification to organizer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationApprovedEvent {

    /**
     * Organization ID
     */
    private String organizationId;

    /**
     * Owner user ID (who becomes ORGANIZER)
     */
    private String ownerId;

    /**
     * Organization name
     */
    private String organizationName;

    /**
     * Organization slug (URL-friendly identifier)
     */
    private String organizationSlug;

    /**
     * Admin ID who approved the organization
     */
    private String approvedBy;

    /**
     * Approval timestamp
     */
    private Instant approvedAt;
}
