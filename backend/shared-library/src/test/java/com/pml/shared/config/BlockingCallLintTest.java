package com.pml.shared.config;

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
 * ET-PLT-001 R1 — the static half of the reactive contract, across every module.
 *
 * <h2>The rule, as narrowed and approved on 2026-08-18</h2>
 * A blocking call is permitted in exactly two constructs and nowhere else:
 *
 * <ul>
 *   <li>a class implementing {@code ApplicationRunner} or {@code CommandLineRunner}</li>
 *   <li>a class with an {@code @EventListener(ApplicationReadyEvent.class)} method</li>
 * </ul>
 *
 * <p>Both run on the main thread at boot, so neither stalls a Netty worker — which is the harm
 * R1 exists to prevent. Reconciliation found seven blocking calls and six were already in these
 * constructs; a flat ban would have traded Spring Boot's native startup mechanism for a worse
 * one and lost the ordering the seeders depend on.
 *
 * <h2>This lint is coarse, and that is why BlockHound exists</h2>
 * The allowance is granted per <em>file</em>, not per method: a class that both listens for
 * {@code ApplicationReadyEvent} and exposes a request-path method could hide a blocking call in
 * the second and pass here. Method-level scoping by regex would be more precise and much more
 * brittle, and it would still miss the case that matters most — a blocking call three frames
 * down inside a driver, which no source scan can see.
 *
 * <p>So the guarantee is the pair, not either half. This catches the explicit call anywhere in
 * the tree, cheaply, on every build; {@code BlockHoundGuardTest} catches what actually reaches
 * an event-loop thread at runtime, including code we did not write. Neither alone is R1.
 *
 * <p>Fire-and-forget {@code .subscribe()} is a separate defect with a separate triage
 * (ET-PLT-001 B3) and is deliberately not checked here.
 */
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R1 · blocking calls appear only in boot-time constructs")
class BlockingCallLintTest {

    /** Surefire runs with the module directory as CWD, so this reaches the reactor root. */
    private static final Path BACKEND_ROOT = Path.of("..");

    private static final Pattern BLOCKING_CALL = Pattern.compile(
            "\\.block\\s*\\(|\\.blockFirst\\s*\\(|\\.blockLast\\s*\\(|\\.toFuture\\s*\\(\\s*\\)\\s*\\.get\\s*\\(");

    /**
     * Pre-existing violations, frozen so the lint can be introduced without a flag day.
     *
     * <p>This is a ratchet, not an exemption list: new violations fail immediately, and this
     * set may only shrink. Each entry is real debt with a named owner, discovered by this lint
     * after manual reconciliation missed all six — the R0 grep matched {@code .block()} with
     * literal empty parentheses and every one of these is {@code .block(TIMEOUT)}.
     *
     * <p><b>High — the committing thread.</b> {@code @TransactionalEventListener(AFTER_COMMIT)}
     * runs synchronously on whichever thread committed the transaction. On a request path that
     * is a Netty worker, so these four are live stalls, not latent ones. Owned by
     * <b>ET-PLT-003</b>, which moves after-commit work onto the outbox drain.
     *
     * <p><b>Medium — the scheduler pool.</b> {@code @Scheduled} runs on Spring's
     * {@code TaskScheduler}, which defaults to a single thread: blocking there delays every
     * other scheduled job, including the reservation expiry sweep that inventory conservation
     * depends on. Owned by <b>ET-TKT-001</b> B5 and <b>ET-ADM-003</b>.
     */
    private static final Set<String> KNOWN_OFFENDERS = Set.of(
            // high — after-commit listeners on the committing thread
            "booking-service/src/main/java/com/pml/booking/event/listener/PaymentEventListener.java",
            "booking-service/src/main/java/com/pml/booking/event/listener/ChargebackEventListener.java",
            // medium — scheduled sweeps on the TaskScheduler pool
            "booking-service/src/main/java/com/pml/booking/scheduler/ReservationExpirationScheduler.java",
            "booking-service/src/main/java/com/pml/booking/scheduler/PurchaseRecoveryScheduler.java");

