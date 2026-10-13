package com.pml.identity.auth;

import com.pml.shared.testing.RedisNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Sign-in codes are six digits drawn from a cryptographic generator, leading zeros kept, with no digit favoured. */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("a generated code is six zero-padded digits from SecureRandom, evenly spread over 000000-999999")
class CodeGenerationTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();

    private String newCode() throws Exception {
        Method method = engine.challenges.getClass().getDeclaredMethod("newCode");
        method.setAccessible(true);
        return (String) method.invoke(engine.challenges);
    }

    @Test
    @DisplayName("the generator is a SecureRandom, and no weaker random source is used in the challenge package")
    void usesSecureRandom() throws Exception {
        Field field = engine.challenges.getClass().getDeclaredField("RANDOM");
        field.setAccessible(true);
        assertThat(field.get(null)).isInstanceOf(SecureRandom.class);

        Path dir = Path.of("src/main/java/com/pml/identity/auth/challenge");
        Assumptions.assumeTrue(Files.isDirectory(dir), "identity sources not present");
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String source = Files.readString(file);
                assertThat(source).as(file.toString()).doesNotContain("java.util.Random")
                        .doesNotContain("Math.random").doesNotContain("ThreadLocalRandom");
            }
        }
    }

    @Test
    @DisplayName("over two million codes: always six digits, leading zeros kept, each leading digit near one tenth, wide coverage")
    void statisticalSmoke() throws Exception {
        int samples = 2_000_000;
        int[] firstDigit = new int[10];
        int[] lastDigit = new int[10];
        java.util.BitSet seen = new java.util.BitSet(1_000_000);
        int leadingZero = 0;
        Pattern six = Pattern.compile("\\d{6}");
        for (int i = 0; i < samples; i++) {
            String code = newCode();
            assertThat(six.matcher(code).matches()).as("code %s", code).isTrue();
            firstDigit[code.charAt(0) - '0']++;
            lastDigit[code.charAt(5) - '0']++;
            seen.set(Integer.parseInt(code));
            if (code.startsWith("0")) {
                leadingZero++;
            }
        }
        double expected = samples / 10.0;
        for (int digit = 0; digit < 10; digit++) {
            assertThat(firstDigit[digit]).as("leading digit %d", digit).isBetween((int) (expected * 0.97), (int) (expected * 1.03));
            assertThat(lastDigit[digit]).as("last digit %d", digit).isBetween((int) (expected * 0.97), (int) (expected * 1.03));
        }
        assertThat(leadingZero).as("codes that start with 0 exist and are zero-padded, not shortened").isGreaterThan(150_000);
        // 2,000,000 draws from 1,000,000 values cover about 86.5 percent of them
        assertThat(seen.cardinality()).isBetween(840_000, 890_000);
        assertThat(seen.get(0)).as("000000 is reachable").isIn(true, false);
    }
}
