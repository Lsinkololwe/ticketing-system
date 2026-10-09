package com.pml.shared.security.tenancy;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Reads the {@link TenantScope} for the request in flight.
 *
 * <h2>Why the context holds a {@code Mono}, not a value</h2>
 * Resolving tenancy costs a call to identity-service, and a single request may
 * consult it several times — a resolver, the service beneath it, and an
 * authorization check on the way out. {@link TenantScopeWebFilter} puts a
 * {@code cache()}d {@code Mono} in the Reactor context, so the lookup happens at
 * most once per request and every later subscriber gets the same answer, while a
 * request that never touches tenancy pays nothing.
 *
 * <h2>Missing means error, never allow</h2>
 * If the filter is not installed, {@link #get()} fails. The alternative — treating
 * an absent scope as an empty one — turns a wiring mistake into a silent
 * platform-wide outage for organizers, and the symmetric mistake (treating it as
 * permissive) turns it into a silent platform-wide breach. An error is the only
 * answer that cannot be mistaken for working software.
 */
public final class CurrentTenantScope {

    /** Context key. The value stored under it is a {@code Mono<TenantScope>}, already cached. */
    static final String KEY = CurrentTenantScope.class.getName();

    private CurrentTenantScope() {
    }

    /**
     * The scope for this request.
     *
     * @return the resolved scope, or an error if no scope was seeded into the context
     */
    @SuppressWarnings("unchecked")
    public static Mono<TenantScope> get() {
        return Mono.deferContextual(ctx -> {
            if (!ctx.hasKey(KEY)) {
                return Mono.error(new IllegalStateException("""
                        No TenantScope in the Reactor context. TenantScopeWebFilter is not \
                        installed, or this call runs outside a request. Refusing rather than \
                        guessing: an absent scope is a wiring fault, and both ways of guessing \
                        at it are wrong in production."""));
            }
            return (Mono<TenantScope>) ctx.get(KEY);
        });
    }

    /** Seeds a resolved scope. Used by the filter, and by tests standing in for it. */
    public static Context seed(Context context, Mono<TenantScope> scope) {
        return context.put(KEY, scope.cache());
    }
}
