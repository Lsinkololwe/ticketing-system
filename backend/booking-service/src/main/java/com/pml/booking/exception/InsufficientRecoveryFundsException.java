package com.pml.booking.exception;

import com.pml.booking.domain.enums.ChargebackFundSource;

import java.math.BigDecimal;

/**
 * Exception thrown when there are insufficient funds for chargeback recovery.
 *
 * <p>Chargeback recovery follows a waterfall pattern, attempting to recover
 * funds from multiple sources in priority order. This exception is thrown
 * when all sources are exhausted and full recovery is not possible.</p>
 *
 * <h2>Recovery Waterfall (Priority Order)</h2>
 * <ol>
 *   <li><b>ORGANIZER_ESCROW</b>: Event's escrow account</li>
 *   <li><b>ORGANIZER_FUTURE</b>: Pending payouts to organizer</li>
 *   <li><b>PLATFORM_RESERVE</b>: Platform's reserve fund</li>
 *   <li><b>WRITE_OFF</b>: Record as bad debt (last resort)</li>
 * </ol>
 *
 * <h2>Common Scenarios</h2>
 * <ul>
 *   <li>Escrow already paid out (event completed long ago)</li>
 *   <li>Organizer has no other events with pending payouts</li>
 *   <li>Platform reserve is depleted or below minimum threshold</li>
 * </ul>
 *
 * <h2>Resolution Options</h2>
 * <ul>
 *   <li>Approve write-off (record as bad debt expense)</li>
 *   <li>Manually replenish platform reserve</li>
 *   <li>Contact organizer for direct recovery</li>
 *   <li>Flag organizer for future payout deductions</li>
 * </ul>
 *
 * @see com.pml.booking.domain.enums.ChargebackFundSource
 * @see com.pml.booking.domain.model.ChargebackRecord
 * @since 1.0.0
 */
public class InsufficientRecoveryFundsException extends RuntimeException {

    /**
     * The chargeback ID requiring recovery.
     */
    private final String chargebackId;

    /**
     * Amount that needs to be recovered.
     */
    private final BigDecimal amountRequired;

    /**
     * Amount successfully recovered so far.
     */
    private final BigDecimal amountRecovered;

    /**
     * The remaining shortfall.
     */
    private final BigDecimal shortfall;

    /**
     * The fund source that was insufficient.
     */
    private final ChargebackFundSource insufficientSource;

    /**
     * Available balance in the insufficient source.
     */
    private final BigDecimal availableBalance;

    /**
     * Creates a new InsufficientRecoveryFundsException with basic message.
     *
     * @param message Error message
     */
    public InsufficientRecoveryFundsException(String message) {
        super(message);
        this.chargebackId = null;
        this.amountRequired = null;
        this.amountRecovered = null;
        this.shortfall = null;
        this.insufficientSource = null;
        this.availableBalance = null;
    }

    /**
     * Creates a new InsufficientRecoveryFundsException with recovery details.
     *
     * @param chargebackId The chargeback ID
     * @param amountRequired Total amount needed
     * @param amountRecovered Amount recovered so far
     * @param insufficientSource The source that was insufficient
     * @param availableBalance Balance available in that source
     */
    public InsufficientRecoveryFundsException(
            String chargebackId,
            BigDecimal amountRequired,
            BigDecimal amountRecovered,
            ChargebackFundSource insufficientSource,
            BigDecimal availableBalance
    ) {
        super(String.format(
                "Insufficient funds for chargeback %s recovery. " +
                "Required: %s, Recovered: %s, Shortfall: %s. " +
                "%s has only %s available.",
                chargebackId,
                amountRequired,
                amountRecovered,
                amountRequired.subtract(amountRecovered),
                insufficientSource,
                availableBalance
        ));
        this.chargebackId = chargebackId;
        this.amountRequired = amountRequired;
        this.amountRecovered = amountRecovered;
        this.shortfall = amountRequired.subtract(amountRecovered);
        this.insufficientSource = insufficientSource;
        this.availableBalance = availableBalance;
    }

    /**
     * Creates a new InsufficientRecoveryFundsException when all sources exhausted.
     *
     * @param chargebackId The chargeback ID
     * @param amountRequired Total amount needed
     * @param amountRecovered Amount recovered from all sources
     */
    public InsufficientRecoveryFundsException(
            String chargebackId,
            BigDecimal amountRequired,
            BigDecimal amountRecovered
    ) {
        super(String.format(
                "All recovery sources exhausted for chargeback %s. " +
                "Required: %s, Recovered: %s, Shortfall: %s. " +
                "Consider write-off or manual recovery.",
                chargebackId,
                amountRequired,
                amountRecovered,
                amountRequired.subtract(amountRecovered)
        ));
        this.chargebackId = chargebackId;
        this.amountRequired = amountRequired;
        this.amountRecovered = amountRecovered;
        this.shortfall = amountRequired.subtract(amountRecovered);
        this.insufficientSource = null;
        this.availableBalance = null;
    }

    /**
     * Creates a new InsufficientRecoveryFundsException with cause.
     *
     * @param message Error message
     * @param cause Underlying cause
     */
    public InsufficientRecoveryFundsException(String message, Throwable cause) {
        super(message, cause);
        this.chargebackId = null;
        this.amountRequired = null;
        this.amountRecovered = null;
        this.shortfall = null;
        this.insufficientSource = null;
        this.availableBalance = null;
    }

    /**
     * Static factory for organizer escrow insufficient.
     *
     * @param chargebackId The chargeback ID
     * @param required Amount required
     * @param available Amount available in escrow
     * @return New InsufficientRecoveryFundsException
     */
    public static InsufficientRecoveryFundsException escrowInsufficient(
            String chargebackId,
            BigDecimal required,
            BigDecimal available
    ) {
        return new InsufficientRecoveryFundsException(
                chargebackId,
                required,
                available,
                ChargebackFundSource.ORGANIZER_ESCROW,
                available
        );
    }

    /**
     * Static factory for platform reserve insufficient.
     *
     * @param chargebackId The chargeback ID
     * @param required Amount required
     * @param recovered Amount recovered from other sources
     * @param reserveBalance Current reserve balance
     * @return New InsufficientRecoveryFundsException
     */
    public static InsufficientRecoveryFundsException reserveInsufficient(
            String chargebackId,
            BigDecimal required,
            BigDecimal recovered,
            BigDecimal reserveBalance
    ) {
        return new InsufficientRecoveryFundsException(
                chargebackId,
                required,
                recovered,
                ChargebackFundSource.PLATFORM_RESERVE,
                reserveBalance
        );
    }

    // Getters

    public String getChargebackId() {
        return chargebackId;
    }

    public BigDecimal getAmountRequired() {
        return amountRequired;
    }

    public BigDecimal getAmountRecovered() {
        return amountRecovered;
    }

    public BigDecimal getShortfall() {
        return shortfall;
    }

    public ChargebackFundSource getInsufficientSource() {
        return insufficientSource;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
