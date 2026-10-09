package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money is {@code BigDecimal}, rounded {@code HALF_UP} at scale 2, once.
 *
 * <h2>Three properties, each with a different failure</h2>
 * <ul>
 *   <li><b>No floating point.</b> A binary {@code double} cannot represent K0.10 exactly, so the
 *       error compounds across a ledger and the books stop balancing by cents nobody can
 *       locate.</li>
 *   <li><b>One rounding mode.</b> A single {@code HALF_EVEN} among a hundred {@code HALF_UP}
 *       sites shifts a half-cent whenever it runs, and it runs on the value a customer was
 *       shown.</li>
 *   <li><b>A {@code currency} sibling.</b> Not for today — launch is ZMW-only. It is so that
 *       adding a second currency is a data migration rather than an archaeology exercise over
 *       amounts whose currency was implied.</li>
 * </ul>
 *
 * <h2>Field names, not types</h2>
 * The floating-point rule is keyed on what a field is called, because {@code Double latitude} is
 * correct and {@code Double price} is not. Banning the type outright would flag the geo point on
 * {@code Location} — where a {@code BigDecimal} is wrong in a different way, since the
 * {@code 2dsphere} index would not accept it.
 */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R4 · money is BigDecimal, HALF_UP at scale 2, with a currency")
class MoneyDisciplineLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    private static final Set<String> FLOATING = Set.of("double", "float", "Double", "Float");

    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:private|public|protected)?\\s*([A-Za-z_][\\w.]*)\\s+([a-zA-Z_]\\w*)\\s*(?:=|;)",
            Pattern.MULTILINE);

    private static final Pattern DOCUMENT = Pattern.compile("@Document\\s*\\(");

    private static final Pattern MONEY_FIELD = Pattern.compile(
            "(?:private|public|protected)\\s+BigDecimal\\s+(\\w+)");

    private static final Pattern CURRENCY_FIELD = Pattern.compile(
            "(?:private|public|protected)\\s+String\\s+currency\\b");

    private static final Pattern SET_SCALE = Pattern.compile("setScale\\(([^)]*)\\)");

    @Test
    @DisplayName("no money-named field is a floating-point type")
    void moneyIsNeverFloatingPoint() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachProductionSource((path, body) -> {
            Matcher field = FIELD.matcher(body);
            while (field.find()) {
                if (FLOATING.contains(field.group(1)) && looksLikeMoney(field.group(2))) {
                    violations.add("%s → %s %s".formatted(
                            BACKEND_ROOT.relativize(path), field.group(1), field.group(2)));
                }
            }
        });

        assertThat(violations)
                .as("""
                    A binary floating-point type cannot hold K0.10 exactly. Over a ledger the \
                    error compounds, and it surfaces as a reconciliation that is out by cents \
                    with no transaction to attribute them to.""")
                .isEmpty();
    }

    @Test
    @DisplayName("every rounding is HALF_UP at scale 2")
    void roundingIsAlwaysHalfUpAtScaleTwo() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachProductionSource((path, body) -> {
            Matcher rounding = SET_SCALE.matcher(body);
            while (rounding.find()) {
                String arguments = rounding.group(1).replace(" ", "");
                boolean scaleTwo = arguments.startsWith("2,") || arguments.startsWith("SCALE,")
                        || arguments.startsWith("MONEY_SCALE,") || arguments.startsWith("Money.SCALE,");
                boolean halfUp = arguments.contains("HALF_UP") || arguments.contains("ROUNDING");
                if (!scaleTwo || !halfUp) {
                    violations.add("%s → setScale(%s)".formatted(
                            BACKEND_ROOT.relativize(path), rounding.group(1)));
                }
            }
        });

        assertThat(violations)
                .as("""
                    Mixed rounding modes are not a style inconsistency. HALF_EVEN rounds K0.125 \
                    to K0.12 where HALF_UP gives K0.13, so the same fee computed in two places \
                    differs by a ngwee and the difference lands in a reconciliation report \
                    rather than in a review.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no money computation divides without a rounding mode")
    void divisionAlwaysStatesItsRounding() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachProductionSource((path, body) -> {
            for (String line : body.split("\n", -1)) {
                if (line.strip().startsWith("*") || line.strip().startsWith("//")) {
                    continue;
                }
                if (!line.contains(".divide(")) {
                    continue;
                }
                if (line.contains("RoundingMode") || line.contains("MathContext")
                        || line.contains("ROUNDING") || line.contains("Money.")) {
                    continue;
                }
                // The deprecated int constants (BigDecimal.ROUND_HALF_UP) are deliberately NOT
                // accepted. They are removed in newer JDKs and, unlike RoundingMode, an int
                // argument in that position is indistinguishable from a scale at a glance.
                violations.add("%s → %s".formatted(BACKEND_ROOT.relativize(path), line.strip()));
            }
        });

        assertThat(violations)
                .as("""
                    BigDecimal.divide without a rounding mode throws ArithmeticException the \
                    first time a quotient does not terminate — one third of a kwacha — and when \
                    it does terminate it returns whatever scale the exact result needs. Both are \
                    wrong: the first is a 500 at checkout, the second is a sub-ngwee amount \
                    persisted as Decimal128 and summed later.""")
                .isEmpty();
    }

    @Test
    @DisplayName("every document holding money carries a currency sibling")
    void moneyDocumentsDeclareTheirCurrency() throws IOException {
        List<String> missing = new ArrayList<>();

        forEachProductionSource((path, body) -> {
            if (!DOCUMENT.matcher(body).find()) {
                return;
            }
            if (!MONEY_FIELD.matcher(body).find()) {
                return;
            }
            if (!CURRENCY_FIELD.matcher(body).find()) {
                String fileName = path.getFileName().toString();
                missing.add(fileName.substring(0, fileName.length() - ".java".length()));
            }
        });

        assertThat(missing)
                .as("""
                    §4's type rules pair every monetary field with `currency: String`, default \
                    ZMW. Launch being single-currency is exactly why the field has to exist now: \
                    once amounts without one are in the database, the currency of each is a \
                    question about when it was written.""")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    /**
     * A field holds money if its name says so — and a rate, a percentage or a change is not
     * money however monetary the noun in front of it is.
     *
     * <p>{@code commissionRate} and {@code refundPercentage} are ratios: a {@code double} holds
     * them adequately, and forcing them to {@code BigDecimal} would say something untrue about
     * what they are. {@code minimumPayoutAmount} is money and was a {@code Double}. Without the
     * exclusion the rule flags all of them, and a rule that cries wolf on five of seven gets
     * a blanket suppression rather than a fix.</p>
     */
    private static boolean looksLikeMoney(String fieldName) {
        String lower = fieldName.toLowerCase();
        boolean ratio = Stream.of("rate", "percent", "percentage", "ratio", "change", "factor")
                .anyMatch(lower::contains);
        if (ratio) {
            return false;
        }
        return Stream.of("amount", "price", "fee", "balance", "total", "commission",
                        "revenue", "payout", "refund", "cost", "subtotal", "discount")
                .anyMatch(lower::contains);
    }

    @FunctionalInterface
    private interface SourceVisitor {
        void visit(Path path, String body) throws IOException;
    }

    private static void forEachProductionSource(SourceVisitor visitor) throws IOException {
        int scanned = 0;
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                        scanned++;
                        visitor.visit(path, Files.readString(path));
                    }
                }
            }
        }
        assertThat(scanned).as("an empty sweep is not a passing lint").isGreaterThan(200);
    }
}
