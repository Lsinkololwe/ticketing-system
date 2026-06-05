package com.pml.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

/**
 * Organization statistics - embedded document within Organization.
 * Denormalized for performance, updated asynchronously.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationStats {

    /**
     * Total number of team members
     */
    @Builder.Default
    private int memberCount = 0;

    /**
     * Total number of events created
     */
    @Builder.Default
    private int totalEvents = 0;

    /**
     * Total tickets sold across all events
     */
    @Builder.Default
    private int totalTicketsSold = 0;

    /**
     * Total revenue across all events
     */
    @Builder.Default
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    /**
     * Average rating from customers
     */
    private Double averageRating;
}
