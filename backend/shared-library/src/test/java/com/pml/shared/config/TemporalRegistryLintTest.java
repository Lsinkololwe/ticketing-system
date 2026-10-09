package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The task queue registry, the workflow registry and the reactive boundary, held against the
 * tree.
 *
 * <h2>The defect class</h2>
 * A task queue is a string in three places: the documented registry, the worker configuration, and the
 * annotation or stub that routes work to it. When a worker polls {@code booking-finance} and a
 * workflow is started on {@code booking-finances}, both sides start cleanly and the execution waits
 * forever for a worker that is not coming. Nothing fails; a payout simply never moves.
 */
@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015 · task queues, workflow types and client use match §4")
class TemporalRegistryLintTest {

    private static final Path BACKEND = Path.of("..");
    private static final Path SPEC = BACKEND.resolve("../specs/_platform/015-durable-execution/spec.md");
    private static final List<String> SERVICES = List.of("booking", "catalog", "identity");

    private static final Pattern QUEUE_ROW = Pattern.compile("(?m)^\\| `([a-z-]+)` \\| (booking|catalog|identity) \\|");
    private static final Pattern WORKFLOW_ROW = Pattern.compile("(?m)^\\| `(\\w+Workflow)` \\| `([a-z-]+)` \\|");
    private static final Pattern YAML_QUEUE = Pattern.compile("(?m)^\\s+- task-queue:\\s*(\\S+)\\s*$");
    private static final Pattern CONSTANT = Pattern.compile("public static final String \\w+ = \"([a-z-]+)\";");
    private static final Pattern LITERAL_QUEUE = Pattern.compile("@(WorkflowImpl|ActivityImpl)\\s*\\([^)]*\"");
    private static final Pattern WORKFLOW_INTERFACE = Pattern.compile("@WorkflowInterface\\s+public\\s+interface\\s+(\\w+)");

    @Test
    @DisplayName("R1 · each service polls exactly its §4 queues, and its constants name the same set")
    void queuesMatchTheRegistry() throws IOException {
        Map<String, Set<String>> registry = new TreeMap<>();
        Matcher row = QUEUE_ROW.matcher(section(Files.readString(SPEC), "### Task queue registry"));
        while (row.find()) {
            registry.computeIfAbsent(row.group(2), k -> new TreeSet<>()).add(row.group(1));
        }
        assertThat(registry).as("§4 registers queues for all three services").containsOnlyKeys(SERVICES);

        List<String> problems = new ArrayList<>();
        for (String service : SERVICES) {
            Set<String> yaml = matches(YAML_QUEUE, withoutComments(Files.readString(
                    BACKEND.resolve(service + "-service/src/main/resources/application.yml"))));
            Set<String> constants = matches(CONSTANT, Files.readString(BACKEND.resolve(
                    service + "-service/src/main/java/com/pml/" + service + "/infrastructure/temporal/TaskQueues.java")));
            if (!yaml.equals(registry.get(service))) {
                problems.add("%s polls %s; §4 registers %s".formatted(service, yaml, registry.get(service)));
            }
            if (!constants.equals(registry.get(service))) {
                problems.add("%s TaskQueues names %s; §4 registers %s".formatted(service, constants, registry.get(service)));
            }
        }
        assertThat(problems).as("a queue nobody polls holds work forever").isEmpty();
    }

    @Test
    @DisplayName("R1 · no workflow or activity implementation names its queue by literal")
    void noLiteralQueues() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            if (LITERAL_QUEUE.matcher(withoutComments(Files.readString(file))).find()) {
                offenders.add(BACKEND.relativize(file).toString());
            }
        }
        assertThat(offenders).as("queues are TaskQueues constants").isEmpty();
    }

    @Test
    @DisplayName("§4 · every workflow interface in the tree is a registry row, on a queue its service owns")
    void workflowTypesAreRegistered() throws IOException {
        String spec = Files.readString(SPEC);
        Map<String, String> registered = new TreeMap<>();
        Matcher row = WORKFLOW_ROW.matcher(section(spec, "### Workflow registry"));
        while (row.find()) {
            registered.put(row.group(1), row.group(2));
        }

        List<String> problems = new ArrayList<>();
        for (Path file : mainSources()) {
            Matcher declared = WORKFLOW_INTERFACE.matcher(Files.readString(file));
            while (declared.find()) {
                String type = declared.group(1);
                String service = serviceOf(file);
                String queue = registered.get(type);
                if (queue == null) {
                    problems.add(type + " is not a row of the §4 workflow registry");
                } else if (!queue.startsWith(service + "-")) {
                    problems.add("%s lives in %s but §4 puts it on %s".formatted(type, service, queue));
                }
            }
        }
        assertThat(problems).as("an unregistered workflow has no declared id, queue or conflict policy").isEmpty();
    }

    @Test
    @DisplayName("R5 · only infrastructure/temporal adapters and workflow packages touch WorkflowClient")
    void clientIsConfined() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            String path = file.toString().replace('\\', '/');
            if (path.contains("/infrastructure/temporal/") || path.contains("/workflow/")) {
                continue;
            }
            if (withoutComments(Files.readString(file)).contains("WorkflowClient")) {
                offenders.add(BACKEND.relativize(file).toString());
            }
        }
        assertThat(offenders).as("a resolver holding WorkflowClient blocks a Netty worker on gRPC").isEmpty();
    }

    @Test
    @DisplayName("the patterns catch what they exist to catch")
    void patternsAreNotVacuous() {
        assertThat(LITERAL_QUEUE.matcher("@ActivityImpl(taskQueues = \"booking-finance\")").find()).isTrue();
        assertThat(LITERAL_QUEUE.matcher("@ActivityImpl(taskQueues = TaskQueues.FINANCE)").find()).isFalse();
        assertThat(YAML_QUEUE.matcher("    workers:\n      - task-queue: booking-recon\n").find()).isTrue();
        assertThat(WORKFLOW_INTERFACE.matcher("@WorkflowInterface\npublic interface PayoutWorkflow {").find()).isTrue();
    }

    private static String serviceOf(Path file) {
        return BACKEND.relativize(file).getName(0).toString().replace("-service", "");
    }

    private static List<Path> mainSources() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String service : SERVICES) {
            Path root = BACKEND.resolve(service + "-service/src/main/java");
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }

    private static String section(String markdown, String heading) {
        int start = markdown.indexOf(heading);
        assertThat(start).as(heading + " exists in ET-PLT-015").isNotNegative();
        int end = markdown.indexOf("\n### ", start + heading.length());
        return markdown.substring(start, end < 0 ? markdown.length() : end);
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static String withoutComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*#.*$", "").replaceAll("(?m)//.*$", "");
    }
}
