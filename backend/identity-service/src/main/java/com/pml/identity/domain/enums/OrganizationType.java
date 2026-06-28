package com.pml.identity.domain.enums;

/**
 * Type of organization (event organizer) on the platform.
 *
 * <p>Mirrors the organizer categories used by mainstream ticketing platforms
 * (Eventbrite, SimpleTix, TicketSpice) adapted to the Zambian market, where
 * churches, schools and community groups are major event organizers.</p>
 *
 * <p>KYB rule: {@link #INDIVIDUAL} is created lazily from the user's personal
 * details. Every other type is a formal entity that requires KYB verification
 * before payouts can be released.</p>
 */
public enum OrganizationType {
    /**
     * Individual organizer (sole proprietor / personal account).
     * Created automatically from the user's personal details and can be
     * upgraded to any formal type later.
     */
    INDIVIDUAL,

    /**
     * For-profit company or commercial event promoter.
     * Requires KYB verification before receiving payouts.
     */
    BUSINESS,

    /**
     * Non-profit, NGO, charity or foundation.
     * Typically eligible for reduced fees / fundraising features.
     */
    NON_PROFIT,

    /**
     * Government or public-sector entity (ministry, council, parastatal).
     */
    GOVERNMENT,

    /**
     * Educational institution (school, college, university).
     */
    EDUCATIONAL,

    /**
     * Community group, club, association or sports league.
     */
    COMMUNITY,

    /**
     * Religious organization (church, mosque, faith-based group).
     */
    RELIGIOUS
}
