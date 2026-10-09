package com.pml.catalog.domain.valueobject;

/**
 * One line of the event's running order: a venue-local clock time ({@code HH:mm}, 24-hour) and
 * what happens then. The day is the event's own, so no date is stored.
 */
public record RunningOrderItem(String time, String title) {
}
