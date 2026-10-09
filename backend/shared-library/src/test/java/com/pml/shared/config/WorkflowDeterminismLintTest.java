package com.pml.shared.config;

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

/**
 * Workflow code is deterministic, activities never publish directly, and workflow payloads carry
 * identifiers rather than personal data.
 *
 * <h2>Why a lint and not only a replay test</h2>
 * A workflow that reads {@code Instant.now()} passes every test that runs it once. It fails only
 * when a worker replays the history after a deploy or a crash, reads a different time, takes a
 * different branch, and the server refuses the task as non-deterministic — in production, on an
 * execution that was holding someone's money. The construct is visible in source long before that.
 */
@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015-R3 · workflows are deterministic; activities stage, never send; payloads carry ids")
class WorkflowDeterminismLintTest {

    private static final Path BACKEND = Path.of("..");
    private static final List<String> SERVICES = List.of("booking", "catalog", "identity");

    /** What workflow code must never reach: I/O, wall-clock time, randomness, threads, reactive types. */
    static final Pattern NON_DETERMINISTIC = Pattern.compile(
            "\\b(ReactiveMongoTemplate|StreamBridge|WebClient|Mono|Flux|Thread|Clock|\\w+Repository|\\w+ServiceImpl)\\b"
                    + "|\\bInstant\\.now\\b|\\bLocalDateTime\\.now\\b|\\bUUID\\.randomUUID\\b"
                    + "|\\bSystem\\.currentTimeMillis\\b|\\bnew\\s+Random\\b"
                    + "|\\bgetInfo\\(\\)\\s*\\.\\s*get(WorkflowId|RunId)\\b");

    /** A record component named for personal data. */
    static final Pattern PERSONAL = Pattern.compile(
            "(?i)\\b\\w*(phone|msisdn|email|firstname|lastname|fullname|address|nationalid|passport|documentnumber)\\w*\\s*[,)]");

    private static final Pattern RECORD = Pattern.compile("\\brecord\\s+\\w+\\s*\\(([^)]*)\\)");

    @Test
    @DisplayName("R3 · no @WorkflowImpl class reaches I/O, the clock, randomness or threads")
    void workflowsAreDeterministic() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            String body = withoutComments(Files.readString(file));
            if (!body.contains("@WorkflowImpl")) {
                continue;
            }
            Matcher hit = NON_DETERMINISTIC.matcher(body);
            if (hit.find()) {
                offenders.add("%s uses %s".formatted(BACKEND.relativize(file), hit.group()));
            }
        }
        assertThat(offenders)
                .as("time, ids and sleeps come from io.temporal.workflow.Workflow; everything else is an activity")
                .isEmpty();
    }

    @Test
    @DisplayName("R4 · no @ActivityImpl class sends to the bus; it stages through the outbox")
    void activitiesNeverSend() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            String body = withoutComments(Files.readString(file));
            if (body.contains("@ActivityImpl") && body.contains("StreamBridge")) {
                offenders.add(BACKEND.relativize(file).toString());
            }
        }
        assertThat(offenders).as("an activity that publishes reopens the lost-message window the outbox closes").isEmpty();
    }

    @Test
    @DisplayName("R7 · no record in a workflow package carries personal data")
    void payloadsCarryIds() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            if (!file.toString().replace('\\', '/').contains("/workflow/")) {
                continue;
            }
            Matcher record = RECORD.matcher(withoutComments(Files.readString(file)));
            while (record.find()) {
                Matcher personal = PERSONAL.matcher(record.group(1) + ")");
                if (personal.find()) {
                    offenders.add("%s: %s".formatted(BACKEND.relativize(file), personal.group().replaceAll("[,)]$", "")));
                }
            }
        }
        assertThat(offenders).as("workflow history is plain text on the Temporal server; load details by id").isEmpty();
    }

    @Test
    @DisplayName("the patterns catch what they exist to catch")
    void patternsAreNotVacuous() {
        assertThat(NON_DETERMINISTIC.matcher("Instant at = Instant.now();").find()).isTrue();
        assertThat(NON_DETERMINISTIC.matcher("private final PayoutRequestRepository repo;").find()).isTrue();
        assertThat(NON_DETERMINISTIC.matcher("String id = UUID.randomUUID().toString();").find()).isTrue();
        assertThat(NON_DETERMINISTIC.matcher("Workflow.sleep(Duration.ofDays(7));").find()).isFalse();
        assertThat(NON_DETERMINISTIC.matcher("long now = Workflow.currentTimeMillis();").find()).isFalse();
        assertThat(NON_DETERMINISTIC.matcher("String id = Workflow.getInfo().getWorkflowId();").find())
                .as("an execution's own id is run metadata a replay does not reproduce; carry business ids in the payload")
                .isTrue();
        assertThat(PERSONAL.matcher("String payoutId, String phoneNumber)").find()).isTrue();
        assertThat(PERSONAL.matcher("String payoutId, BigDecimal amount)").find()).isFalse();
    }

    private static List<Path> mainSources() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String service : SERVICES) {
            try (Stream<Path> walk = Files.walk(BACKEND.resolve(service + "-service/src/main/java"))) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }

    private static String withoutComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
