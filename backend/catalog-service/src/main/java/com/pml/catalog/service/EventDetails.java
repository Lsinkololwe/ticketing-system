package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.domain.valueobject.CheckoutSettings;
import com.pml.catalog.domain.valueobject.EventAccessibility;
import com.pml.catalog.domain.valueobject.EventFaq;
import com.pml.catalog.domain.valueobject.RunningOrderItem;
import com.pml.catalog.web.graphql.dto.CheckoutSettingsInput;
import com.pml.catalog.web.graphql.dto.EventFaqInput;
import com.pml.catalog.web.graphql.dto.RunningOrderItemInput;
import com.pml.catalog.web.graphql.dto.CreateEventInput;
import com.pml.catalog.web.graphql.dto.EventAccessibilityInput;
import com.pml.catalog.web.graphql.dto.UpdateEventInput;
import com.pml.shared.error.FieldViolation;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How an organizer's input becomes an event, and what an event must satisfy afterwards.
 *
 * <p>Pure: no I/O, no clock. The service resolves the venue and applies the lifecycle rules; this
 * class copies input onto the event and reports every violation of the event's own consistency
 * at once, each at the input path that caused it.
 */
public final class EventDetails {

    /** The values the {@code catalog_events} validator accepts for {@code refundPolicy}. */
    /**
     * The refund policy is the code of a platform refund policy (ET-ADM-002 §4: FLEXIBLE, MODERATE, STRICT,
     * NO_REFUNDS). The earlier free-form codes stay readable and writable so existing events keep saving.
     */
    public static final Set<String> REFUND_POLICIES = Set.of(
            "FLEXIBLE", "MODERATE", "STRICT", "NO_REFUNDS",
            "FULL_REFUND", "PARTIAL_REFUND", "NO_REFUND", "CUSTOM");

    /** The values the {@code catalog_events} validator accepts for {@code ageRestriction}. */
    public static final Set<String> AGE_RESTRICTIONS = Set.of("ALL_AGES", "13+", "16+", "18+", "21+");

    /** An organizer's free-form map is bounded, so it cannot be used to bloat the document. */
    static final int MAX_ADDITIONAL_INFO_KEYS = 50;

    static final int MAX_TAGLINE = 100;
    static final int MAX_FAQS = 20;
    static final int MAX_RUNNING_ORDER = 40;

    private EventDetails() {
    }

    public static void apply(Event event, CreateEventInput input) {
        event.setTitle(input.title().trim());
        event.setDescription(input.description());
        event.setCategoryId(input.categoryId());
        event.setEventDateTime(input.eventDateTime());
        event.setEndDateTime(input.endDateTime());
        event.setTotalCapacity(input.totalCapacity());
        event.setAdditionalInfo(input.additionalInfo());
        event.setBannerImageUrl(blankToNull(input.bannerImageUrl()));
        event.setVirtual(Boolean.TRUE.equals(input.isVirtual()));
        event.setFreeEvent(Boolean.TRUE.equals(input.isFreeEvent()));
        event.setVirtualEventUrl(blankToNull(input.virtualEventUrl()));
        event.setRefundPolicy(blankToNull(input.refundPolicy()));
        event.setCancellationPolicy(blankToNull(input.cancellationPolicy()));
        event.setTermsAndConditions(blankToNull(input.termsAndConditions()));
        event.setWaitlistEnabled(Boolean.TRUE.equals(input.enableWaitlist()));
        event.setWaitlistCapacity(input.waitlistCapacity());
        if (input.accessibility() != null) {
            event.setAccessibility(merge(EventAccessibility.defaults(), input.accessibility()));
        }
        if (input.galleryImages() != null) event.setGalleryImages(List.copyOf(input.galleryImages()));
        event.setTagline(blankToNull(input.tagline()));
        event.setAgeRestriction(blankToNull(input.ageRestriction()));
        event.setDoorsOpenAt(input.doorsOpenAt());
        event.setPublishAt(input.publishAt());
        event.setFaqs(faqs(input.faqs()));
        event.setRunningOrder(runningOrder(input.runningOrder()));
        event.setGettingThere(blankToNull(input.gettingThere()));
        event.setParkingInfo(blankToNull(input.parkingInfo()));
        event.setBagPolicy(blankToNull(input.bagPolicy()));
        event.setCheckoutSettings(checkout(input.checkoutSettings()));
        event.setBannerAltText(blankToNull(input.bannerAltText()));
    }

