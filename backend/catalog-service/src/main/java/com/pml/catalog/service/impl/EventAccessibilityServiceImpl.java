package com.pml.catalog.service.impl;

import com.pml.catalog.web.graphql.dto.EventAccessibilityInput;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.valueobject.EventAccessibility;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.EventAccessibilityService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Event Accessibility Service Implementation
 *
 * Manages accessibility information for events to help users with disabilities
 * make informed attendance decisions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAccessibilityServiceImpl implements EventAccessibilityService {

    private final EventRepository eventRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    /**
     * An event the caller is entitled to act on, or {@code EVENT_UNKNOWN}.
     *
     * <p>{@code updateEventAccessibility}'s
     * {@code @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")} checks only the role, and the
     * {@code eventId} it writes to is caller-supplied. Accessibility copy is what a venue's disabled
     * attendees rely on to decide whether they can attend at all, so an organizer
     * able to rewrite a rival's — announcing step-free access that does not exist,
     * or removing the note that it does — is a safety problem before it is a
     * security one. OWASP A01:2021, CWE-639.
     */
    private Mono<Event> eventVisibleToCaller(String eventId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                eventRepository.findById(eventId),
                organizationIds -> eventRepository.findByIdAndOrganizationIdIn(eventId, organizationIds),
                ErrorCode.EVENT_UNKNOWN,
                "event " + eventId));
    }

    @Override
    public Mono<Event> updateAccessibility(String eventId, EventAccessibilityInput input) {
        log.info("Updating accessibility information for event {}", eventId);

        return eventVisibleToCaller(eventId)
                .flatMap(event -> {
                    EventAccessibility accessibility = event.getAccessibility();
                    if (accessibility == null) {
                        accessibility = EventAccessibility.defaults();
                    }

                    if (input.wheelchairAccessible() != null) {
                        accessibility.setWheelchairAccessible(input.wheelchairAccessible());
                    }
                    if (input.wheelchairSeatsAvailable() != null) {
                        accessibility.setWheelchairSeatsAvailable(input.wheelchairSeatsAvailable());
                    }
                    if (input.signLanguageInterpreter() != null) {
                        accessibility.setSignLanguageInterpreter(input.signLanguageInterpreter());
                    }
                    if (input.hearingLoopAvailable() != null) {
                        accessibility.setHearingLoopAvailable(input.hearingLoopAvailable());
                    }
                    if (input.accessibleParking() != null) {
                        accessibility.setAccessibleParking(input.accessibleParking());
                    }
                    if (input.accessibleRestrooms() != null) {
                        accessibility.setAccessibleRestrooms(input.accessibleRestrooms());
                    }
                    if (input.assistanceDogsAllowed() != null) {
                        accessibility.setAssistanceDogsAllowed(input.assistanceDogsAllowed());
                    }
                    if (input.additionalNotes() != null) {
                        accessibility.setAdditionalNotes(input.additionalNotes());
                    }

                    event.setAccessibility(accessibility);
                    event.setUpdatedAt(clock.instant());

                    return eventRepository.save(event)
                            .doOnSuccess(updated -> log.info("Accessibility updated for event {}", eventId));
                });
    }
}
