package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;

import com.pml.identity.domain.enums.TransferStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Ownership Transfer Request Model
 *
 * Represents a request to transfer organization ownership from one user to another.
 *
 * TRANSFER FLOW:
 * =============
 * 1. Current owner initiates transfer with new owner's user ID
 * 2. System creates OwnershipTransferRequest with unique token
 * 3. New owner receives notification with acceptance link
 * 4. New owner clicks link and confirms with 2FA code
 * 5. On confirmation:
 *    - Current owner's role changes to ADMIN
 *    - New owner's role changes to OWNER
 *    - Organization's ownerId is updated
 *    - Keycloak group memberships are updated
 *    - Notification sent to all organization members
 *
 * SECURITY:
 * ========
 * - Transfer requires 2FA confirmation from new owner
 * - Transfer expires after 72 hours
 * - Only current owner can initiate transfer
 * - New owner must be existing ADMIN member
 */
@Document(collection = IdentityCollections.OWNERSHIP_TRANSFERS)
@TypeAlias("ownership_transfers")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OwnershipTransferRequest {

    @Id
    private String id;

    /**
     * Organization being transferred
     */
    @NotBlank(message = "Organization ID is required")
    private String organizationId;

    /**
     * Current owner initiating the transfer
     */
    @NotBlank(message = "Current owner ID is required")
    private String currentOwnerId;

    /**
     * New owner who will receive ownership
     */
    @NotBlank(message = "New owner ID is required")
    private String newOwnerId;

    /**
     * Reason for transfer (optional)
     */
    private String reason;

    /**
     * Unique token for acceptance link
     */
    @NotBlank(message = "Transfer token is required")
    private String transferToken;

    /**
     * Transfer status
     */
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    /**
     * When the transfer request expires
     */
    @NotNull(message = "Expiry date is required")
    private Instant expiresAt;

    /**
     * When the transfer was initiated
     */
    @CreatedDate
    private Instant initiatedAt;

    /**
     * When the transfer was completed
     */
    private Instant completedAt;

    /**
     * When the transfer was cancelled
     */
    private Instant cancelledAt;

    /**
     * Check if transfer request is still valid
     */
    public boolean isValid(Instant now) {
        return status == TransferStatus.PENDING &&
               expiresAt != null &&
               expiresAt.isAfter(now);
    }

    /**
     * Check if transfer has expired
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
