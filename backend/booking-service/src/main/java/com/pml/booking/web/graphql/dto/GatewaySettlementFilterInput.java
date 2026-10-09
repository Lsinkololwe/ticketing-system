package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record GatewaySettlementFilterInput(Instant from, Instant to, @Size(max = 100) String settlementId) {
}
