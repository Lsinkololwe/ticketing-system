package com.pml.shared.idempotency;

import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;

/**
 * The key was already used for a different request. Never retryable: repeating it cannot succeed,
 * and a client that treated it as transient would resend the very request the key exists to stop.
 */
public final class IdempotencyKeyReusedRefusal extends DomainRefusal {

    public IdempotencyKeyReusedRefusal(String scope) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotency key reused with a different request in scope " + scope);
    }
}
