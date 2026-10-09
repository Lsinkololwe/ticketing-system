package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every bean method a {@code @PreAuthorize} expression calls exists.
 *
 * <p>SpEL is a string: {@code @refundSecurityService.isOwnerByRequestId(...)} compiles whether or
 * not the method is there, and fails only when a caller who is not an administrator arrives —
 * the owner the check exists for is refused, every time, with an evaluation error. So each
 * {@code @bean.method(} is matched against a class that registers that bean name and declares
 * that method.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Every @PreAuthorize bean call names a method that exists")
class PreAuthorizeTargetLintTest {

    private static final Path BACKEND = Path.of("..").toAbsolutePath().normalize();
    private static final Pattern PRE_AUTHORIZE = Pattern.compile("@PreAuthorize\\(\"((?:\\\\.|[^\"\\\\])*)\"\\)");
    private static final Pattern BEAN_CALL = Pattern.compile("@(\\w+)\\.(\\w+)\\(");
    private static final Pattern NAMED_BEAN = Pattern.compile("@(?:Service|Component)\\(\"(\\w+)\"\\)");
    private static final Pattern CLASS = Pattern.compile("\\bclass (\\w+)");

    @Test
    @DisplayName("no expression calls a method its bean does not declare")
    void everyTargetExists() throws IOException {
        List<String> problems = new ArrayList<>();
        int calls = 0;
        // Beans are resolved per service: two services may each register a bean of the same name.
        for (String module : List.of("booking-service", "catalog-service", "identity-service")) {
            List<Path> sources = mainSources(module);
            Map<String, String> beanSource = new HashMap<>();
            for (Path file : sources) {
                String text = Files.readString(file);
                Matcher named = NAMED_BEAN.matcher(text);
                Matcher type = CLASS.matcher(text);
                if (named.find()) {
                    beanSource.put(named.group(1), text);
                } else if (type.find() && (text.contains("@Service") || text.contains("@Component"))) {
                    String simple = type.group(1);
                    beanSource.put(Character.toLowerCase(simple.charAt(0)) + simple.substring(1), text);
                }
            }
            for (Path file : sources) {
                Matcher annotation = PRE_AUTHORIZE.matcher(Files.readString(file));
                while (annotation.find()) {
                    Matcher call = BEAN_CALL.matcher(annotation.group(1));
                    while (call.find()) {
                        calls++;
                        String bean = call.group(1);
                        String method = call.group(2);
                        String target = beanSource.get(bean);
                        if (target == null) {
                            problems.add("%s: no bean named %s".formatted(BACKEND.relativize(file), bean));
                        } else if (!Pattern.compile("\\b" + method + "\\s*\\(").matcher(target).find()) {
                            problems.add("%s: @%s has no method %s".formatted(BACKEND.relativize(file), bean, method));
                        }
                    }
                }
            }
        }
        assertThat(calls).as("an empty sweep is not a passing lint").isGreaterThan(20);
        assertThat(problems).as("a SpEL call that cannot resolve refuses every caller it was meant to admit").isEmpty();
    }

    private static List<Path> mainSources(String module) throws IOException {
        try (Stream<Path> walk = Files.walk(BACKEND.resolve(module).resolve("src/main/java"))) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
