package com.pml.booking.domain.enums;

/**
 * Whether an organizer has looked at a conflict yet.
 *
 * <p>Conflicts are not errors to be cleared — they are a record of something
 * that happened at the gate. Reviewing one annotates it; it never deletes it,
 * because the count of duplicate presentations is itself the finding.
 */
public enum CheckInConflictStatus {

    /** Detected, not yet looked at. */
    OPEN,

    /** An organizer has read it and left a note. */
    REVIEWED
}
