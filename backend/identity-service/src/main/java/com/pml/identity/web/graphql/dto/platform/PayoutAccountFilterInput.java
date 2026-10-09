package com.pml.identity.web.graphql.dto.platform;

import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.shared.constants.PayoutMethod;
import jakarta.validation.constraints.Size;

public record PayoutAccountFilterInput(
        PayoutAccountStatus status,
        PayoutMethod method,
        @Size(max = 100) String search
) {}
