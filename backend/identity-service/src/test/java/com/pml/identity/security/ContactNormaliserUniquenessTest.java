package com.pml.identity.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One person typed two ways must stay one contact, so there is exactly one place that turns a typed
 * phone number or email into the stored form: the shared phone and email normalisers. This scans
 * the production sources of identity-service and shared-library and fails when a second one
 * appears: a phone-number library used outside the shared utilities, an E.164 or country-code
 * pattern written by hand, or a hand-rolled "keep digits and plus" clean-up.
 */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("there is one contact normaliser: no second phone or email parser in identity-service or shared-library")
class ContactNormaliserUniquenessTest {

    /** The only files allowed to parse or format a phone number. */
    private static final Set<String> SHARED_UTILITIES = Set.of("PhoneNumbers.java", "ContactMasking.java");

    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static final List<Pattern> SECOND_NORMALISER = List.of(
            Pattern.compile("com\\.google\\.i18n\\.phonenumbers"),
            Pattern.compile("\"\\+260\""),
            Pattern.compile("\\\\\\\\\\+\\s*(\\[1-9]|\\d)"),
            Pattern.compile("\\[\\^0-9\\+]"),
            Pattern.compile("\\btoE164\\s*\\("),
            Pattern.compile("(?i)Pattern\\.compile\\(\"\\^?\\\\\\\\\\+"));

    private static List<Path> sources() throws IOException {
        List<Path> roots = List.of(Path.of("src/main/java"), Path.of("../shared-library/src/main/java"));
        Assumptions.assumeTrue(Files.isDirectory(roots.get(0)), "identity sources not present");
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
                }
            }
        }
        return files;
    }

    @Test
    @DisplayName("no production source outside the shared phone utilities parses, formats or pattern-matches phone numbers itself")
    void noSecondPhoneNormaliser() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            if (SHARED_UTILITIES.contains(file.getFileName().toString())) {
                continue;
            }
            String code = COMMENTS.matcher(Files.readString(file)).replaceAll("");
            for (Pattern pattern : SECOND_NORMALISER) {
                if (pattern.matcher(code).find()) {
                    offenders.add(file + " matches " + pattern.pattern());
                }
            }
        }
        assertThat(offenders).as("a second contact normaliser; route it through PhoneNumbers.parseMobile or Emails.normalize").isEmpty();
    }

    @Test
    @DisplayName("an email is normalised only by Emails.normalize: no hand-written lower-casing of an address into a contact")
    void noSecondEmailNormaliser() throws IOException {
        Pattern handwritten = Pattern.compile("(?i)\\bemail\\w*(\\(\\))?\\.toLowerCase\\(");
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            String name = file.getFileName().toString();
            if (name.equals("Emails.java")) {
                continue;
            }
            String code = COMMENTS.matcher(Files.readString(file)).replaceAll("");
            if (handwritten.matcher(code).find()) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).isEmpty();
    }
}
