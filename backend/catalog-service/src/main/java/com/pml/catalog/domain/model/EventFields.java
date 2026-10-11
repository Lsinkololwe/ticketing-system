package com.pml.catalog.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.Map;

/**
 * Every field of {@link Event}, classified once.
 *
 * <p>A <em>material</em> change alters what a reviewer approved — when, where, by whom, how many —
 * so it sends an approved event back to draft and is refused on a published one. An
 * <em>editable</em> field is one an organizer may change without review. Everything else is kept
 * by the system. A test fails when a field of {@code Event} is in none of the three sets.
 */
public final class EventFields {

    public static final Set<String> MATERIAL = Set.of(
            "eventDateTime", "endDateTime", "locationId", "organizationId", "totalCapacity");

    public static final Set<String> EDITABLE = Set.of(
            "title", "description", "categoryId", "locationName", "locationAddress", "cityName", "cityId",
            "bannerImageUrl", "thumbnailImageUrl", "galleryImages", "additionalInfo", "isVirtual",
            "virtualEventUrl", "virtualEventPlatform", "waitlistEnabled", "waitlistCapacity", "refundPolicy",
            "cancellationPolicy", "termsAndConditions", "isFreeEvent", "accessibility", "featured",
            "tagline", "ageRestriction", "doorsOpenAt", "bannerAltText", "faqs", "runningOrder", "gettingThere",
            "parkingInfo", "bagPolicy", "checkoutSettings", "publishAt");

    public static final Set<String> SYSTEM = Set.of(
            "id", "currency", "lowestTicketPrice", "organizerId", "organizerName", "status", "published", "publishedAt",
            "availableTickets", "soldTickets", "ticketCategories", "tags", "isRecurring", "recurrencePattern",
            "parentEventId", "hasWaitlist", "version", "submittedForApprovalAt", "approvalDeadline", "isOverdue",
            "assignedReviewerId", "assignedReviewerName", "approvedAt", "approvedBy", "rejectedAt", "rejectedBy",
            "rejectionReason", "changesRequestedAt", "changesRequestedBy", "changesRequestedComments",
            "submissionCount", "previousStartsAt", "rescheduleCount", "createdAt", "updatedAt", "createdBy",
            "updatedBy", "isActive", "isDeleted", "deletedAt", "deletedBy", "deletionReason", "publishScheduled",
            "cancellationReason", "cancelledAt", "grossSales", "commissionAmount");

    private static final Map<String, Function<Event, Object>> MATERIAL_READERS = Map.of(
            "eventDateTime", Event::getEventDateTime,
            "endDateTime", Event::getEndDateTime,
            "locationId", Event::getLocationId,
            "organizationId", Event::getOrganizationId,
            "totalCapacity", Event::getTotalCapacity);

    private EventFields() {
    }

    /** The material fields whose value differs between the two snapshots. */
    public static Set<String> materialChanges(Event before, Event after) {
        return MATERIAL_READERS.entrySet().stream()
                .filter(field -> !Objects.equals(field.getValue().apply(before), field.getValue().apply(after)))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
