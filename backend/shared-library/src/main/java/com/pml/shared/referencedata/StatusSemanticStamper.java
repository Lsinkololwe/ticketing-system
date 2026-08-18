package com.pml.shared.referencedata;

import com.pml.shared.constants.WorkflowSemantic;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Keeps {@code statusSemantic} true on every write, not just the first one.
 *
 * <h2>The bug this exists to prevent</h2>
 * Stamping the semantic where a row is created looks sufficient and is not. A
 * payout created as {@code PENDING} and later moved to {@code COMPLETED} keeps
 * the semantic it was born with, so "payouts still in flight" counts money that
 * has already left the building. The wrong answer is the confident one — the row
 * has a semantic, it is just stale.
 *
 * <p>Status changes happen inside domain methods on the models themselves, which
 * cannot make a database call to re-derive meaning. Rather than have every call
 * site remember to restamp — and it only takes one that forgets — this runs on
 * the single path all of them share: the write.
 *
 * <h2>Unresolvable clears rather than keeps</h2>
 * When the status code matches no reference data the field is set to null. That
 * looks destructive and is the safe direction: the status just changed, so any
 * value already there describes the PREVIOUS status and is now positively wrong.
 * Null excludes the row from every semantic query and is findable; a confidently
 * wrong value is neither.
 *
 * @see StatusSemanticResolver
 */
@Slf4j
public abstract class StatusSemanticStamper<T> {

    private final StatusSemanticResolver resolver;
    private final String referenceType;
    private final Function<T, String> statusCode;
    private final BiConsumer<T, WorkflowSemantic> setter;
    private final Function<T, String> describe;

    protected StatusSemanticStamper(StatusSemanticResolver resolver,
                                    String referenceType,
                                    Function<T, String> statusCode,
                                    BiConsumer<T, WorkflowSemantic> setter,
                                    Function<T, String> describe) {
        this.resolver = resolver;
        this.referenceType = referenceType;
        this.statusCode = statusCode;
        this.setter = setter;
        this.describe = describe;
    }

    protected final Mono<T> stamp(T entity) {
        String code = statusCode.apply(entity);
        if (code == null) {
            return Mono.just(entity);
        }
        return resolver.resolve(referenceType, code)
                .doOnNext(semantic -> setter.accept(entity, semantic))
                .hasElement()
                .doOnNext(resolved -> {
                    if (!resolved) {
                        log.warn("{} {} has status {} with no {} reference data — semantic cleared",
                                entity.getClass().getSimpleName(), describe.apply(entity), code, referenceType);
                        setter.accept(entity, null);
                    }
                })
                .thenReturn(entity);
    }

}
