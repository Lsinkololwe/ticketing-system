package com.pml.shared.graphql;

import graphql.language.StringValue;
import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code DateTime} scalar serializes the temporal types the domain
 * models actually use — in particular {@link Instant}, which the previous
 * OffsetDateTime-backed scalar rejected with a SERIALIZATION_ERROR.
 */
@DisplayName("DateTimeScalar")
class DateTimeScalarTest {

    private final DateTimeScalar scalar = new DateTimeScalar();

    @Test
    @DisplayName("serializes Instant to ISO-8601 UTC")
    void serializesInstant() {
        Instant instant = Instant.parse("2026-06-27T19:20:41Z");
        assertThat(scalar.serialize(instant)).isEqualTo("2026-06-27T19:20:41Z");
    }

    @Test
    @DisplayName("serializes OffsetDateTime, ZonedDateTime and LocalDateTime")
    void serializesOtherTemporals() {
        OffsetDateTime odt = OffsetDateTime.parse("2026-06-27T21:20:41+02:00");
        assertThat(scalar.serialize(odt)).isEqualTo("2026-06-27T19:20:41Z");

        LocalDateTime ldt = LocalDateTime.parse("2026-06-27T19:20:41");
        assertThat(scalar.serialize(ldt)).isEqualTo("2026-06-27T19:20:41Z");
    }

    @Test
    @DisplayName("null serializes to null")
    void serializesNull() {
        assertThat(scalar.serialize(null)).isNull();
    }

    @Test
    @DisplayName("rejects non-temporal values")
    void rejectsNonTemporal() {
        assertThatThrownBy(() -> scalar.serialize("not a date"))
                .isInstanceOf(CoercingSerializeException.class);
    }

    @Test
    @DisplayName("parses ISO-8601 input back to Instant")
    void parsesValue() {
        assertThat(scalar.parseValue("2026-06-27T19:20:41Z"))
                .isEqualTo(Instant.parse("2026-06-27T19:20:41Z"));
        assertThat(scalar.parseValue("2026-06-27T21:20:41+02:00"))
                .isEqualTo(Instant.parse("2026-06-27T19:20:41Z"));
    }

    @Test
    @DisplayName("parses literal StringValue")
    void parsesLiteral() {
        assertThat(scalar.parseLiteral(new StringValue("2026-06-27T19:20:41Z")))
                .isEqualTo(Instant.parse("2026-06-27T19:20:41Z"));
    }

    @Test
    @DisplayName("LocalDateTime stored as UTC round-trips through Instant")
    void localDateTimeUtc() {
        LocalDateTime ldt = LocalDateTime.of(2026, 6, 27, 19, 20, 41);
        assertThat(scalar.serialize(ldt))
                .isEqualTo(ldt.toInstant(ZoneOffset.UTC).toString());
    }
}
