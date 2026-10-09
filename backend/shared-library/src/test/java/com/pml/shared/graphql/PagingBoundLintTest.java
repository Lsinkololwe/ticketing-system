package com.pml.shared.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every pagination input enforces a maximum, and refuses rather than clamps.
 *
 * <h2>What this was written for</h2>
 * {@code PageSize.require} existed, was well documented, and was called by <b>three of seven</b>
 * pagination input types. The four that did not simply returned the client's number:
 *
 * <pre>{@code
 * if (size == null) size = 20;
 * ...
 * public int getLimit() { return size; }     // whatever was asked for
 * }</pre>
 *
 * <p>Eighty-one paged fields across the three subgraphs take {@code OffsetPaginationInput}, and all
 * three copies of it were in that group — so {@code size: 1000000} became {@code limit(1000000)},
 * on operator finance tables among others.</p>
 *
 * <h2>The sibling comparison again</h2>
 * Seven types doing one job, three of them enforcing. Where several things do the same job,
 * open the one that does not — it is the most reliable way to find a missing guard. The reason
 * it keeps working is visible here: the rule was written once, on the type somebody was thinking
 * about, and the copies were added later by people who assumed the guard was elsewhere.
 *
 * <h2>Why a source scan</h2>
 * The claim is that every pagination input <em>routes through</em> the shared rule. A runtime test
 * proves one input refuses one number; only reading all of them proves none was missed, which is
 * exactly the failure that occurred.
 */
@Tag("L1")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R5 · every pagination input enforces a maximum")
class PagingBoundLintTest {

    private static final List<String> SERVICES = List.of(
            "catalog-service", "booking-service", "identity-service");

    /** Any input type whose whole purpose is to carry a page size. */
    private static final List<String> PAGINATION_INPUTS = List.of(
            "OffsetPaginationInput", "CursorPaginationInput", "PageableInput", "PaginationInput");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static List<Path> paginationSources() throws IOException {
        List<Path> found = new ArrayList<>();
        for (String service : SERVICES) {
            Path root = backendRoot().resolve(service).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> PAGINATION_INPUTS.stream()
                                .anyMatch(n -> p.getFileName().toString().equals(n + ".java")))
                        .forEach(found::add);
            }
        }
        return found;
    }

    @Test
    @DisplayName("every pagination input routes its size through PageSize")
    void everyPaginationInputEnforcesTheMaximum() throws IOException {
        List<Path> inputs = paginationSources();
        List<String> unbounded = new ArrayList<>();

        for (Path input : inputs) {
            // Comments stripped: this file's own explanation of the rule names PageSize, and a
            // scan that counts prose would report a documented-but-unenforced type as compliant —
            // which is the precise shape of the defect being guarded against.
            String source = Files.readString(input)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("(?m)//.*$", "");
            if (!source.contains("PageSize.require")) {
                unbounded.add(backendRoot().relativize(input).toString());
            }
        }

        assertThat(inputs)
                .as("no pagination inputs found — the naming convention changed and this lint has "
                        + "been passing against an empty set")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(unbounded)
                .as("a pagination input that returns the client's number unchecked lets one "
                        + "request pull an entire collection. Route it through PageSize.require, "
                        + "which refuses above the ceiling rather than silently reducing to it.")
                .isEmpty();
    }

    @Test
    @DisplayName("PageSize refuses above the ceiling and does not clamp")
    void itRefusesRatherThanClamps() {
        assertThat(PageSize.require(100)).isEqualTo(100);
        assertThat(PageSize.require(null)).isEqualTo(PageSize.DEFAULT);

        // Refuse, never clamp. A clamp is worse than a refusal precisely because
        // it succeeds: the caller receives 100 of the 1000 rows it asked for, sees a full page,
        // and concludes it has read everything.
        try {
            PageSize.require(101);
            throw new AssertionError("101 must be refused, not reduced to 100");
        } catch (PageSize.PageSizeExceeded expected) {
            assertThat(expected.getMessage()).contains("101");
        }
    }

    @Test
    @DisplayName("a page of nothing is refused too")
    void zeroAndNegativeAreRefused() {
        // Not pedantry: `size: 0` on an offset page yields limit(0), an empty result, and a client
        // that concludes the collection is empty rather than that it asked wrongly.
        for (int bad : new int[] {0, -1, -100}) {
            try {
                PageSize.require(bad);
                throw new AssertionError("page size " + bad + " must be refused");
            } catch (IllegalArgumentException expected) {
                assertThat(expected.getMessage()).contains("at least 1");
            }
        }
    }
}
