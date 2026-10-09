package com.pml.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation actually runs, and covers more inputs over time.
 *
 * <h2>Two different failures, checked separately</h2>
 * <ol>
 *   <li><b>The mechanism.</b> A resolver without {@code @Validated}, or an input
 *       argument without {@code @Valid}, means Bean Validation never executes
 *       for that mutation. The annotations on the input class still read as
 *       though it is protected — which is how 203 constraint annotations came to
 *       exist on this platform while none of them ran. Banned outright: this is
 *       mechanical and there is no reason for a new mutation to opt out.</li>
 *   <li><b>The coverage.</b> An input class with no constraints at all is
 *       validated successfully and vacuously. Ratcheted rather than banned,
 *       because deciding the right constraint for each of ~60 remaining classes
 *       is per-field domain work, not a sweep — and a ban would invite the
 *       lint-satisfying {@code @NotNull} on every field, which is worse than
 *       nothing because it looks like validation.</li>
 * </ol>
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R5 · validation runs, and reaches more inputs over time")
class InputValidationLintTest {

    /** Input classes carrying no constraint annotation. May only decrease. */
    private static final Map<String, Integer> UNCONSTRAINED_BUDGET = Map.of(
            "booking-service", 21,
            "catalog-service", 20,
            "identity-service", 10);

    private static final Pattern CONSTRAINT = Pattern.compile(
            "@(NotNull|NotBlank|NotEmpty|Size|Min|Max|Positive|PositiveOrZero|Negative"
                    + "|Email|Pattern|Past|Future|PastOrPresent|FutureOrPresent|Digits"
                    + "|DecimalMin|DecimalMax|AssertTrue|AssertFalse|Valid)\\b");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static List<Path> javaSources(String service) throws IOException {
        Path root = backendRoot().resolve(service).resolve("src/main/java");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    @Test
    @DisplayName("every mutation resolver is @Validated and every input argument is @Valid")
    void validationIsActuallyWired() throws IOException {
        List<String> unwired = new ArrayList<>();

        for (String service : UNCONSTRAINED_BUDGET.keySet()) {
            for (Path source : javaSources(service)) {
                String text = Files.readString(source);
                if (!text.contains("@DgsMutation")) {
                    continue;
                }
                String relative = backendRoot().relativize(source).toString();

                if (!text.contains("@Validated")) {
                    unwired.add(relative + " — class is not @Validated");
                }
                // Every @InputArgument of an input type must carry @Valid.
                for (String line : text.split("\n")) {
                    if (line.contains("@InputArgument")
                            && line.matches(".*\\b[A-Za-z0-9_]*Input\\b.*")
                            && !line.contains("@Valid")) {
                        unwired.add(relative + " —" + line.strip());
                    }
                }
            }
        }

        assertThat(unwired)
                .as("""
                    without @Validated on the class and @Valid on the argument, Bean \
                    Validation never runs — while the constraint annotations on the input \
                    class still read as though the field is protected. That is how this \
                    platform came to hold 203 constraint annotations none of which \
                    executed.""")
                .isEmpty();
    }

    @Test
    @DisplayName("input classes without any constraint only ever decrease")
    void unconstrainedInputsShrink() throws IOException {
        Map<String, Integer> measured = new LinkedHashMap<>();

        for (String service : UNCONSTRAINED_BUDGET.keySet()) {
            int unconstrained = 0;
            for (Path source : javaSources(service)) {
                if (!source.getFileName().toString().endsWith("Input.java")) {
                    continue;
                }
                if (!CONSTRAINT.matcher(Files.readString(source)).find()) {
                    unconstrained++;
                }
            }
            measured.put(service, unconstrained);
        }

        List<String> grown = new ArrayList<>();
        List<String> shrunk = new ArrayList<>();

        measured.forEach((service, count) -> {
            int budget = UNCONSTRAINED_BUDGET.get(service);
            if (count > budget) {
                grown.add("%s: %d unconstrained, budget %d".formatted(service, count, budget));
            } else if (count < budget) {
                shrunk.add("%s: %d unconstrained, budget still %d".formatted(service, count, budget));
            }
        });

        assertThat(grown)
                .as("""
                    a new input type with no constraints validates vacuously: @Valid runs, \
                    finds nothing to check, and the mutation accepts anything. Declare the \
                    constraints the field actually has.""")
                .isEmpty();

        assertThat(shrunk)
                .as("""
                    constraints were added without lowering the budget, which leaves room \
                    for the next unconstrained input to slip in unnoticed. Lower \
                    UNCONSTRAINED_BUDGET to the measured value.""")
                .isEmpty();
    }
}
