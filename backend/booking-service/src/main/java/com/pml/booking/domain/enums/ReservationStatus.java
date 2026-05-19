package com.pml.booking.domain.enums;

/**
 * Reservation Status Enum
 *
 * Lifecycle:
 * - ACTIVE: Reservation is active and not expired
 * - CONVERTED: Reservation was successfully converted to tickets
 * - EXPIRED: Reservation expired before conversion
 * - CANCELLED: User or system cancelled the reservation
 */
public enum ReservationStatus {
    ACTIVE,
    CONVERTED,
    EXPIRED,
    CANCELLED
}
