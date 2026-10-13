package com.pml.shared.security.tenancy;

/**
 * Starts the durable delivery of one outbox row to identity.
 *
 * <p>The call is blocking (it reaches Temporal over gRPC) and is made off the request's thread. A
 * second start for a row whose delivery is already running attaches to it, so starting is always
 * safe to repeat.
 */
@FunctionalInterface
public interface PlatformWideAuditDelivery {

    void start(String recordId);
}
