package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.CommissionRecord;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CommissionFilterInput(CommissionRecord.CommissionStatus status, @Size(max = 64) String eventId,
                                    @Size(max = 64) String organizationId, @Size(max = 64) String ticketId, Instant createdAfter, Instant createdBefore) {
}
