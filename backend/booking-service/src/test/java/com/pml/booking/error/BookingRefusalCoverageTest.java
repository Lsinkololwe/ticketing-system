package com.pml.booking.error;

import com.pml.shared.error.RefusalCoverage;
import com.pml.shared.error.RestGraphQlParity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Booking-service declares no exception without a registry code.
 *
 * <p>Scans the exception package rather than listing it, so a class added
 * tomorrow is covered today. An unmapped exception does not fail loudly: it
 * falls through to the defect path, so an ordinary refusal reaches the caller as
 * {@code INTERNAL_ERROR} and the on-call as an ERROR with a stack trace.</p>
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R3 · every booking exception has a registry code")
class BookingRefusalCoverageTest {

    @Test
    @DisplayName("every declared exception maps to a §4 code")
    void everyExceptionIsMapped() {
        RefusalCoverage.assertEveryExceptionIsMapped(
                new BookingRefusalTranslator(), "com.pml.booking.exception");
        RefusalCoverage.assertEveryExceptionIsMapped(
                new BookingRefusalTranslator(), "com.pml.booking.infrastructure.gateway.exception");
    }

    @Test
    @DisplayName("nothing in this service shadows the platform error path")
    void noCompetingErrorBeans() {
        RefusalCoverage.assertNoCompetingErrorBeans("com.pml.booking");
    }

    @Test
    @DisplayName("REST and GraphQL answer with the same registry code")
    void transportsAgree() {
        RestGraphQlParity.assertTransportsAgree(
                new BookingRefusalTranslator(), "com.pml.booking.exception");
    }
}
