package com.pml.shared.security.tenancy;

import reactor.core.publisher.Mono;

/**
 * Where a platform-wide reach is recorded.
 *
 * <p>{@link LogOnlyPlatformWideAuditSink} is the default and is enough to answer "who read across
 * tenants, and why" from the log pipeline. A service that wants a queryable row defines its own
 * bean of this type and the default backs off.
 *
 * <p>A sink that fails does not block the reach: {@link PlatformWideAccess} reports the failure
 * and carries on, because an administrator locked out by the audit store's outage is also locked
 * out of the console used to diagnose it. A sink that must be exact should be exactly that —
 * a local write, not a remote call.
 */
@FunctionalInterface
public interface PlatformWideAuditSink {

    Mono<Void> record(PlatformWideAuditRecord record);
}
