package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * DTO for event cancellation response.
 * Includes refund workflow tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCancellationResponseDto {
    private Event event;
    private int ticketsAffected;
    private boolean refundSagaInitiated;
    private String sagaId;


}