    /** The two constructs the narrowed rule permits. Both execute on the main thread at boot. */
    private static final Pattern BOOT_TIME_CONSTRUCT = Pattern.compile(
            "implements\\s+[^{]*\\b(ApplicationRunner|CommandLineRunner)\\b"
                    + "|@EventListener\\s*\\(\\s*ApplicationReadyEvent\\.class\\s*\\)");

    @Test
    @DisplayName("every blocking call in production source sits in an ApplicationRunner or an ApplicationReadyEvent listener")
    void blockingCallsOnlyAtBoot() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (Path sourceRoot : productionSourceRoots()) {
            try (Stream<Path> sources = Files.walk(sourceRoot)) {
                for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    scanned++;
                    String body = Files.readString(path);
                    if (BOOT_TIME_CONSTRUCT.matcher(body).find()) {
                        continue;               // the whole file is boot-time work
                    }
                    collectBlockingLines(path, body, offenders);
                }
            }
        }

        assertThat(scanned)
                .as("an empty sweep is not a passing lint — the source layout has moved")
                .isGreaterThan(200);

        List<String> unexpected = offenders.stream()
                .filter(o -> KNOWN_OFFENDERS.stream().noneMatch(o::startsWith))
                .toList();

        List<String> alreadyFixed = KNOWN_OFFENDERS.stream()
                .filter(known -> offenders.stream().noneMatch(o -> o.startsWith(known)))
                .toList();

        assertThat(alreadyFixed)
                .as("""
                    These known offenders no longer block — remove them from KNOWN_OFFENDERS so \
                    the ratchet tightens. A stale entry silently re-permits a file that had \
                    been cleaned up.""")
                .isEmpty();

        assertThat(unexpected)
                .as("""
                    A blocking call outside a boot-time construct will, sooner or later, run on \
                    a Netty worker — and one stalled worker stalls every concurrent request it \
                    is carrying, which at on-sale peak is thousands.

                    Compose instead: flatMap or concatMap, never block. A genuinely blocking \
                    third-party SDK is wrapped once, in infrastructure/, on \
                    Schedulers.boundedElastic() (CONVENTIONS §1) — containing it to one adapter \
                    class is the point.

                    If the call really is boot-time work, put it in an ApplicationRunner or an \
                    @EventListener(ApplicationReadyEvent.class) method, which is where Spring \
                    Boot expects it and where it costs nothing.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the ratchet only tightens — the known-offender list may shrink, never grow")
    void theRatchetOnlyTightens() {
        assertThat(KNOWN_OFFENDERS)
                .as("""
                    Six pre-existing blocking calls across four files were frozen on 2026-08-18 \
                    so this lint could be introduced without a flag day. Raising this number \
                    means new debt was admitted rather than paid down — if that is genuinely \
                    intended, change the literal deliberately and say why.""")
                .hasSizeLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("the lint can actually see a violation — proven against a synthetic offender")
    void theLintHasTeeth() {
        // A file body with a blocking call and no boot-time construct: the exact shape the
        // sweep must reject. Asserting the matchers directly, because a lint that has never
        // been shown to fire is an assumption.
        String offending = """
                @Service
                public class SomeService {
                    public String read() { return repository.findById("x").block(); }
                }
                """;
        assertThat(BOOT_TIME_CONSTRUCT.matcher(offending).find()).isFalse();
        assertThat(BLOCKING_CALL.matcher(offending).find()).isTrue();

        String allowed = """
                @Component
                public class SomeSeeder implements ApplicationRunner {
                    public void run(ApplicationArguments args) { seed().block(); }
                }
                """;
        assertThat(BOOT_TIME_CONSTRUCT.matcher(allowed).find()).isTrue();
    }

    // --------------------------------------------------------------------- helpers

    private static List<Path> productionSourceRoots() throws IOException {
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            return modules
                    .map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .toList();
        }
    }

    /**
     * Skips comment lines so a {@code .block()} mentioned in Javadoc — of which this codebase
     * has several, explaining why not to — is not reported as a call.
     */
    private static void collectBlockingLines(Path path, String body, List<String> offenders) {
        String[] lines = body.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                continue;
            }
            if (BLOCKING_CALL.matcher(trimmed).find()) {
                offenders.add("%s:%d → %s".formatted(
                        BACKEND_ROOT.relativize(path), i + 1, trimmed));
            }
        }
    }
}
