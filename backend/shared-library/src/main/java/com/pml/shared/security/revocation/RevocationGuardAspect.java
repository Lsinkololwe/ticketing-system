package com.pml.shared.security.revocation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Mono;

/**
 * Runs {@link SensitiveOperationGuard} in front of every {@link FailClosedOnRevocation} method.
 *
 * <h2>Ordering</h2>
 * <p>{@link Ordered#LOWEST_PRECEDENCE} makes this the innermost advice, so it runs after Spring
 * Security's {@code @PreAuthorize} interceptor. Authorization failures therefore surface as 403
 * rather than being masked by a revocation 503, and no revocation check is spent on a call that
 * is going to be rejected anyway.</p>
 *
 * <h2>Reactive assembly</h2>
 * <p>{@code proceed()} is wrapped in {@link Mono#defer} so the guarded method is assembled and
 * invoked only after the guard's {@code Mono<Void>} completes.</p>
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class RevocationGuardAspect {

    private final SensitiveOperationGuard guard;

    @Around("@annotation(annotation)")
    public Object enforce(ProceedingJoinPoint joinPoint, FailClosedOnRevocation annotation)
            throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (!Mono.class.isAssignableFrom(signature.getReturnType())) {
            // Fail at the first call rather than skipping silently, so an annotation that
            // cannot be honoured is caught immediately.
            throw new IllegalStateException(
                    "@FailClosedOnRevocation requires a Mono-returning method, but "
                            + signature.getDeclaringTypeName() + "#" + signature.getName()
                            + " returns " + signature.getReturnType().getName());
        }

        String operation = annotation.value().isBlank()
                ? signature.getName()
                : annotation.value();

        return guard.assertNotRevoked(operation)
                .then(Mono.defer(() -> proceed(joinPoint)));
    }

    @SuppressWarnings("unchecked")
    private Mono<Object> proceed(ProceedingJoinPoint joinPoint) {
        try {
            return (Mono<Object>) joinPoint.proceed();
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }
    }
}
