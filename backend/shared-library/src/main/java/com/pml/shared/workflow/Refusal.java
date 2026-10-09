package com.pml.shared.workflow;

import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ApplicationFailure;

/**
 * A refusal a workflow's rules decide on, before it becomes the failure the caller receives.
 *
 * <p>Rules return this rather than throwing, so a layer-1 test asserts the code without a worker,
 * and a validator and its handler raise the identical failure.
 */
public record Refusal(ErrorCode code, String message) {

    public ApplicationFailure failure() {
        return Refusals.refusal(code, message);
    }
}
