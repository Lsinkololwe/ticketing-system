package com.pml.booking.domain.model;

import com.pml.shared.constants.EscrowAccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Escrow Account Model
 */
@Document(collection = "escrow_accounts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EscrowAccount {

    @Id
    private String id;

    @NotBlank(message = "Account name is required")
    @Indexed(unique = true)
    private String accountName;

    @NotBlank(message = "Account number is required")
    @Indexed(unique = true)
    private String accountNumber;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Current balance is required")
    @DecimalMin(value = "0.0", message = "Balance cannot be negative")
    private BigDecimal currentBalance;

    @NotNull(message = "Total deposits is required")
    @DecimalMin(value = "0.0", message = "Total deposits cannot be negative")
    private BigDecimal totalDeposits;

    @NotNull(message = "Total withdrawals is required")
    @DecimalMin(value = "0.0", message = "Total withdrawals cannot be negative")
    private BigDecimal totalWithdrawals;

    @NotNull(message = "Total commissions is required")
    @DecimalMin(value = "0.0", message = "Total commissions cannot be negative")
    private BigDecimal totalCommissions;

    @DecimalMin(value = "0.0", message = "Minimum balance cannot be negative")
    private BigDecimal minimumBalance;

    @DecimalMin(value = "0.0", message = "Maximum payout amount cannot be negative")
    private BigDecimal maximumPayoutAmount;

    private Integer dailyPayoutLimit;
    private Integer monthlyPayoutLimit;

    @NotBlank(message = "Status is required")
    @Indexed
    private String status;

    @Builder.Default
    private boolean isActive = true;

    private String contactPerson;
    private String contactEmail;
    private String contactPhone;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime lastTransactionAt;

    @Indexed
    private String associatedBankAccountNumber;

    private Map<String, Object> metadata;
    private String description;

    public void addFunds(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.currentBalance = this.currentBalance.add(amount);
        this.totalDeposits = this.totalDeposits.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    public void deductFunds(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalWithdrawals = this.totalWithdrawals.add(amount);
        this.lastTransactionAt = LocalDateTime.now();
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.currentBalance.compareTo(amount) >= 0;
    }

    public boolean isPayoutAmountValid(BigDecimal amount) {
        if (maximumPayoutAmount != null && amount.compareTo(this.maximumPayoutAmount) > 0) {
            return false;
        }
        return this.currentBalance.compareTo(amount) >= 0;
    }

    public BigDecimal getAvailableBalance() {
        if (minimumBalance == null) {
            return this.currentBalance;
        }
        return this.currentBalance.subtract(this.minimumBalance);
    }

    public boolean isOperational() {
        return this.isActive && EscrowAccountStatus.ACTIVE.name().equals(this.status);
    }
}
