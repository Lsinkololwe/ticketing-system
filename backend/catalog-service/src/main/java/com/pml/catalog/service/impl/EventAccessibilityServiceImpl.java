package com.pml.catalog.service.impl;

import com.pml.catalog.web.graphql.dto.EventAccessibilityInput;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.valueobject.EventAccessibility;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.EventAccessibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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

    @Override
    public Mono<Event> updateAccessibility(String eventId, EventAccessibilityInput input) {
        log.info("Updating accessibility information for event {}", eventId);

        return eventRepository.findById(eventId)
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
                    event.setUpdatedAt(LocalDateTime.now());

                    return eventRepository.save(event)
                            .doOnSuccess(updated -> log.info("Accessibility updated for event {}", eventId));
                });
    }
}
