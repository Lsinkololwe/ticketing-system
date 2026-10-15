package com.pml.shared.graphql;

import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-PLT-004")
@DisplayName("DateTime serializes every temporal the domain returns, a calendar date as the start of its day in UTC")
class DateTimeScalarSerializationTest {

    private final DateTimeScalar scalar = new DateTimeScalar();

    @Test
    @DisplayName("a LocalDate field, such as a reconciliation date, serializes instead of failing the whole page")
    void localDate() {
        assertThat(scalar.serialize(LocalDate.of(2026, 10, 14))).isEqualTo("2026-10-14T00:00:00Z");
    }

    @Test
    @DisplayName("the other temporals are unchanged, and a non-temporal is still refused")
    void others() {
        assertThat(scalar.serialize(Instant.parse("2026-10-14T08:00:00Z"))).isEqualTo("2026-10-14T08:00:00Z");
        assertThat(scalar.serialize(LocalDateTime.of(2026, 10, 14, 8, 0))).isEqualTo("2026-10-14T08:00:00Z");
        assertThatThrownBy(() -> scalar.serialize("yesterday")).isInstanceOf(CoercingSerializeException.class);
    }
}