    /** A null field leaves the stored value alone; {@code featured} is the caller's to authorize. */
    public static void apply(Event event, UpdateEventInput input) {
        if (input.title() != null) event.setTitle(input.title().trim());
        if (input.description() != null) event.setDescription(input.description());
        if (input.categoryId() != null) event.setCategoryId(input.categoryId());
        if (input.eventDateTime() != null) event.setEventDateTime(input.eventDateTime());
        if (input.endDateTime() != null) event.setEndDateTime(input.endDateTime());
        if (input.totalCapacity() != null) event.setTotalCapacity(input.totalCapacity());
        if (input.additionalInfo() != null) event.setAdditionalInfo(input.additionalInfo());
        if (input.bannerImageUrl() != null) event.setBannerImageUrl(blankToNull(input.bannerImageUrl()));
        if (input.thumbnailImageUrl() != null) event.setThumbnailImageUrl(blankToNull(input.thumbnailImageUrl()));
        if (input.galleryImages() != null) event.setGalleryImages(List.copyOf(input.galleryImages()));
        if (input.isVirtual() != null) event.setVirtual(input.isVirtual());
        if (input.isFreeEvent() != null) event.setFreeEvent(input.isFreeEvent());
        if (input.virtualEventUrl() != null) event.setVirtualEventUrl(blankToNull(input.virtualEventUrl()));
        if (input.virtualEventPlatform() != null) event.setVirtualEventPlatform(blankToNull(input.virtualEventPlatform()));
        if (input.refundPolicy() != null) event.setRefundPolicy(blankToNull(input.refundPolicy()));
        if (input.cancellationPolicy() != null) event.setCancellationPolicy(blankToNull(input.cancellationPolicy()));
        if (input.termsAndConditions() != null) event.setTermsAndConditions(blankToNull(input.termsAndConditions()));
        if (input.enableWaitlist() != null) event.setWaitlistEnabled(input.enableWaitlist());
        if (input.waitlistCapacity() != null) event.setWaitlistCapacity(input.waitlistCapacity());
        if (input.featured() != null) event.setFeatured(input.featured());
        if (input.tagline() != null) event.setTagline(blankToNull(input.tagline()));
        if (input.ageRestriction() != null) event.setAgeRestriction(blankToNull(input.ageRestriction()));
        if (input.doorsOpenAt() != null) event.setDoorsOpenAt(input.doorsOpenAt());
        if (input.publishAt() != null) event.setPublishAt(input.publishAt());
        if (input.faqs() != null) event.setFaqs(faqs(input.faqs()));
        if (input.runningOrder() != null) event.setRunningOrder(runningOrder(input.runningOrder()));
        if (input.gettingThere() != null) event.setGettingThere(blankToNull(input.gettingThere()));
        if (input.parkingInfo() != null) event.setParkingInfo(blankToNull(input.parkingInfo()));
        if (input.bagPolicy() != null) event.setBagPolicy(blankToNull(input.bagPolicy()));
        if (input.checkoutSettings() != null) event.setCheckoutSettings(checkout(input.checkoutSettings()));
        if (input.bannerAltText() != null) event.setBannerAltText(blankToNull(input.bannerAltText()));
    }

    static List<EventFaq> faqs(List<EventFaqInput> input) {
        if (input == null) {
            return null;
        }
        return input.stream()
                .map(faq -> new EventFaq(faq.question().trim(), faq.answer().trim()))
                .toList();
    }

    static List<RunningOrderItem> runningOrder(List<RunningOrderItemInput> input) {
        if (input == null) {
            return null;
        }
        return input.stream()
                .map(item -> new RunningOrderItem(item.time().trim(), item.title().trim()))
                .toList();
    }

    static CheckoutSettings checkout(CheckoutSettingsInput input) {
        if (input == null) {
            return null;
        }
        return new CheckoutSettings(input.maxTicketsPerOrder(), Boolean.TRUE.equals(input.collectHolderNames()),
                blankToNull(input.extraQuestion()));
    }

    /** The venue's identity and names, copied onto the event. */
    public static void place(Event event, Location venue) {
        event.setLocationId(venue.getId());
        event.setLocationName(venue.getName());
        event.setLocationAddress(venue.getAddress());
        event.setCityId(venue.getCityId());
        event.setCityName(venue.getCityName());
    }

