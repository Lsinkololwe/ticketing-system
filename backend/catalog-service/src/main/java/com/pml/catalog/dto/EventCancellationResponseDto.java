package com.pml.catalog.dto;

import com.pml.catalog.domain.model.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for event cancellation response.
 * Includes refund workflow tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCancellationResponseDto {
    private boolean success;
    private String message;
    private Event event;
    private int ticketsAffected;
    private boolean refundSagaInitiated;
    private String sagaId;
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static EventCancellationResponseDto success(Event event, int ticketsAffected, String sagaId) {
        return EventCancellationResponseDto.builder()
                .success(true)
                .message("Event cancelled successfully")
                .event(event)
                .ticketsAffected(ticketsAffected)
                .refundSagaInitiated(sagaId != null)
                .sagaId(sagaId)
                .build();
    }

    public static EventCancellationResponseDto error(String errorMessage) {
        return EventCancellationResponseDto.builder()
                .success(false)
                .message(errorMessage)
                .ticketsAffected(0)
                .refundSagaInitiated(false)
                .errors(List.of(errorMessage))
                .build();
    }
}
