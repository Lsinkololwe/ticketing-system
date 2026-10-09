package com.pml.shared.infrastructure.temporal;

import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The one place a service's reactive code meets the Temporal client.
 *
 * <p>The Java SDK is thread-based: a start, signal or update is a blocking gRPC call. Every such
 * call is made here, on {@code boundedElastic}, and handed back as a {@code Mono}, so no resolver,
 * controller or consumer ever holds a {@link WorkflowClient} or blocks a Netty worker on one.
 * Registered by {@link TemporalGatewayAutoConfiguration} in every service that runs a worker.
 */
public class TemporalGateway {

    private final WorkflowClient client;

    public TemporalGateway(WorkflowClient client) {
        this.client = client;
    }

    /** A stub for starting a new execution under a business id, with its declared conflict policy. */
    public <W> W newWorkflow(Class<W> type, String workflowId, String taskQueue,
                             WorkflowIdConflictPolicy conflictPolicy) {
        return client.newWorkflowStub(type, WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowIdConflictPolicy(conflictPolicy)
                .build());
    }

    /**
     * A stub for a start that must happen at most once per id for the life of the namespace's
     * retention: with {@code REJECT_DUPLICATE} a closed execution under the same id refuses the start.
     */
    public <W> W newWorkflow(Class<W> type, String workflowId, String taskQueue,
                             WorkflowIdConflictPolicy conflictPolicy, WorkflowIdReusePolicy reusePolicy) {
        return client.newWorkflowStub(type, WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowIdConflictPolicy(conflictPolicy)
                .setWorkflowIdReusePolicy(reusePolicy)
                .build());
    }

    /**
     * As the conflict-policy start, tagging the execution with search attributes at start. The
     * attributes must be registered on the namespace (see {@link ProcessSearchAttributes}).
     */
    public <W> W newWorkflow(Class<W> type, String workflowId, String taskQueue,
                             WorkflowIdConflictPolicy conflictPolicy, ProcessSearchAttributes attributes) {
        return client.newWorkflowStub(type, withAttributes(WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowIdConflictPolicy(conflictPolicy), attributes).build());
    }

    /** As the reuse-policy start, tagging the execution with search attributes at start. */
    public <W> W newWorkflow(Class<W> type, String workflowId, String taskQueue,
                             WorkflowIdConflictPolicy conflictPolicy, WorkflowIdReusePolicy reusePolicy,
                             ProcessSearchAttributes attributes) {
        return client.newWorkflowStub(type, withAttributes(WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowIdConflictPolicy(conflictPolicy)
                .setWorkflowIdReusePolicy(reusePolicy), attributes).build());
    }

    /** As {@link #signalWithStartWorkflow(Class, String, String)}, tagging a newly started execution. */
    public <W> W signalWithStartWorkflow(Class<W> type, String workflowId, String taskQueue,
                                         ProcessSearchAttributes attributes) {
        return client.newWorkflowStub(type, withAttributes(WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue), attributes).build());
    }

    private static WorkflowOptions.Builder withAttributes(WorkflowOptions.Builder options,
                                                          ProcessSearchAttributes attributes) {
        return attributes == null ? options : options.setTypedSearchAttributes(attributes.toSearchAttributes());
    }

    /** A stub for signal-with-start, which reaches a running execution or starts one. */
    public <W> W signalWithStartWorkflow(Class<W> type, String workflowId, String taskQueue) {
        return client.newWorkflowStub(type, WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .build());
    }

    /** A stub bound to an execution that already exists, for signals, updates and queries. */
    public <W> W existingWorkflow(Class<W> type, String workflowId) {
        return client.newWorkflowStub(type, workflowId);
    }

    /** Runs a blocking client call off the event loop. */
    public <T> Mono<T> call(Callable<T> blocking) {
        return Mono.fromCallable(blocking).subscribeOn(Schedulers.boundedElastic());
    }

    /** Runs a blocking client call that returns nothing. */
    public Mono<Void> run(Runnable blocking) {
        return Mono.<Void>fromRunnable(blocking).subscribeOn(Schedulers.boundedElastic());
    }

    /** Awaits an asynchronous client result without holding a thread. */
    public <T> Mono<T> await(Supplier<CompletableFuture<T>> future) {
        return Mono.defer(() -> Mono.fromFuture(future.get())).subscribeOn(Schedulers.boundedElastic());
    }

    /** The client itself, for Schedule management at boot — never for a request path. */
    public WorkflowClient client() {
        return client;
    }
}
