package com.pml.catalog.error;

import com.pml.shared.error.RefusalCoverage;
import com.pml.shared.error.RestGraphQlParity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Catalog-service declares no exception without a registry code.
 *
 * <p>Scans the exception package rather than listing it, so a class added
 * tomorrow is covered today. An unmapped exception does not fail loudly: it
 * falls through to the defect path, so an ordinary refusal reaches the caller as
 * {@code INTERNAL_ERROR} and the on-call as an ERROR with a stack trace.</p>
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R3 · every catalog exception has a registry code")
class CatalogRefusalCoverageTest {

    @Test
    @DisplayName("every declared exception maps to a §4 code")
    void everyExceptionIsMapped() {
        RefusalCoverage.assertEveryExceptionIsMapped(
                new CatalogRefusalTranslator(), "com.pml.catalog.exception");
    }

    @Test
    @DisplayName("nothing in this service shadows the platform error path")
    void noCompetingErrorBeans() {
        RefusalCoverage.assertNoCompetingErrorBeans("com.pml.catalog");
    }

    @Test
    @DisplayName("REST and GraphQL answer with the same registry code")
    void transportsAgree() {
        RestGraphQlParity.assertTransportsAgree(
                new CatalogRefusalTranslator(), "com.pml.catalog.exception");
    }
}
