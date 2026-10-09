package com.pml.identity.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The GraphQL operation documents the web apps really send, read straight from the frontend sources
 * ({@code export const NAME = gql`...`}), with {@code ${FRAGMENT}} interpolations resolved the way the
 * bundler does. A contract test that runs these against the real service catches the failures unit
 * tests cannot: a non-null field nothing resolves, an enum value the schema lacks.
 */
final class FrontendOperations {

    private static final Pattern BLOCK = Pattern.compile("(?:export )?const (\\w+)\\s*=\\s*gql`(.*?)`", Pattern.DOTALL);
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{(\\w+)}");
    private static final Pattern OPERATION = Pattern.compile("\\b(query|mutation|subscription)\\s+(\\w+)\\s*(\\(([^)]*)\\))?");
    private static final Pattern FRAGMENT = Pattern.compile("fragment\\s+(\\w+)\\s+on");

    /** One runnable document: its name, kind, text and declared variables (name to type text). */
    record Operation(String name, String kind, String document, Map<String, String> variables) {
    }

    private FrontendOperations() {
    }

    static Path sharedApiRoot() {
        Path here = Path.of("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            Path candidate = p.resolve("frontend/web/libs/shared/src/api");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Every query and mutation document under the given sub-directories of libs/shared/src/api. */
    static List<Operation> load(String... subdirs) throws IOException {
        Path root = sharedApiRoot();
        List<Operation> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        for (String sub : subdirs) {
            try (Stream<Path> files = Files.walk(root.resolve(sub))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".queries.ts") || f.toString().endsWith(".mutations.ts")).toList()) {
                    out.addAll(parse(Files.readString(file)));
                }
            }
        }
        return out;
    }

    /** Operation documents declared inside the apps themselves (outside libs/shared), by app directory name. */
    static List<Operation> loadApps(String... apps) throws IOException {
        Path root = sharedApiRoot();
        List<Operation> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        Path appsDir = root.getParent().getParent().getParent().getParent().getParent().resolve("apps");
        for (String app : apps) {
            Path src = appsDir.resolve(app).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path file : files.filter(f -> (f.toString().endsWith(".ts") || f.toString().endsWith(".tsx"))
                        && !f.toString().contains("__tests__") && !f.toString().contains(".test.")).toList()) {
                    String text = Files.readString(file);
                    if (text.contains("gql`")) {
                        out.addAll(parse(text));
                    }
                }
            }
        }
        return out;
    }

    private static List<Operation> parse(String source) {
        Map<String, String> blocks = new LinkedHashMap<>();
        Matcher m = BLOCK.matcher(source);
        while (m.find()) {
            blocks.put(m.group(1), m.group(2));
        }
        List<Operation> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            Matcher op = OPERATION.matcher(entry.getValue().replaceAll("(?s)fragment\\s+\\w+\\s+on.*", ""));
            if (!op.find()) {
                continue;
            }
            String document = resolve(entry.getValue(), blocks, new java.util.LinkedHashSet<>());
            Map<String, String> variables = new LinkedHashMap<>();
            if (op.group(4) != null) {
                for (String declaration : op.group(4).split(",(?![^\\[]*])")) {
                    String[] parts = declaration.trim().split(":", 2);
                    if (parts.length == 2) {
                        variables.put(parts[0].trim().replace("$", ""), parts[1].trim());
                    }
                }
            }
            out.add(new Operation(op.group(2), op.group(1), document, variables));
        }
        return out;
    }

    /** Inlines every interpolated fragment once. */
    private static String resolve(String text, Map<String, String> blocks, java.util.Set<String> seen) {
        Matcher m = INTERPOLATION.matcher(text);
        StringBuilder head = new StringBuilder();
        StringBuilder fragments = new StringBuilder();
        int last = 0;
        while (m.find()) {
            head.append(text, last, m.start());
            last = m.end();
            String name = m.group(1);
            String body = blocks.get(name);
            if (body != null && seen.add(name)) {
                fragments.append('\n').append(resolve(body, blocks, seen));
            }
        }
        head.append(text.substring(last));
        return head + fragments.toString();
    }

    static boolean declaresFragment(String document, String name) {
        Matcher m = FRAGMENT.matcher(document);
        while (m.find()) {
            if (m.group(1).equals(name)) {
                return true;
            }
        }
        return false;
    }
}
