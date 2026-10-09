package com.pml.catalog.security;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Set;

/** Facts about the signed-in caller that a field resolver needs and a role annotation cannot express. */
public final class Callers {

    private static final Set<String> PLATFORM_ADMINISTRATOR = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private Callers() {
    }

    /** Whether the caller holds a platform-wide administrator role; false when nobody is signed in. */
    public static Mono<Boolean> platformAdministrator() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication() != null && context.getAuthentication().getAuthorities().stream()
                        .anyMatch(authority -> PLATFORM_ADMINISTRATOR.contains(authority.getAuthority())))
                .defaultIfEmpty(false);
    }
}
