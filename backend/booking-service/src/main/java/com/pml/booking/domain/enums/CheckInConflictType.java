package com.pml.booking.domain.enums;

/**
 * Why a scan could not become a check-in.
 *
 * <p>A conflict row is written whenever a scan is refused for a reason the
 * organizer should see. That is deliberately narrower than "any failed scan":
 * a QR that does not parse is a device problem, not an attendance problem, and
 * recording it would bury the cases that matter.
 */
public enum CheckInConflictType {

    /**
     * The ticket had already been admitted. Online this is a refused second
     * scan; offline it is two devices that each admitted the same ticket and
     * only discovered it when both uploaded.
     */
    DUPLICATE_SCAN,

    /** The ticket is valid but belongs to a different event. */
    TICKET_NOT_VALID_FOR_EVENT,

    /** The ticket was refunded, cancelled or otherwise not admissible. */
    INVALID_STATE,

    /** No ticket matched the scanned code or reference. */
    TICKET_NOT_FOUND
}
