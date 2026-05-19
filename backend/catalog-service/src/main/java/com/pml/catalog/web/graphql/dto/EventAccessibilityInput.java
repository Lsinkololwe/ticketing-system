package com.pml.catalog.web.graphql.dto;

/**
 * Event Accessibility Input
 *
 * Input DTO for updating event accessibility information.
 */
public record EventAccessibilityInput(
        Boolean wheelchairAccessible,
        Integer wheelchairSeatsAvailable,
        Boolean signLanguageInterpreter,
        Boolean hearingLoopAvailable,
        Boolean accessibleParking,
        Boolean accessibleRestrooms,
        Boolean assistanceDogsAllowed,
        String additionalNotes
) {
}
