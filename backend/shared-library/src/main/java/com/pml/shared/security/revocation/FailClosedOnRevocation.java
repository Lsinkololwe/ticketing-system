package com.pml.shared.security.revocation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation as too sensitive to run on a token whose revocation state is unknown.
 *
 * <p>The annotated method must return a {@link reactor.core.publisher.Mono}. Before it is
 * invoked, {@link RevocationGuardAspect} resolves the caller's token against the revocation
 * store; a revoked token yields 401 and an unresolvable one yields 503. Every request is already
 * checked by {@link RevocationRequestGuard}, which lets a request through when the store cannot
 * answer; this annotation is what refuses it instead.</p>
 *
 * <p>On a class, it covers every {@code @DgsMutation} the class declares — for a resolver whose
 * every mutation moves money or changes who holds it.</p>
 *
 * <p>Apply it where an operation grants money-adjacent capability or changes who holds it. On
 * the organizer path that means the whole application lifecycle: submitting an application,
 * and any admin decision on one.</p>
 *
 * @see SensitiveOperationGuard
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FailClosedOnRevocation {

    /**
     * Operation label used in logs and as the {@code operation} tag on
     * {@code identity_revocation_denials_total}. Defaults to the method name.
     */
    String value() default "";
}
