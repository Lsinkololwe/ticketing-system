package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for event cancellation input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCancellationInputDto {
    private String eventId;
    private String reason;
    @Builder.Default
    private Boolean notifyAttendees = true;
    @Builder.Default
    private Boolean triggerRefunds = true;
    @Builder.Default
    private Boolean notifyBuyers = true;
    @Builder.Default
    private Boolean processRefundsImmediately = true;
}
