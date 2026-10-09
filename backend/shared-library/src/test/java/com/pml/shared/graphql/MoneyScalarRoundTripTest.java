package com.pml.shared.graphql;

import graphql.GraphQLContext;
import graphql.language.FloatValue;
import graphql.language.StringValue;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money scalar survives the wire.
 *
 * <h2>Why this scalar is singled out</h2>
 * Every scalar needs a round-trip test, and {@code BigDecimal} in particular must serialise as a
 * decimal string, never a float. The reason is that a float is not a rounding inconvenience for
 * money, it is a wrong
 * number: `0.1 + 0.2` in binary floating point is `0.30000000000000004`, and a ticket priced
 * K150.10 can arrive as K150.09999999999999.
 *
 * <p>Every price, fee, commission, escrow balance and payout amount in this platform crosses the
 * graph through this scalar. If it degrades to a double anywhere in that path the ledger stops
 * balancing, and `Ledger.assertBalanced` in the test harness would be asserting against numbers
 * that were already wrong when they arrived.</p>
 *
 * <h2>What is asserted, and what deliberately is not</h2>
 * This pins the <b>coercion</b>, not the transport encoding. `graphql-java` hands the serialised
 * value to the JSON writer, and what matters is that it hands over a `BigDecimal` — a type that
 * cannot lose precision — rather than a `Double`. Asserting the literal JSON bytes would couple
 * this to a serializer this module does not own.
 */
@Tag("L1")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R2 · the money scalar round-trips without becoming a float")
class MoneyScalarRoundTripTest {

    private static final GraphQLScalarType MONEY = ExtendedScalars.GraphQLBigDecimal;

    /**
     * graphql-java 24 requires a non-null context and locale on every coercion call — passing
     * {@code null} fails with "Object required to be not null" before the coercion runs at all,
     * which reads exactly like a broken scalar. Defaults, since neither affects a decimal.
     */
    private static final GraphQLContext CONTEXT = GraphQLContext.getDefault();
    private static final Locale LOCALE = Locale.ROOT;

    @SuppressWarnings("unchecked")
    private static Coercing<Object, Object> coercing() {
        return (Coercing<Object, Object>) MONEY.getCoercing();
    }

    @Test
    @DisplayName("a price serialises as BigDecimal, not Double")
    void serialisesAsBigDecimal() {
        Object wire = coercing().serialize(new BigDecimal("150.10"), CONTEXT, LOCALE);

        assertThat(wire)
                .as("a Double here loses cents at the fourth decimal and the ledger stops "
                        + "balancing — silently, because every individual amount still looks right")
                .isInstanceOf(BigDecimal.class)
                .isNotInstanceOf(Double.class);
        assertThat(wire).hasToString("150.10");
    }

    @Test
    @DisplayName("the exact value survives a full round trip, scale included")
    void roundTripsExactly() {
        BigDecimal price = new BigDecimal("150.10");

        Object wire = coercing().serialize(price, CONTEXT, LOCALE);
        Object back = coercing().parseValue(wire, CONTEXT, LOCALE);

        assertThat(back)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo(price);

        // Scale, not just value. `150.1` and `150.10` are equal by compareTo and different on an
        // invoice, and a scale that drifts is how a total stops matching the sum of its lines.
        assertThat(((BigDecimal) back).toPlainString()).isEqualTo("150.10");
    }

    @Test
    @DisplayName("the value that breaks binary floating point survives")
    void theClassicFloatingPointCase() {
        // 0.1 + 0.2 == 0.30000000000000004 as doubles. If anything in this path is a double, this
        // is the assertion that says so — which is why it is a real amount rather than a round one.
        BigDecimal a = new BigDecimal("0.10");
        BigDecimal b = new BigDecimal("0.20");

        Object sum = coercing().serialize(a.add(b), CONTEXT, LOCALE);

        assertThat(sum).hasToString("0.30");
        assertThat(sum).isInstanceOf(BigDecimal.class);
    }

    @Test
    @DisplayName("a decimal string literal from a query parses exactly")
    void parsesAStringLiteral() {
        Object parsed = coercing().parseLiteral(StringValue.newStringValue("150.10").build(),
                graphql.execution.CoercedVariables.emptyVariables(), CONTEXT, LOCALE);

        assertThat(parsed)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo(new BigDecimal("150.10"));
    }

    @Test
    @DisplayName("a float literal is accepted but is exactly what clients must not send")
    void aFloatLiteralIsTheHazard() {
        // Recorded rather than asserted away. The coercion accepts a GraphQL float literal, so a
        // client CAN send `amount: 150.10` unquoted and lose precision before the server ever sees
        // it — the one half of this contract the backend cannot enforce.
        //
        // That is a frontend obligation (types come from codegen, and the generated input type
        // for a BigDecimal is a string). Pinned here so the gap is visible to whoever reads this
        // test rather than discovered from a mismatched payout.
        Object parsed = coercing().parseLiteral(
                FloatValue.newFloatValue(new BigDecimal("150.10")).build(),
                graphql.execution.CoercedVariables.emptyVariables(), CONTEXT, LOCALE);

        assertThat(parsed)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo(new BigDecimal("150.10"));
    }
}
