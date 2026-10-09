package com.pml.catalog.web.graphql.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The {@code UpdateEventInput} GraphQL type; a null field leaves the stored value unchanged.
 * {@code tags} is deprecated in the schema and not bound.
 */
public record UpdateEventInput(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1, max = 10_000) String description,
        @Size(min = 1) String categoryId,
        Instant eventDateTime,
        Instant endDateTime,
        @Valid EventLocationInput location,
        @PositiveOrZero Integer totalCapacity,
        Map<String, Object> additionalInfo,
        @Size(max = 2048) String bannerImageUrl,
        @Size(max = 2048) String thumbnailImageUrl,
        @Size(max = 20) List<@jakarta.validation.constraints.NotNull @Size(max = 2048) String> galleryImages,
        Boolean isVirtual,
        Boolean isFreeEvent,
        @Size(max = 2048) String virtualEventUrl,
        @Size(max = 100) String virtualEventPlatform,
        String refundPolicy,
        @Size(max = 5_000) String cancellationPolicy,
        @Size(max = 20_000) String termsAndConditions,
        Boolean enableWaitlist,
        @PositiveOrZero Integer waitlistCapacity,
        Boolean featured,
        @Size(max = 100) String tagline,
        @Size(max = 10) String ageRestriction,
        Instant doorsOpenAt,
        Instant publishAt,
        @Size(max = 20) List<@Valid @jakarta.validation.constraints.NotNull EventFaqInput> faqs,
        @Size(max = 40) List<@Valid @jakarta.validation.constraints.NotNull RunningOrderItemInput> runningOrder,
        @Size(max = 1_000) String gettingThere,
        @Size(max = 1_000) String parkingInfo,
        @Size(max = 1_000) String bagPolicy,
        @Valid CheckoutSettingsInput checkoutSettings,
        @Size(max = 200) String bannerAltText
) {

    /** A capacity change and nothing else. */
    public static UpdateEventInput capacity(int totalCapacity) {
        return new UpdateEventInput(null, null, null, null, null, null, totalCapacity, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
