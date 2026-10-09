package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The {@code CreateTicketTierInput} GraphQL type. {@code currency} must be the platform currency:
 * a tier is priced in what the event sells in.
 */
public record CreateTicketTierInput(
        @NotBlank @Size(max = 24) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2_000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank String currency,
        @Positive int quantity,
        @Positive Integer maxPerOrder,
        @Positive Integer minPerOrder,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> benefits,
        @Min(0) Integer sortOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal earlyBirdPrice,
        Instant earlyBirdEndsAt,
        Boolean isHidden,
        @Size(max = 64) String accessCode,
        com.pml.shared.constants.TicketCategory category
) {
}
