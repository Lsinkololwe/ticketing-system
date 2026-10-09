package com.pml.shared.idempotency;

import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;

/** The first submission of this key is still running past the wait window; a later retry will see its result. */
public final class IdempotentRequestInFlightRefusal extends DomainRefusal {

    public IdempotentRequestInFlightRefusal(String scope) {
        super(ErrorCode.RESOURCE_CONFLICT, "first submission of an idempotency key still running in scope " + scope);
    }
}
