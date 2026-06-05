package com.pml.identity.domain.enums;

/**
 * Type of organization based on how it was created.
 *
 * Industry Standard Pattern (Eventbrite, Stripe Connect model):
 * - INDIVIDUAL: Created lazily when a user tries to create their first event.
 *               Uses the user's personal details. Can be upgraded to BUSINESS.
 * - BUSINESS: A formal business entity with KYB verification required for payouts.
 */
public enum OrganizationType {
    /**
     * Individual organizer (sole proprietor / personal account).
     * Created automatically when a user creates their first event.
     * Uses the user's personal details initially.
     */
    INDIVIDUAL,

    /**
     * Formal business entity with verified business details.
     * Requires KYB verification before receiving payouts.
     */
    BUSINESS
}
