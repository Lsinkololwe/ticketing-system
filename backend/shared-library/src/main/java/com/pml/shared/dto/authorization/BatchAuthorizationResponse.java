package com.pml.shared.dto.authorization;

import java.util.List;

/** The answers to a {@link BatchAuthorizationRequest}, in the order the questions were asked. */
public record BatchAuthorizationResponse(List<AuthorizationResult> results) {
}
