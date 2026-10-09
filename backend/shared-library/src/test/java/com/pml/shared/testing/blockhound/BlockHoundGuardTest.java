package com.pml.shared.testing.blockhound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The runtime half of the reactive contract.
 *
 * <p>A grep finds the blocking calls we thought to look for. BlockHound finds the ones three
 * frames down inside a driver or a parser, and it draws the line where the harm actually is:
 * on a thread Reactor has marked non-blocking. Blocking the main thread at boot is fine;
 * blocking a Netty worker stalls every concurrent request that worker is carrying, which at
 * on-sale peak is thousands.
 *
 * <p>The rule is "never block a non-blocking thread", not "never block", and BlockHound
 * enforces exactly that without anyone having to maintain a list of blessed call sites.
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R1 · blocking calls are caught on non-blocking threads")
class BlockHoundGuardTest {

    @BeforeAll
    static void installBlockHound() {
        // Idempotent. On JDK 13+ this needs -XX:+AllowRedefinitionToAddDeleteMethods, which
        // surefire supplies; BlockHound self-checks its own instrumentation and throws here
        // if the flag is missing, rather than silently detecting nothing.
        BlockHound.install();
    }

    @Test
    @DisplayName("instrumentation succeeded — the JDK 13+ redefinition flag reached the forked JVM")
    void isActuallyInstalled() {
        assertThatCode(BlockHound::install)
                .as("""
                    BlockHound verifies instrumentation by running a probe that must be \
                    detected. If this throws, the surefire argLine lost \
                    -XX:+AllowRedefinitionToAddDeleteMethods and every other assertion in \
                    this class would pass while detecting nothing.""")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a blocking call on a non-blocking scheduler is an error")
    void blockingOnParallelIsRefused() {
        Throwable caught = catchThrowable(() ->
                Mono.fromCallable(() -> {
                            Thread.sleep(10);           // the thing that must never happen
                            return "done";
                        })
                        .subscribeOn(Schedulers.parallel())
                        .block(Duration.ofSeconds(5)));

        assertThat(isBlockingOperationError(caught))
                .as("Schedulers.parallel() threads are marked non-blocking; this is the harm R1 names")
                .isTrue();
    }

    @Test
    @DisplayName("the same call on boundedElastic is allowed — that is the escape hatch, and it must stay open")
    void blockingOnBoundedElasticIsAllowed() {
        String result = Mono.fromCallable(() -> {
                    Thread.sleep(10);
                    return "done";
                })
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(5));

        assertThat(result)
                .as("""
                    CONVENTIONS §1: a genuinely blocking third-party SDK is wrapped once, in \
                    infrastructure/, on boundedElastic. If BlockHound refused this too, there \
                    would be nowhere legitimate to put such a call and teams would disable it.""")
                .isEqualTo("done");
    }

    /**
     * {@code block()} inside {@code Mono.fromCallable} looks safe while the inner source is
     * synchronous: Reactor resolves a callable source without parking. The moment the inner
     * {@code Mono} does any I/O — a repository read, a remote call — {@code block()} starts
     * parking and every request through that path stalls a Netty worker, and nothing about the
     * change looks dangerous in review.
     *
     * <p>So this asserts the hazard directly: the same shape, with an asynchronous inner source,
     * is refused. Compose with {@code concatMap} instead.
     */
    @Test
    @DisplayName("block() inside fromCallable is refused the instant the inner Mono does I/O")
    void theSameShapeStallsAsSoonAsTheInnerMonoIsAsynchronous() {
        Mono<String> asynchronousInner = Mono.just("value").delayElement(Duration.ofMillis(5));

        Throwable caught = catchThrowable(() ->
                Mono.fromCallable(() -> asynchronousInner.block())   // the batch loop's shape
                        .subscribeOn(Schedulers.parallel())
                        .block(Duration.ofSeconds(5)));

        assertThat(caught)
                .as("""
                    the shape is only safe while the inner Mono is synchronous — an accident \
                    of the implementation, not a property of the design.""")
                .isNotNull();

        // Reactor guards block() on a NonBlocking thread itself, before any park happens,
        // so this surfaces as Reactor's IllegalStateException rather than as a
        // BlockingOperationError. Two mechanisms, one line: BlockHound catches what Reactor
        // cannot see — a blocking call inside a driver or a parser, several frames down.
        assertThat(isBlockingOperationError(caught) || mentionsReactorsNonBlockingGuard(caught))
                .as("refused by BlockHound or by Reactor's own non-blocking guard, but refused: %s", caught)
                .isTrue();
    }

    // --------------------------------------------------------------------- helpers

    /** Reactor wraps errors as it propagates them, so walk the chain rather than the top frame. */
    private static boolean isBlockingOperationError(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof BlockingOperationError) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Reactor's own guard: "block()/blockFirst()/blockLast() are blocking, which is not supported in thread ...". */
    private static boolean mentionsReactorsNonBlockingGuard(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            String message = String.valueOf(t.getMessage());
            if (message.contains("block()") && message.contains("not supported in thread")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Minimal shape the service's reflection accepts: an organizationId getter. */
    public static final class Document {
        private final String organizationId;

        Document(String organizationId) {
            this.organizationId = organizationId;
        }

        public String getOrganizationId() {
            return organizationId;
        }
    }
}
