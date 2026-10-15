package com.pml.shared.dto.authorization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Many authorization questions in one call, so a list of fifty events is one round trip and not
 * fifty. Each entry is an ordinary {@link AuthorizationRequest}; the answers come back in the same
 * order.
 */
public record BatchAuthorizationRequest(
        @NotEmpty @Size(max = MAX_CHECKS) List<@Valid AuthorizationRequest> checks) {

    public static final int MAX_CHECKS = 100;
}
