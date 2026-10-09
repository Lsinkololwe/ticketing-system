package com.pml.shared.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * An over-limit page is refused, and refusing is the point.
 *
 * <h2>What clamping actually costs</h2>
 * A great many pagination loops terminate on <em>"I asked for N and received fewer than N, so
 * that was the last page"</em>. Clamping breaks precisely that loop: the client asks for 500,
 * receives 100, concludes it has read everything, and stops — having seen a fifth of the data.
 * No error is raised at any layer. The server was being defensive, the client was being
 * reasonable, and the outcome is silent data loss.
 *
 * <p>That is why the rule is <em>refused, never clamped</em>, and why this test asserts the absence
 * of clamping across production source rather than only the presence of the check. A second
 * `Math.min(size, 100)` added anywhere would restore the bug while every test about
 * {@link PageSize} kept passing.</p>
 */
@Tag("L1")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R5 · an over-limit page size is refused, not reduced")
class PageSizeTest {

    private static final List<String> SERVICES =
            List.of("catalog-service", "booking-service", "identity-service");

    @Test
    @DisplayName("a size above the maximum is refused, and the error names both numbers")
    void overLimitIsRefused() {
        assertThatThrownBy(() -> PageSize.require(500))
                .isInstanceOf(PageSize.PageSizeExceeded.class)
                .hasMessageContaining("500")
                .hasMessageContaining("100");

        PageSize.PageSizeExceeded refusal = catchRefusal(() -> PageSize.require(101));
        assertThat(refusal.requested()).isEqualTo(101);
        assertThat(refusal.ceiling()).isEqualTo(PageSize.MAX);
        assertThat(refusal.code())
                .as("ET-ADM/ET-PLT-005 clients branch on the code, not the message")
                .isEqualTo("PAGE_SIZE_EXCEEDED");
    }

    @Test
    @DisplayName("a narrower field refuses against its own ceiling rather than reducing to it")
    void narrowerCeilingsAlsoRefuse() {
        assertThat(PageSize.require(30, 50, 10)).isEqualTo(30);
        assertThat(PageSize.require(null, 50, 10)).isEqualTo(10);

        PageSize.PageSizeExceeded refusal = catchRefusal(() -> PageSize.require(100, 50, 10));
        assertThat(refusal.ceiling())
                .as("""
                    A dashboard widget capped at 50 has the same problem as a page capped at 100, \
                    and hits it sooner: the caller asked for 100, would silently receive 50, and \
                    has no way to tell that from there being only 50.""")
                .isEqualTo(50);
        assertThat(refusal).hasMessageContaining("50");
    }

    private static PageSize.PageSizeExceeded catchRefusal(Runnable action) {
        try {
            action.run();
        } catch (PageSize.PageSizeExceeded refusal) {
            return refusal;
        }
        throw new AssertionError("expected PageSizeExceeded, nothing was thrown");
    }

    @Test
    @DisplayName("the maximum itself is allowed — the boundary is inclusive")
    void theBoundaryIsInclusive() {
        assertThat(PageSize.require(PageSize.MAX)).isEqualTo(100);
        assertThatCode(() -> PageSize.require(PageSize.MAX)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no size means the default, and a non-positive size is a caller bug")
    void absentAndNonsensicalSizes() {
        assertThat(PageSize.require(null)).isEqualTo(PageSize.DEFAULT);

        assertThatThrownBy(() -> PageSize.require(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
        assertThatThrownBy(() -> PageSize.require(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("no production source clamps a page size instead of refusing it")
    void nothingClampsAPageSize() throws IOException {
        // Math.min over a size/limit/first/last variable — the shape of a silent clamp.
        Pattern clamp = Pattern.compile(
                "Math\\.min\\s*\\([^;]*\\b(size|limit|first|last|pageSize)\\b[^;]*\\)",
                Pattern.CASE_INSENSITIVE);

        List<String> problems = new ArrayList<>();
        for (String service : SERVICES) {
            Path root = Path.of("../%s/src/main/java".formatted(service));
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path source : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = Files.readString(source);
                    Matcher matcher = clamp.matcher(code);
                    while (matcher.find()) {
                        problems.add("%s: %s".formatted(
                                source.getFileName(), matcher.group().replaceAll("\\s+", " ")));
                    }
                }
            }
        }

        assertThat(problems)
                .as("""
                    §4: refused with PAGE_SIZE_EXCEEDED, never clamped — a clamped page is a \
                    client that believes it has read everything. Route the value through \
                    PageSize.require instead.""")
                .isEmpty();
    }
}
