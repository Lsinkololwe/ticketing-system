package com.pml.identity.auth;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A code or its digest is never compared with {@code String.equals}, {@code ==} or {@code ~=}, whose
 * running time depends on how much of the guess was right. The comparison is the constant-time
 * fold inside the verify script.
 */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("no code or digest is compared with equals or == in the challenge and proof packages")
class ConstantTimeComparisonSourceTest {

    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*|--[^\\n]*", Pattern.DOTALL);

    private static final List<Pattern> NON_CONSTANT_TIME = List.of(
            Pattern.compile("(?i)(?<!error)\\w*(code|digest|hmac|pepper)\\w*(\\(\\))?\\s*\\.equals(IgnoreCase)?\\("),
            Pattern.compile("(?i)\\.equals(IgnoreCase)?\\(\\s*(?<!error)\\w*(code|digest|hmac|pepper)"),
            Pattern.compile("(?i)(?<!error)\\b\\w*(code|digest|hmac|pepper)\\w*\\s*[!=]=\\s*+(?!null)"),
            Pattern.compile("(rec\\[1]|ARGV\\[1])\\s*[~=]="));

    @Test
    @DisplayName("neither package compares a code or digest the ordinary way, and the verify script keeps its constant-time fold")
    void noOrdinaryComparison() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String dir : List.of("challenge", "proof")) {
            Path root = Path.of("src/main/java/com/pml/identity/auth", dir);
            Assumptions.assumeTrue(Files.isDirectory(root), "identity sources not present");
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        List<String> offenders = new ArrayList<>();
        String scripts = "";
        for (Path file : files) {
            String source = Files.readString(file);
            if (file.getFileName().toString().equals("ChallengeScripts.java")) {
                scripts = source;
            }
            String code = COMMENTS.matcher(source).replaceAll("");
            for (Pattern pattern : NON_CONSTANT_TIME) {
                var matcher = pattern.matcher(code);
                while (matcher.find()) {
                    offenders.add(file.getFileName() + ": " + matcher.group().trim());
                }
            }
        }
        assertThat(offenders).isEmpty();
        assertThat(scripts).contains("bit.bxor").contains("bit.bor");
    }
}
