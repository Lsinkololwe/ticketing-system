package com.pml.catalog.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event Accessibility Information
 *
 * Accessibility features and accommodations available at an event venue.
 *
 * Business Intent: Provide comprehensive accessibility information to help users
 * with disabilities make informed decisions about event attendance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAccessibility {

    /**
     * Whether venue is wheelchair accessible
     */
    private boolean wheelchairAccessible;

    /**
     * Number of wheelchair-accessible seats available (null if not tracked)
     */
    private Integer wheelchairSeatsAvailable;

    /**
     * Sign language interpreter available
     */
    private boolean signLanguageInterpreter;

    /**
     * Hearing loop/induction loop system available
     */
    private boolean hearingLoopAvailable;

    /**
     * Accessible parking available
     */
    private boolean accessibleParking;

    /**
     * Accessible restrooms available
     */
    private boolean accessibleRestrooms;

    /**
     * Assistance/guide dogs allowed
     */
    @Builder.Default
    private boolean assistanceDogsAllowed = true;

    /**
     * Additional accessibility notes/details
     */
    private String additionalNotes;

    /**
     * Create default accessibility configuration (minimal accessibility)
     *
     * @return Default accessibility settings
     */
    public static EventAccessibility defaults() {
        return EventAccessibility.builder()
                .wheelchairAccessible(false)
                .signLanguageInterpreter(false)
                .hearingLoopAvailable(false)
                .accessibleParking(false)
                .accessibleRestrooms(false)
                .assistanceDogsAllowed(true)
                .build();
    }

    /**
     * Check if any accessibility features are available
     *
     * @return true if at least one accessibility feature is enabled
     */
    public boolean hasAccessibilityFeatures() {
        return wheelchairAccessible
                || signLanguageInterpreter
                || hearingLoopAvailable
                || accessibleParking
                || accessibleRestrooms
                || assistanceDogsAllowed;
    }
}
