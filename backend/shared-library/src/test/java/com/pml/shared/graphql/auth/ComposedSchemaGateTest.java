package com.pml.shared.graphql.auth;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule each service's gate lint applies, evaluated once over the three services together.
 *
 * <h2>Why a separate source scan</h2>
 * The per-service lint decides a field by loading the resolver classes, which only exists
 * inside the service that owns them: this module cannot see them. What the lint reads from a
 * resolver is narrow, though — whether the method answering a root field, or its class, carries
 * {@code @PreAuthorize} — and that is visible in the source text. This test reads that same fact
 * from the source files directly, so it applies the same rule over the union rather than a weaker
 * one, at the cost of recognising annotations textually.
 *
 * <p>It is a backstop for the union, not a replacement: a field this scan misreads is a failure
 * to investigate, and the per-service lint remains the exact check.</p>
 */
@Tag("L4")
@Tag("ET-PLT-007")
@DisplayName("no query or mutation in any of the three services is left without an access decision")
class ComposedSchemaGateTest {

    private record Service(String name, String module, String packagePath) {

        Path schema() {
            return Path.of(module, "src/main/resources/graphql/schema.graphqls");
        }

        Path resolvers() {
            return Path.of(module, "src/main/java", packagePath, "web/graphql");
        }
    }

    private static final List<Service> SERVICES = List.of(
            new Service("identity", "../identity-service", "com/pml/identity"),
            new Service("catalog", "../catalog-service", "com/pml/catalog"),
            new Service("booking", "../booking-service", "com/pml/booking"));

    /** What one service's sources say about its root fields. */
    private record Findings(Map<String, Boolean> rootFields, Set<String> gatedByAnnotation, int rootResolvers) {

        List<String> undecided() {
            List<String> undecided = new ArrayList<>();
            rootFields.forEach((coordinate, hasDirective) -> {
                if (!hasDirective && !gatedByAnnotation.contains(coordinate)) {
                    undecided.add(coordinate);
                }
            });
            return undecided;
        }
    }

