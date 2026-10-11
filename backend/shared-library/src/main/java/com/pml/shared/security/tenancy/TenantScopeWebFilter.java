package com.pml.shared.security.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;
/**
 * Seeds one {@link TenantScope} per request, resolved lazily and at most once.
 *
 * <h2>Lazy on purpose</h2>
 * Most requests never touch an organization-scoped resource — discovery, health,
 * the whole public catalogue. Resolving tenancy eagerly would add a call to
 * identity-service to every one of them. The filter therefore stores an
 * unsubscribed {@code Mono}; the cost is paid by the first thing that actually
 * asks, and never at all by requests that do not.
 *
 * <h2>Failure lands on deny, and says so</h2>
 * An unauthenticated request gets {@link TenantScope#denyAll}. A membership
 * lookup that <em>fails</em> is a different thing and is allowed to propagate, so
 * the error contract can answer it as retryable rather than as "your event does
 * not exist" — see {@link TenantMemberships}. Neither path can widen access.
 *
 * <h2>Ordering</h2>
 * Runs after Spring Security's authentication filters, since it reads the
 * principal they establish.
 */
public class TenantScopeWebFilter implements WebFilter, Ordered {

    /** After Spring Security's chain, which authenticates the principal this reads. */
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private static final Logger log = LoggerFactory.getLogger(TenantScopeWebFilter.class);

    private final TenantMemberships memberships;
    private final Set<String> platformAdminAuthorities;
    private final PlatformWideAccess platformWideAccess;

    public TenantScopeWebFilter(TenantMemberships memberships) {
        this(memberships, Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN"));
    }

    public TenantScopeWebFilter(TenantMemberships memberships, Set<String> platformAdminAuthorities) {
        this(memberships, platformAdminAuthorities, null);
    }

    /**
     * @param platformWideAccess the recorder platform-wide reaches in this request are written
     *                           through; {@code null} leaves the log-only fallback in place
     */
    public TenantScopeWebFilter(TenantMemberships memberships, Set<String> platformAdminAuthorities,
                                PlatformWideAccess platformWideAccess) {
        this.memberships = memberships;
        this.platformAdminAuthorities = Set.copyOf(platformAdminAuthorities);
        this.platformWideAccess = platformWideAccess;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<TenantScope> scope = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .flatMap(this::resolve)
                // No security context at all: anonymous. Permits nothing, and costs no lookup.
                .switchIfEmpty(Mono.fromSupplier(() -> TenantScope.denyAll(null)));

        return chain.filter(exchange)
                .contextWrite(ctx -> PlatformWideAccess.seed(CurrentTenantScope.seed(ctx, scope), platformWideAccess));
    }

    private Mono<TenantScope> resolve(Authentication authentication) {
        String subject = authentication.getName();
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(platformAdminAuthorities::contains);

        return memberships.activeOrganizationIdsOf(subject)
                .defaultIfEmpty(Set.of())
                .map(orgs -> admin
                        ? TenantScope.platformAdministrator(subject, orgs)
                        : TenantScope.of(subject, orgs))
                .doOnNext(resolved -> log.debug("Tenant scope resolved: {}", resolved))
                .onErrorResume(failure -> {
                    // A platform administrator's reach does not depend on membership data,
                    // so an identity-service outage must not lock them out of the console
                    // they would use to diagnose it. Everyone else gets the error, which
                    // denies and stays honest about why.
                    if (admin) {
                        log.warn("Membership lookup failed for administrator {}; "
                                + "continuing with platform-wide scope", subject, failure);
                        return Mono.just(TenantScope.platformAdministrator(subject, Set.of()));
                    }
                    return Mono.error(failure);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
