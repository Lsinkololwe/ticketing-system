package com.pml.catalog.web.graphql.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The {@code CreateEventInput} GraphQL type. {@code tags} is deprecated in the schema and not bound:
 * an event carries one category and no tag list.
 */
public record CreateEventInput(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10_000) String description,
        @NotBlank String categoryId,
        @NotNull Instant eventDateTime,
        @NotNull Instant endDateTime,
        @Valid EventLocationInput location,
        @PositiveOrZero int totalCapacity,
        @NotNull @Size(max = 50) List<@Valid @NotNull CreateTicketTierInput> ticketTiers,
        Map<String, Object> additionalInfo,
        @Size(max = 2048) String bannerImageUrl,
        Boolean isVirtual,
        Boolean isFreeEvent,
        @Size(max = 2048) String virtualEventUrl,
        String refundPolicy,
        @Size(max = 5_000) String cancellationPolicy,
        @Size(max = 20_000) String termsAndConditions,
        Boolean enableWaitlist,
        @PositiveOrZero Integer waitlistCapacity,
        @Valid EventAccessibilityInput accessibility,
        @Size(max = 20) List<@jakarta.validation.constraints.NotNull @Size(max = 2048) String> galleryImages,
        @Size(max = 100) String tagline,
        @Size(max = 10) String ageRestriction,
        Instant doorsOpenAt,
        Instant publishAt,
        @Size(max = 20) List<@Valid @NotNull EventFaqInput> faqs,
        @Size(max = 40) List<@Valid @NotNull RunningOrderItemInput> runningOrder,
        @Size(max = 1_000) String gettingThere,
        @Size(max = 1_000) String parkingInfo,
        @Size(max = 1_000) String bagPolicy,
        @Valid CheckoutSettingsInput checkoutSettings,
        @Size(max = 200) String bannerAltText
) {
}
