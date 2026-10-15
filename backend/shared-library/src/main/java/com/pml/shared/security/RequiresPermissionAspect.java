package com.pml.shared.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;
import com.pml.shared.security.tenancy.PlatformWideAuditSink;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.util.Set;

/**
 * Enforces {@link RequiresPermission}: the caller's platform roles, read from the authorities the
 * token was converted into, must grant the permission before the method is assembled or invoked.
 *
 * <p>It is the innermost advice, so Spring Security's role gate answers first and an anonymous
 * caller is refused as unauthenticated rather than as forbidden. An absent authentication, a role
 * the model does not know, and an annotation on a method that cannot be guarded all refuse.
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequiresPermissionAspect {

    private final PlatformWideAuditSink audit;
    private final String service;
    private final Clock clock;

    /** An aspect that enforces and records nothing: for tests that only exercise the gate. */
    public RequiresPermissionAspect() {
        this(null, "unknown", Clock.systemUTC());
    }

    /**
     * @param audit   where a platform role's use of a permission is recorded; {@code null} records nothing
     * @param service the application name written into each record
     */
    public RequiresPermissionAspect(PlatformWideAuditSink audit, String service, Clock clock) {
        this.audit = audit;
        this.service = service;
        this.clock = clock;
    }

    // The annotation is read reflectively rather than bound as an advice argument, for the same
    // reason the revocation aspect does: binding fails when the method-security chain also advises.
    @Around("@annotation(com.pml.shared.security.RequiresPermission)")
    public Object enforce(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequiresPermission required = AnnotationUtils.findAnnotation(signature.getMethod(), RequiresPermission.class);
        Class<?> returned = signature.getReturnType();
        if (required == null || !(Mono.class.isAssignableFrom(returned) || Flux.class.isAssignableFrom(returned))) {
            throw new IllegalStateException("@RequiresPermission needs a Mono or Flux method, but "
                    + signature.getDeclaringTypeName() + "#" + signature.getName() + " returns " + returned.getName());
        }
        return guarded(joinPoint, returned, check(required.value(), required.orInternalService())
                .then(recordPlatformAllow(required.value())));
    }

    /**
     * Enforces {@link RequiresTenantPermission}: the caller holds the permission on the platform, or
     * acts for at least one organization. The service then checks the permission against the
     * organization or event the call names.
     */
    @Around("@annotation(com.pml.shared.security.RequiresTenantPermission)")
    public Object enforceTenant(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequiresTenantPermission required =
                AnnotationUtils.findAnnotation(signature.getMethod(), RequiresTenantPermission.class);
        Class<?> returned = signature.getReturnType();
        if (required == null || !(Mono.class.isAssignableFrom(returned) || Flux.class.isAssignableFrom(returned))) {
            throw new IllegalStateException("@RequiresTenantPermission needs a Mono or Flux method, but "
                    + signature.getDeclaringTypeName() + "#" + signature.getName() + " returns " + returned.getName());
        }
        return guarded(joinPoint, returned, checkTenant(required.value()).then(recordPlatformAllow(required.value())));
    }

    /**
     * Records that a platform role's grant, and not a membership or an internal service, satisfied a
     * permission that changes something. The record names the permission and never an argument; a sink that fails does not
     * block the operation, as for every other platform-wide reach.
     */
    private Mono<Void> recordPlatformAllow(Permission permission) {
        // A view is polled by dashboards and carries its own reasons where a read is sensitive; the trail
        // records what a platform role changed, not every time it looked.
        if (audit == null || permission.action() == Permission.Action.VIEW) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .flatMap(authentication -> {
                    String role = platformRoleGranting(authentication, permission);
                    if (role == null) {
                        return Mono.<Void>empty();
                    }
                    return audit.record(new PlatformWideAuditRecord(authentication.getName(), role, service,
                            PlatformWideAccess.Reason.PLATFORM_PERMISSION_EXERCISED, permission.code(),
                            clock.instant()));
                })
                .onErrorResume(error -> Mono.empty());
    }

    private static String platformRoleGranting(Authentication authentication, Permission permission) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        return PlatformRole.heldByAuthority(authorities).stream()
                .filter(role -> role.grants().contains(permission))
                .map(PlatformRole::authority).sorted().findFirst().orElse(null);
    }

    private Object guarded(ProceedingJoinPoint joinPoint, Class<?> returned, Mono<Void> check) {
        if (Flux.class.isAssignableFrom(returned)) {
            return check.thenMany(Flux.defer(() -> this.<Object>proceedMany(joinPoint)));
        }
        return check.then(Mono.defer(() -> this.<Object>proceedOne(joinPoint)));
    }

    /** Completes when the caller's platform roles grant {@code permission}; otherwise refuses. */
    static Mono<Void> check(Permission permission) {
        return check(permission, false);
    }

    static Mono<Void> check(Permission permission, boolean orInternalService) {
        return authorities().flatMap(authorities -> {
            boolean granted = grants(authorities, permission)
                    || (orInternalService && authorities.stream().anyMatch(Authorities.INTERNAL_CALLER::contains));
            return granted
                    ? Mono.<Void>empty()
                    : Mono.error(new AccessDeniedException("requires " + permission.code()));
        });
    }

    /**
     * Completes for a caller who holds {@code permission} on the platform, or who acts for at least one
     * organization; refuses everyone else. The organization-level decision is the service's.
     */
    static Mono<Void> checkTenant(Permission permission) {
        return authorities().flatMap(authorities -> {
            if (grants(authorities, permission)) {
                return Mono.<Void>empty();
            }
            return CurrentTenantScope.get()
                    .map(scope -> !scope.organizationIds().isEmpty() || scope.platformAdmin())
                    .defaultIfEmpty(false)
                    .flatMap(admitted -> admitted
                            ? Mono.<Void>empty()
                            : Mono.<Void>error(new AccessDeniedException(
                                    "requires " + permission.code() + " in an organization")));
        });
    }

    private static Mono<Set<String>> authorities() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .switchIfEmpty(Mono.error(() -> new AuthenticationCredentialsNotFoundException("No authentication")))
                .map(authentication -> authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet()));
    }

    private static boolean grants(Set<String> authorities, Permission permission) {
        return PlatformRole.heldByAuthority(authorities).stream().anyMatch(role -> role.grants().contains(permission));
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> proceedOne(ProceedingJoinPoint joinPoint) {
        try {
            return (Mono<T>) joinPoint.proceed();
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Flux<T> proceedMany(ProceedingJoinPoint joinPoint) {
        try {
            return (Flux<T>) joinPoint.proceed();
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }
}
