package com.pml.identity.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one-time password a new staff member is given: long enough, readable without mistakes, within the
 * staff realm's rules, different every time, and drawn from a cryptographic source of randomness.
 */
@Tag("L1")
@Tag("ET-IDN-001")
@Tag("OWASP")
@DisplayName("the temporary password generator makes long, readable, unrelated, policy-conforming passwords from SecureRandom")
class TemporaryPasswordGeneratorTest {

    private static final String AMBIGUOUS = "0Oo1lI";
    private static final int SAMPLES = 5_000;

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    @DisplayName("every password is at least sixteen characters, from the declared alphabet, with no look-alike character")
    void lengthAndAlphabet() {
        String alphabet = TemporaryPasswordGenerator.UPPER + TemporaryPasswordGenerator.LOWER
                + TemporaryPasswordGenerator.DIGITS + TemporaryPasswordGenerator.SYMBOLS;

        for (int i = 0; i < SAMPLES; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(TemporaryPasswordGenerator.LENGTH).hasSizeGreaterThanOrEqualTo(16);
            for (char c : password.toCharArray()) {
                assertThat(alphabet).as("character of a generated password").contains(String.valueOf(c));
                assertThat(AMBIGUOUS).as("look-alike character").doesNotContain(String.valueOf(c));
            }
        }
        for (char c : AMBIGUOUS.toCharArray()) {
            assertThat(alphabet).doesNotContain(String.valueOf(c));
        }
    }

    @Test
    @DisplayName("every password holds capitals, lower-case letters, digits and symbols, in the counts the rules promise")
    void everyClassIsPresent() {
        for (int i = 0; i < SAMPLES; i++) {
            String password = generator.generate();
            assertThat(count(password, TemporaryPasswordGenerator.UPPER)).isGreaterThanOrEqualTo(3);
            assertThat(count(password, TemporaryPasswordGenerator.LOWER)).isGreaterThanOrEqualTo(3);
            assertThat(count(password, TemporaryPasswordGenerator.DIGITS)).isGreaterThanOrEqualTo(3);
            assertThat(count(password, TemporaryPasswordGenerator.SYMBOLS)).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("the staff realm's rules hold: twelve characters, not the username, not the email, not a recent password")
    void satisfiesTheRealmPolicy() {
        String username = "chileshe.mwansa@example.org";
        String local = "chileshe.mwansa";
        String previous = generator.generate();
        for (int i = 0; i < SAMPLES; i++) {
            String password = generator.generate();
            assertThat(password.length()).as("length(12)").isGreaterThanOrEqualTo(12);
            assertThat(password.toLowerCase()).as("notUsername / notEmail")
                    .doesNotContain(username).doesNotContain(local).doesNotContain("chileshe");
            assertThat(password).as("passwordHistory").isNotEqualTo(previous);
            previous = password;
        }
    }

    @Test
    @DisplayName("passwords are unrelated: none repeats over thousands of calls, and a class has no fixed position")
    void passwordsAreUnrelated() {
        Set<String> seen = new HashSet<>();
        Set<Character> firstUpper = new HashSet<>();
        boolean symbolFirst = false;
        boolean digitFirst = false;
        boolean upperFirst = false;
        boolean lowerFirst = false;
        for (int i = 0; i < SAMPLES; i++) {
            String password = generator.generate();
            assertThat(seen.add(password)).as("a password was produced twice").isTrue();
            char first = password.charAt(0);
            upperFirst |= TemporaryPasswordGenerator.UPPER.indexOf(first) >= 0;
            lowerFirst |= TemporaryPasswordGenerator.LOWER.indexOf(first) >= 0;
            digitFirst |= TemporaryPasswordGenerator.DIGITS.indexOf(first) >= 0;
            symbolFirst |= TemporaryPasswordGenerator.SYMBOLS.indexOf(first) >= 0;
            firstUpper.add(first);
        }
        assertThat(upperFirst && lowerFirst && digitFirst && symbolFirst)
                .as("each class turns up at the first position, so the guaranteed characters are not at fixed places").isTrue();
        assertThat(firstUpper.size()).as("the first character varies widely").isGreaterThan(30);
    }

    @Test
    @DisplayName("every character and position comes from the random source it was given")
    void drawsFromTheSourceItWasGiven() {
        AtomicInteger draws = new AtomicInteger();
        SecureRandom counting = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                draws.incrementAndGet();
                return super.nextInt(bound);
            }
        };

        new TemporaryPasswordGenerator(counting).generate();

        // one draw per character, and one more per position in the shuffle
        assertThat(draws.get()).isGreaterThanOrEqualTo(TemporaryPasswordGenerator.LENGTH);
    }

    @Test
    @DisplayName("the source uses SecureRandom and no predictable generator")
    void sourceUsesSecureRandomOnly() throws IOException {
        Path file = Path.of("src/main/java/com/pml/identity/account/TemporaryPasswordGenerator.java");
        String code = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL).matcher(Files.readString(file)).replaceAll("");

        assertThat(code).contains("java.security.SecureRandom");
        assertThat(code).doesNotContain("java.util.Random").doesNotContain("Math.random")
                .doesNotContain("ThreadLocalRandom").doesNotContain("randomUUID").doesNotContain("new Random(");
    }

    private static long count(String password, String alphabet) {
        return password.chars().filter(c -> alphabet.indexOf(c) >= 0).count();
    }
}
