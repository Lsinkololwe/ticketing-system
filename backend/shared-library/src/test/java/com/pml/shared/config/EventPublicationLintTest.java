package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publishing happens in one place, and never inside a transaction.
 *
 * <h2>Two rules a review cannot hold</h2>
 * <ul>
 *   <li><b>No broker client outside an {@code EventBridge}.</b> A send from a service
 *       method is a message that leaves before the transaction commits, announcing something
 *       that may never become true — and there is no unsend.</li>
 *   <li><b>No {@code @TransactionalEventListener(AFTER_COMMIT)} that rethrows.</b> An
 *       after-commit listener cannot un-commit the write, so throwing converts a delivery
 *       problem into a lie about the transaction: the caller sees a failure for work that
 *       actually happened.</li>
 * </ul>
 *
 * <h2>Ratcheted, because the migration is in progress</h2>
 * Every service publishes through its outbox, so every budget is zero and the budget only exists to
 * keep it there: a send site that appears outside {@code AmqpEventTransport} fails the build.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R2 · publishing is centralised and never transactional")
class EventPublicationLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    /** A send through a Spring AMQP template: the broker client, which only the transport may hold. */
    private static final java.util.regex.Pattern BROKER_SEND = java.util.regex.Pattern.compile(
            "\\b(?:rabbitTemplate|amqpTemplate|rabbitOperations)\\.(?:send|convertAndSend|invoke)\\(");

    /**
     * The most broker send calls each module may hold outside {@code EventBridge} and its transport.
     * May only fall, and must be lowered when it does.
     *
     * <p>Every service publishes only through its outbox, so every budget is zero.</p>
     */
    private static final Map<String, Integer> SEND_BUDGET = new LinkedHashMap<>(Map.of(
            "identity-service", 0,
            "booking-service", 0,
            "catalog-service", 0,
            "api-gateway", 0));

    @Test
    @DisplayName("a broker send appears only where the budget allows")
    void brokerSendsDoNotGrow() throws IOException {
        Map<String, Integer> actual = new LinkedHashMap<>();

        forEachProductionSource((module, path, body) -> {
            String file = path.getFileName().toString();
            if (file.equals("EventBridge.java") || file.equals("AmqpEventTransport.java")) {
                return;                     // the only places it is meant to be
            }
            int sends = BROKER_SEND.matcher(body).results().mapToInt(m -> 1).sum();
            if (sends > 0) {
                actual.merge(module, sends, Integer::sum);
            }
        });

        List<String> regressions = new ArrayList<>();
        List<String> unlowered = new ArrayList<>();
        SEND_BUDGET.forEach((module, frozen) -> {
            int sends = actual.getOrDefault(module, 0);
            if (sends > frozen) {
                regressions.add("%s: %d broker send sites, budget %d".formatted(module, sends, frozen));
            } else if (sends < frozen) {
                unlowered.add("%s: down to %d from %d — lower the budget to lock the gain in"
                        .formatted(module, sends, frozen));
            }
        });

        assertThat(regressions)
                .as("""
                    A send outside an EventBridge cannot be checked for the two things that \
                    matter: that it is not inside a transaction, and that it goes to an exchange \
                    §4 names.""")
                .isEmpty();
        assertThat(unlowered).as("a ratchet that is not tightened stops ratcheting").isEmpty();
    }

    @Test
    @DisplayName("no broker send sits inside a @Transactional method")
    void nothingPublishesInsideATransaction() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachProductionSource((module, path, body) -> {
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (!BROKER_SEND.matcher(lines[i]).find()) {
                    continue;
                }
                // Walk back to the enclosing method signature; report only if @Transactional
                // sits between it and the class body.
                for (int j = i; j >= 0 && i - j < 80; j--) {
                    String line = lines[j];
                    if (line.matches("\\s{4}(public|private|protected).*\\(.*")) {
                        String annotations = j > 0 ? lines[j - 1] : "";
                        if (annotations.contains("@Transactional")
                                && !annotations.contains("@TransactionalEventListener")) {
                            violations.add("%s → %s:%d".formatted(
                                    module, path.getFileName(), i + 1));
                        }
                        break;
                    }
                }
            }
        });

        // No budget. This case is a straight contradiction of the outbox contract, and it has
        // zero instances — the cheapest moment to make sure it stays that way.
        assertThat(violations)
                .as("""
                    A message published from inside a transaction announces something that may \
                    never become true. The transaction can still roll back; the message cannot \
                    be recalled, and every consumer has already acted on it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no after-commit listener rethrows a delivery failure")
    void afterCommitListenersDoNotRethrow() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachProductionSource((module, path, body) -> {
            if (!body.contains("@TransactionalEventListener")) {
                return;
            }
            String[] lines = body.split("\n", -1);
            boolean insideListener = false;
            for (String line : lines) {
                if (line.contains("@TransactionalEventListener")) {
                    insideListener = true;
                    continue;
                }
                if (insideListener && line.matches("\\s{4}}\\s*")) {
                    insideListener = false;
                }
                if (insideListener && line.trim().startsWith("throw ")) {
                    violations.add("%s → %s: %s".formatted(module, path.getFileName(), line.trim()));
                }
            }
        });

        // No budget. Nothing retries a throw from an after-commit listener: Spring logs and discards
        // it, and the write is already durable. Work that must survive a failure belongs in the
        // outbox drain or a workflow, both of which retry.
        assertThat(violations)
                .as("""
                    An after-commit listener runs when the write is already durable, so throwing \
                    cannot undo it. It only reports a failure for work that succeeded — and the \
                    delivery problem it was actually raising is still unsolved.""")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface SourceVisitor {
        void visit(String module, Path path, String body) throws IOException;
    }

    private static void forEachProductionSource(SourceVisitor visitor) throws IOException {
        int scanned = 0;
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                        scanned++;
                        visitor.visit(module.getFileName().toString(), path, Files.readString(path));
                    }
                }
            }
        }
        assertThat(scanned).as("an empty sweep is not a passing lint").isGreaterThan(200);
    }

    @Test
    @DisplayName("each service publishes on the §4 exchange")
    void exchangesMatchTheSpecification() throws IOException {
        // An exchange nobody declared is refused by the broker; one that is declared but not the one §4
        // names is a message that reaches no subscription. Naming it in configuration is what makes the
        // difference between a delivered message and a silent one.
        Map<String, String> expected = Map.of(
                "catalog-service", "catalog-events",
                "booking-service", "booking-events",
                "identity-service", "identity-events");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Path config = BACKEND_ROOT.resolve(entry.getKey()).resolve("src/main/resources/application.yml");
            String body = Files.readString(config);
            if (!body.contains("exchange: " + entry.getValue())) {
                problems.add(entry.getKey() + " does not publish on " + entry.getValue());
            }
            if (!com.pml.shared.event.EventTopology.EXCHANGES.contains(entry.getValue())) {
                problems.add(entry.getValue() + " is not an exchange the platform topology declares");
            }
        }

        assertThat(problems)
                .as("ET-PLT-003 §4 names three exchanges and the one each service publishes on")
                .isEmpty();
    }

    /**
     * Parts of the outbox and event mechanism that no service references directly. It may only
     * <b>shrink</b>.
     *
     * <p>A name here is a covered class that nothing outside shared-library reaches. That is the shape
     * of defect worth guarding against — green tests, correct class, unreachable from a running
     * service — so a mechanism may only be listed when being unreferenced is the intended shape.</p>
     */
    private static final Set<String> NOT_YET_WIRED = new LinkedHashSet<>(List.of(
            // EventBridge is reached only from OutboxDrain, which lives in shared-library, and this
            // scan ignores shared-library because a class naming itself proves nothing. A service
            // referencing EventBridge directly would be publishing around the outbox, so this entry
            // records a correct shape rather than pending work.
            "EventBridge"));

    @Test
    @DisplayName("no shared event mechanism is orphaned beyond the ones already recorded")
    void theEventMechanismIsReachableFromAService() throws IOException {
        Set<String> mechanisms = new LinkedHashSet<>(List.of(
                "EventBridge", "Outbox", "ConsumerGuard", "ConsumerDispatch", "EventEnvelopes"));

        Set<String> referenced = new LinkedHashSet<>();
        forEachProductionSource((module, path, body) -> {
            if (module.equals("shared-library")) {
                return;                     // a class naming itself proves nothing
            }
            mechanisms.forEach(mechanism -> {
                if (body.contains(mechanism)) {
                    referenced.add(mechanism);
                }
            });
        });

        Set<String> orphaned = new TreeSet<>(mechanisms);
        orphaned.removeAll(referenced);

        Set<String> newlyOrphaned = new TreeSet<>(orphaned);
        newlyOrphaned.removeAll(NOT_YET_WIRED);

        assertThat(newlyOrphaned)
                .as("""
                    A mechanism no service constructs is tested and unreachable. Its unit tests \
                    build it directly, so they stay green while production keeps using whatever \
                    it was meant to replace — and the spec's gate rows read as satisfied.""")
                .isEmpty();

        Set<String> nowWired = new TreeSet<>(NOT_YET_WIRED);
        nowWired.removeAll(orphaned);

        assertThat(nowWired)
                .as("these are wired now — remove them from NOT_YET_WIRED so the gain is locked in")
                .isEmpty();
    }
}
