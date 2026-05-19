package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * DTO for MongoDB aggregation results that return a total amount.
 *
 * <p>This wrapper solves the Java 21+ module system issue where Spring Data MongoDB
 * cannot directly return BigDecimal from @Aggregation queries due to reflection
 * restrictions on java.math internal fields.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * @Aggregation(pipeline = {
 *     "{ $match: { escrowAccountId: ?0 } }",
 *     "{ $group: { _id: null, total: { $sum: '$amount' } } }"
 * })
 * Mono&lt;AggregationResult&gt; sumAmount(String accountId);
 *
 * // Then extract:
 * sumAmount(id).map(AggregationResult::getTotal)
 * </pre>
 *
 * @since 1.0.0
 */
public class AggregationResult {

    private BigDecimal total;

    public AggregationResult() {
        this.total = BigDecimal.ZERO;
    }

    public AggregationResult(BigDecimal total) {
        this.total = total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getTotal() {
        return total != null ? total : BigDecimal.ZERO;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    /**
     * Static factory for empty result.
     */
    public static AggregationResult empty() {
        return new AggregationResult(BigDecimal.ZERO);
    }

    @Override
    public String toString() {
        return "AggregationResult{total=" + total + "}";
    }
}