    private static final Pattern DGS_ENTRY = Pattern.compile("@Dgs(Query|Mutation|Data)\\b(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern FIELD_ARG = Pattern.compile("\\bfield\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern PARENT_ARG = Pattern.compile("\\bparentType\\s*=\\s*\"(\\w+)\"");
    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(?:class|interface|record|enum)\\s+\\w+");
    private static final Pattern LAST_IDENTIFIER = Pattern.compile("(\\w+)\\s*$");

    private static Findings examine(Service service) {
        Map<String, Boolean> rootFields = rootFieldsOf(service.schema());

        Set<String> gated = new HashSet<>();
        int resolvers = 0;
        try (Stream<Path> files = Files.walk(service.resolvers())) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = withoutComments(Files.readString(file));
                resolvers += scan(code, gated);
            }
        } catch (IOException unreadable) {
            throw new AssertionError("resolver sources could not be read: "
                    + service.resolvers().toAbsolutePath().normalize(), unreadable);
        }
        return new Findings(rootFields, gated, resolvers);
    }

    /** {@code Type.field} for every Query and Mutation field, mapped to whether the schema gates it. */
    private static Map<String, Boolean> rootFieldsOf(Path schema) {
        String sdl;
        try {
            sdl = Files.readString(schema);
        } catch (IOException unreadable) {
            throw new AssertionError("schema could not be read: " + schema.toAbsolutePath().normalize(), unreadable);
        }
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);

        Map<String, Boolean> fields = new LinkedHashMap<>();
        for (String root : new TreeSet<>(List.of("Query", "Mutation"))) {
            List<FieldDefinition> definitions = new ArrayList<>();
            registry.getType(root, ObjectTypeDefinition.class)
                    .ifPresent(type -> definitions.addAll(type.getFieldDefinitions()));
            registry.objectTypeExtensions().getOrDefault(root, List.of())
                    .forEach((ObjectTypeExtensionDefinition extension) ->
                            definitions.addAll(extension.getFieldDefinitions()));
            for (FieldDefinition definition : definitions) {
                fields.put(root + "." + definition.getName(), !definition.getDirectives("auth").isEmpty());
            }
        }
        return fields;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)/{2}.*$", "");
    }

    /**
     * Adds to {@code gated} the root coordinates a source file answers behind {@code @PreAuthorize},
     * and returns how many root resolvers it declares in total.
     */
    private static int scan(String code, Set<String> gated) {
        Matcher type = TYPE_DECLARATION.matcher(code);
        int firstType = type.find() ? type.start() : 0;
        boolean classGated = code.substring(0, firstType).contains("@PreAuthorize");

        int resolvers = 0;
        Matcher entry = DGS_ENTRY.matcher(code);
        while (entry.find()) {
            String kind = entry.group(1);
            String args = entry.group(2) == null ? "" : entry.group(2);

            String parent = switch (kind) {
                case "Query" -> "Query";
                case "Mutation" -> "Mutation";
                default -> {
                    Matcher parentArg = PARENT_ARG.matcher(args);
                    yield parentArg.find() ? parentArg.group(1) : "";
                }
            };
            if (!parent.equals("Query") && !parent.equals("Mutation")) {
                continue;
            }

            int cursor = afterFurtherAnnotations(code, entry.end());
            int open = code.indexOf('(', cursor);
            if (open < 0) {
                continue;
            }
            Matcher method = LAST_IDENTIFIER.matcher(code.substring(cursor, open));
            if (!method.find()) {
                continue;
            }

            Matcher fieldArg = FIELD_ARG.matcher(args);
            String field = fieldArg.find() ? fieldArg.group(1) : method.group(1);

            // The annotations on a method sit between the end of the previous member and its
            // signature, so the header is read from there, covering ones written above the
            // Dgs annotation as well as below it.
            int memberStart = Math.max(code.lastIndexOf(';', entry.start()),
                    Math.max(code.lastIndexOf('}', entry.start()), code.lastIndexOf('{', entry.start()))) + 1;
            boolean methodGated = code.substring(memberStart, cursor).contains("@PreAuthorize");

            resolvers++;
            if (classGated || methodGated) {
                gated.add(parent + "." + field);
            }
        }
        return resolvers;
    }

    /** Skips whitespace and any annotations (with their argument lists) to reach the signature. */
    private static int afterFurtherAnnotations(String code, int from) {
        int cursor = from;
        while (true) {
            while (cursor < code.length() && Character.isWhitespace(code.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= code.length() || code.charAt(cursor) != '@') {
                return cursor;
            }
            cursor++;
            while (cursor < code.length()
                    && (Character.isJavaIdentifierPart(code.charAt(cursor)) || code.charAt(cursor) == '.')) {
                cursor++;
            }
            while (cursor < code.length() && Character.isWhitespace(code.charAt(cursor))) {
                cursor++;
            }
            if (cursor < code.length() && code.charAt(cursor) == '(') {
                cursor = afterBalancedParentheses(code, cursor);
            }
        }
    }

    private static int afterBalancedParentheses(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"') {
                i++;
                while (i < code.length() && code.charAt(i) != '"') {
                    i += code.charAt(i) == '\\' ? 2 : 1;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i + 1;
            }
        }
        return code.length();
    }

    @Test
    @DisplayName("every service's schema and resolvers are found and non-empty")
    void everyServiceIsExamined() {
        for (Service service : SERVICES) {
            Findings findings = examine(service);

            assertThat(findings.rootFields())
                    .as("%s declares Query and Mutation fields; none found means the schema was not read",
                            service.name())
                    .isNotEmpty();
            assertThat(findings.rootResolvers())
                    .as("%s declares root resolvers; none found means the source scan is broken and "
                            + "would report every field as decided or undecided for the wrong reason",
                            service.name())
                    .isPositive();
        }
    }

    @Test
    @DisplayName("over the union of all three services, no root field lacks @auth or @PreAuthorize")
    void noRootFieldIsUndecidedAcrossTheComposedSchema() {
        List<String> undecided = new ArrayList<>();
        StringBuilder counts = new StringBuilder();
        int total = 0;

        for (Service service : SERVICES) {
            Findings findings = examine(service);
            total += findings.rootFields().size();
            counts.append("%s=%d ".formatted(service.name(), findings.rootFields().size()));
            findings.undecided().forEach(field -> undecided.add(service.name() + ": " + field));
        }

        assertThat(undecided)
                .as("%d root fields examined across the composed schema (%s); each needs "
                        + "@auth(requires: ...) in the schema, or @auth(requires: PUBLIC) if it is "
                        + "meant to be open, or @PreAuthorize on its resolver",
                        total, counts.toString().trim())
                .isEmpty();
    }
}
