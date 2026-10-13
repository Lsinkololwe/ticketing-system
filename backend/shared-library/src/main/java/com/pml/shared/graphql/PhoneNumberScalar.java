package com.pml.shared.graphql;

import com.netflix.graphql.dgs.DgsScalar;
import com.pml.shared.util.PhoneNumbers;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

/**
 * GraphQL {@code PhoneNumber} scalar.
 *
 * <p>Represents a phone number as a canonical
 * <a href="https://en.wikipedia.org/wiki/E.164">E.164</a> string
 * (e.g. {@code +260971234567}). The scalar is the typed boundary for phone
 * input across all subgraphs: any incoming value goes through the platform's one
 * phone normaliser, {@link PhoneNumbers#parseMobile}, so a malformed, non-mobile
 * or local-format number the normaliser refuses is rejected at the edge instead of
 * failing deeper. A number with no country code is read as Zambian. Output is always
 * E.164. The refusal message never repeats the number.</p>
 *
 * <p>Registered automatically in every service via the {@code com.pml.shared}
 * component scan ({@code @DgsScalar}).</p>
 */
@DgsScalar(name = "PhoneNumber")
public class PhoneNumberScalar implements Coercing<String, String> {

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult == null) {
            return null;
        }
        String value = dataFetcherResult.toString();
        // Stored values are already E.164; normalize defensively so output is
        // always canonical even if a legacy/unnormalized value slips through.
        String e164 = canonical(value);
        if (e164 != null) {
            return e164;
        }
        // Preserve a non-empty legacy value rather than dropping it on read.
        return value;
    }

    @Override
    public String parseValue(Object input) throws CoercingParseValueException {
        if (input == null) {
            return null;
        }
        String raw = input.toString();
        if (raw.isBlank()) {
            return null;
        }
        String e164 = canonical(raw);
        if (e164 == null) {
            throw new CoercingParseValueException(
                    "Invalid phone number. Expected a valid mobile number in "
                            + "international (E.164) or local form (e.g. +260971234567).");
        }
        return e164;
    }

    @Override
    public String parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue stringValue) {
            String raw = stringValue.getValue();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String e164 = canonical(raw);
            if (e164 == null) {
                throw new CoercingParseLiteralException(
                        "Invalid phone number literal. Expected E.164 or local form.");
            }
            return e164;
        }
        throw new CoercingParseLiteralException(
                "Expected a String literal for PhoneNumber, but got: " + input);
    }

    private static String canonical(String raw) {
        return PhoneNumbers.parseMobile(raw, PhoneNumbers.DEFAULT_REGION).map(PhoneNumbers.Parsed::e164).orElse(null);
    }
}
