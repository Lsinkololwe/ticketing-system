package com.pml.shared.constants;

/**
 * Service Type Enum
 *
 * Defines the microservices in the 3-microservices architecture.
 * Used for identifying services in cross-service communication.
 */
public enum ServiceType {

    /**
     * Event Catalog Service (Port 8081)
     * Bounded Context: Event Discovery & Management
     */
    CATALOG("catalogService", "Event Catalog Service", "Event Discovery & Management"),

    /**
     * Booking & Ticketing Service (Port 8082)
     * Bounded Context: Ticket Inventory & Orders
     */
    BOOKING("bookingService", "Booking & Ticketing Service", "Ticket Inventory & Orders"),

    /**
     * Platform & Identity Service (Port 8083)
     * Bounded Context: User Management & Platform Operations
     */
    IDENTITY("identityService", "Platform & Identity Service", "User Management & Platform Operations");

    private final String code;
    private final String displayName;
    private final String description;

    ServiceType(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static ServiceType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ServiceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown service type: " + code);
    }

    @Override
    public String toString() {
        return code;
    }
}
