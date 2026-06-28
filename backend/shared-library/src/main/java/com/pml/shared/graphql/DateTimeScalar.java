package com.pml.shared.graphql;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * GraphQL {@code DateTime} scalar.
 *
 * <p>Serializes any of the platform's temporal types to a canonical ISO-8601
 * UTC string (e.g. {@code 2026-06-27T19:20:41.123Z}) and parses input back to
 * an {@link Instant}.</p>
 *
 * <p>This replaces DGS's auto-registered {@code ExtendedScalars.DateTime}, which
 * is backed by {@link OffsetDateTime} and throws a {@code SerializationError}
 * when handed an {@link Instant} — the type our MongoDB-mapped domain models use
 * for {@code createdAt}/{@code updatedAt} and similar fields. To avoid a
 * duplicate-scalar clash, the DGS time-dates extended scalar must be disabled:
 * <pre>
 * dgs.graphql.extensions.scalars.time-dates.enabled: false
 * </pre>
 * Registered automatically in every service via the {@code com.pml.shared}
 * component scan ({@code @DgsScalar}), mirroring {@link PhoneNumberScalar}.</p>
 */
@DgsScalar(name = "DateTime")
public class DateTimeScalar implements Coercing<Instant, String> {

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult == null) {
            return null;
        }
        if (dataFetcherResult instanceof Instant instant) {
            return instant.toString();
        }
        if (dataFetcherResult instanceof OffsetDateTime odt) {
            return odt.toInstant().toString();
        }
        if (dataFetcherResult instanceof ZonedDateTime zdt) {
            return zdt.toInstant().toString();
        }
        if (dataFetcherResult instanceof LocalDateTime ldt) {
            // Domain LocalDateTime values are stored/interpreted as UTC.
            return ldt.toInstant(ZoneOffset.UTC).toString();
        }
        throw new CoercingSerializeException(
                "Expected a temporal type (Instant, OffsetDateTime, ZonedDateTime or "
                        + "LocalDateTime) but was: " + dataFetcherResult.getClass().getName());
    }

    @Override
    public Instant parseValue(Object input) throws CoercingParseValueException {
        if (input == null) {
            return null;
        }
        String raw = input.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(raw).toInstant();
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException e3) {
                    throw new CoercingParseValueException(
                            "Invalid DateTime: '" + raw + "'. Expected ISO-8601 (e.g. "
                                    + "2026-06-27T19:20:41Z).", e3);
                }
            }
        }
    }

    @Override
    public Instant parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue stringValue) {
            return parseValue(stringValue.getValue());
        }
        throw new CoercingParseLiteralException(
                "Expected a String literal for DateTime, but got: " + input);
    }
}
