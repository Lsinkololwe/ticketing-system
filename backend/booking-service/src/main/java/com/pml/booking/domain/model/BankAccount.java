package com.pml.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * Bank Account Model
 *
 * Stores organizer bank account details for receiving payouts.
 * Each organizer can have multiple bank accounts with one marked as default.
 */
@Document(collection = "bank_accounts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "organizer_default_idx", def = "{'organizerId': 1, 'isDefault': 1}")
})
public class BankAccount {

    @Id
    private String id;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant bank account management.
     * Supports organizations with multiple team members who can manage accounts.
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    private String bankCode;
    private String branchName;
    private String branchCode;

    @NotBlank(message = "Account number is required")
    @Indexed
    private String accountNumber;

    private String accountType;

    @NotBlank(message = "Currency is required")
    @Builder.Default
    private String currency = "ZMW";

    private String swiftCode;

    @Builder.Default
    private boolean isDefault = false;

    @Builder.Default
    private boolean isVerified = false;

    private LocalDateTime verifiedAt;
    private String verifiedBy;

    @Builder.Default
    private String status = "ACTIVE";

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    /**
     * Version for optimistic locking.
     *
     * <p>Prevents concurrent modifications to bank accounts.
     * Important when multiple admin actions or verification processes
     * may modify the same account simultaneously.</p>
     *
     * <p>If two transactions try to modify the same BankAccount simultaneously,
     * one will fail with OptimisticLockingFailureException.</p>
     */
    @Version
    private Long version;
}