    /**
     * Every way the event is inconsistent with itself.
     *
     * @param hasVenue whether the event has, or is about to be given, a venue
     */
    public static List<FieldViolation> check(Event event, boolean hasVenue) {
        List<FieldViolation> violations = new ArrayList<>();
        if (event.getEventDateTime() != null && event.getEndDateTime() != null
                && !event.getEndDateTime().isAfter(event.getEventDateTime())) {
            violations.add(new FieldViolation("endDateTime", "must be after eventDateTime"));
        }
        if (event.isVirtual()) {
            if (event.getVirtualEventUrl() == null) {
                violations.add(new FieldViolation("virtualEventUrl", "is required for a virtual event"));
            }
        } else if (!hasVenue) {
            violations.add(new FieldViolation("location", "is required for an in-person event"));
        }
        webUrl(violations, "bannerImageUrl", event.getBannerImageUrl());
        webUrl(violations, "thumbnailImageUrl", event.getThumbnailImageUrl());
        webUrl(violations, "virtualEventUrl", event.getVirtualEventUrl());
        List<String> gallery = event.getGalleryImages() == null ? List.of() : event.getGalleryImages();
        for (int i = 0; i < gallery.size(); i++) {
            webUrl(violations, "galleryImages[" + i + "]", gallery.get(i));
        }
        if (event.getRefundPolicy() != null && !REFUND_POLICIES.contains(event.getRefundPolicy())) {
            violations.add(new FieldViolation("refundPolicy", "must be one of " + REFUND_POLICIES));
        }
        if (event.isWaitlistEnabled() && (event.getWaitlistCapacity() == null || event.getWaitlistCapacity() <= 0)) {
            violations.add(new FieldViolation("waitlistCapacity", "must be positive when the waitlist is enabled"));
        }
        Map<String, Object> info = event.getAdditionalInfo();
        if (info != null && info.size() > MAX_ADDITIONAL_INFO_KEYS) {
            violations.add(new FieldViolation("additionalInfo", "must have at most " + MAX_ADDITIONAL_INFO_KEYS + " keys"));
        }
        if (event.getAgeRestriction() != null && !AGE_RESTRICTIONS.contains(event.getAgeRestriction())) {
            violations.add(new FieldViolation("ageRestriction", "must be one of " + AGE_RESTRICTIONS));
        }
        if (event.getDoorsOpenAt() != null && event.getEventDateTime() != null
                && event.getDoorsOpenAt().isAfter(event.getEventDateTime())) {
            violations.add(new FieldViolation("doorsOpenAt", "must not be after the event starts"));
        }
        if (event.getPublishAt() != null && event.getEventDateTime() != null
                && !event.getPublishAt().isBefore(event.getEventDateTime())) {
            violations.add(new FieldViolation("publishAt", "must be before the event starts"));
        }
        if (event.getTagline() != null && event.getTagline().length() > MAX_TAGLINE) {
            violations.add(new FieldViolation("tagline", "must be at most " + MAX_TAGLINE + " characters"));
        }
        if (event.getFaqs() != null && event.getFaqs().size() > MAX_FAQS) {
            violations.add(new FieldViolation("faqs", "must have at most " + MAX_FAQS + " questions"));
        }
        if (event.getRunningOrder() != null && event.getRunningOrder().size() > MAX_RUNNING_ORDER) {
            violations.add(new FieldViolation("runningOrder", "must have at most " + MAX_RUNNING_ORDER + " lines"));
        }
        if (event.isFreeEvent() && event.getLowestTicketPrice() != null
                && event.getLowestTicketPrice().signum() > 0) {
            violations.add(new FieldViolation("isFreeEvent", "a free event cannot have a paid tier"));
        }
        return violations;
    }

    /** A publication time the organizer sets must still be ahead. */
    public static List<FieldViolation> checkPublishAt(Instant publishAt, Instant now) {
        return publishAt != null && !publishAt.isAfter(now)
                ? List.of(new FieldViolation("publishAt", "must be in the future"))
                : List.of();
    }

    /** A new event must start in the future. */
    public static List<FieldViolation> checkStart(Event event, Instant now) {
        return event.getEventDateTime() != null && !event.getEventDateTime().isAfter(now)
                ? List.of(new FieldViolation("eventDateTime", "must be in the future"))
                : List.of();
    }

    /**
     * Only an absolute http(s) link with a host is stored. A {@code javascript:} or {@code data:}
     * link would be rendered by every client that shows the event.
     */
    static void webUrl(List<FieldViolation> violations, String path, String value) {
        if (value == null) {
            return;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null || uri.getUserInfo() != null) {
                violations.add(new FieldViolation(path, "must be an http or https link"));
            }
        } catch (URISyntaxException e) {
            violations.add(new FieldViolation(path, "must be an http or https link"));
        }
    }

    static EventAccessibility merge(EventAccessibility accessibility, EventAccessibilityInput input) {
        if (input.wheelchairAccessible() != null) accessibility.setWheelchairAccessible(input.wheelchairAccessible());
        if (input.wheelchairSeatsAvailable() != null) accessibility.setWheelchairSeatsAvailable(input.wheelchairSeatsAvailable());
        if (input.signLanguageInterpreter() != null) accessibility.setSignLanguageInterpreter(input.signLanguageInterpreter());
        if (input.hearingLoopAvailable() != null) accessibility.setHearingLoopAvailable(input.hearingLoopAvailable());
        if (input.accessibleParking() != null) accessibility.setAccessibleParking(input.accessibleParking());
        if (input.accessibleRestrooms() != null) accessibility.setAccessibleRestrooms(input.accessibleRestrooms());
        if (input.assistanceDogsAllowed() != null) accessibility.setAssistanceDogsAllowed(input.assistanceDogsAllowed());
        if (input.additionalNotes() != null) accessibility.setAdditionalNotes(input.additionalNotes());
        return accessibility;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
