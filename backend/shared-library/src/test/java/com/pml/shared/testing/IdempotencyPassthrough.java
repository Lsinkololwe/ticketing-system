package com.pml.shared.testing;

import com.pml.shared.idempotency.IdempotencyGuard;
import java.util.function.Supplier;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Mono;

/**
 * An {@link IdempotencyGuard} for unit tests whose subject is something else entirely: it runs
 * the supplied operation exactly once and returns its result, with no claim, no replay and no
 * fingerprint check. A test that is genuinely exercising idempotency (replay, a reused key,
 * concurrent claims) needs the real guard against real Redis and Mongo — see
 * {@code IdempotencyGuardTest} for that fixture — not this one.
 */
public final class IdempotencyPassthrough {

    private IdempotencyPassthrough() {
    }

    @SuppressWarnings("unchecked")
    public static IdempotencyGuard guard() {
        IdempotencyGuard guard = Mockito.mock(IdempotencyGuard.class);
        Answer<Mono<?>> runTheOperation = invocation -> {
            Supplier<Mono<?>> operation = invocation.getArgument(4, Supplier.class);
            return operation.get();
        };
        Mockito.when(guard.execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .then(runTheOperation);
        return guard;
    }
}
